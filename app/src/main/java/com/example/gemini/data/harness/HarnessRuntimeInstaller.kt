package com.example.gemini.data.harness

import android.content.Context
import android.system.Os
import android.util.Log
import com.example.gemini.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream

/**
 * Modular Runtime Bundle Manager for JIN.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
 *
 * Downloads, verifies SHA256, and extracts Zstandard-compressed (.tar.zst)
 * runtime bundles (Core Ubuntu 20.04 ARM64, DeepSeek Harness, Claude Code,
 * Python 3, Android SDK 36/JDK 17, and Antigravity CLI).
 *
 * Full support for both Online (live cloud fetch) and Offline (prebundled/cached assets) modes.
 */
class HarnessRuntimeInstaller(private val context: Context) {

    private val runtimeDir = File(context.filesDir, "runtime")
    val rootfs = File(runtimeDir, "ubuntu")
    private val downloads = File(context.cacheDir, "runtime-downloads")

    // Markers inside rootfs
    private val coreReadyMarker = File(rootfs, ".jin-runtime-ready")
    private val coreMarker = File(rootfs, ".jin-core-version")
    private val dshMarker = File(rootfs, ".jin-dsh-version")
    private val claudeMarker = File(rootfs, ".jin-claude-version")
    private val pythonMarker = File(rootfs, ".jin-python-version")
    private val androidMarker = File(rootfs, ".jin-android-version")
    private val agyMarker = File(rootfs, ".jin-agy-version")

    companion object {
        const val TAG = "HarnessInstaller"
        const val CORE_VERSION = "2026.09.5"
        const val DSH_VERSION = "2026.09.1"
        const val CLAUDE_VERSION = "2026.09.1"
        const val PYTHON_VERSION = "2026.09.2"
        const val ANDROID_VERSION = "2026.09.1"
        const val AGY_VERSION = "2026.09.1"

        const val PRIMARY_RELEASE_BASE_URL = "https://github.com/techjarves/Mobile-Harness/releases/download/runtime-2026.09.4"
        const val FALLBACK_RELEASE_BASE_URL = "https://github.com/samir-ahmmed-tech/jin/releases/download/runtime-2026.09.4"

        val BUNDLES = mapOf(
            "core" to HarnessRuntimeBundle(
                name = "core",
                version = CORE_VERSION,
                fileName = "pocketdev-core-arm64-2026.09.5.tar.zst",
                sha256 = "df0cf7251c74f82d424231e3804114a4ca66b16130eea9abab11e220dc7ac012",
                compressedBytes = 72185773L,
                uncompressedBytes = 369172480L,
                description = "Ubuntu 20.04.5 ARM64 base with Node.js 24, Git, and Coreutils",
            ),
            "dsh" to HarnessRuntimeBundle(
                name = "dsh",
                version = DSH_VERSION,
                fileName = "pocketdev-dsh-arm64-2026.09.1.tar.zst",
                sha256 = "88e6a23ba74e1cd74a2c923b7e0d6bd78ba4b7e5f8a9b649f12bf4ffe158cce5",
                compressedBytes = 27752194L,
                uncompressedBytes = 249456640L,
                description = "DeepSeek Harness 0.1.2-rc.1 coding agent and launcher",
            ),
            "claude" to HarnessRuntimeBundle(
                name = "claude",
                version = CLAUDE_VERSION,
                fileName = "pocketdev-claude-arm64-2026.09.1.tar.zst",
                sha256 = "0f68e15630e8c0fc941afe3f61ab5a3eb4407b334018de6dbdabfa5eca627724",
                compressedBytes = 75289800L,
                uncompressedBytes = 215224320L,
                description = "Claude Code 2.1.263 ARM64",
            ),
            "python" to HarnessRuntimeBundle(
                name = "python",
                version = PYTHON_VERSION,
                fileName = "pocketdev-python-arm64-2026.09.2.tar.zst",
                sha256 = "6b3f56f7743fec142bc045db3ea561ee83fe89af87c2799165ef60351164ef85",
                compressedBytes = 55419626L,
                uncompressedBytes = 42127360L,
                description = "Python 3 runtime, pip, and venv tooling",
            ),
            "android" to HarnessRuntimeBundle(
                name = "android",
                version = ANDROID_VERSION,
                fileName = "pocketdev-android-arm64-2026.09.1.tar.zst",
                sha256 = "01bea058ebcb17416d1eb08c0211b3782da3228eb3c8348eb3719f2d61dd3ec6",
                compressedBytes = 569652007L,
                uncompressedBytes = 866703360L,
                description = "Temurin OpenJDK 17 ARM64, Android SDK 36, Build Tools 35, Gradle 8.14.3",
            ),
            "agy" to HarnessRuntimeBundle(
                name = "agy",
                version = AGY_VERSION,
                fileName = "pocketdev-agy-arm64-2026.09.1.tar.zst",
                sha256 = "a659ab9188956fc4721ca86fb21b5118e0e489f47a5e02ae6b4f2fb423659d78",
                compressedBytes = 41870025L,
                uncompressedBytes = 200048640L,
                description = "Antigravity CLI 1.1.27 ARM64 runtime",
            ),
        )
    }

    fun isCoreInstalled(): Boolean {
        return rootfs.isDirectory &&
            File(rootfs, "usr/bin/bash").canExecute() &&
            coreReadyMarker.exists()
    }

    fun isBundleInstalled(bundleName: String): Boolean {
        return when (bundleName) {
            "core" -> isCoreInstalled()
            "dsh" -> dshMarker.exists() && File(rootfs, "usr/local/bin/dsh").exists()
            "claude" -> claudeMarker.exists() && File(rootfs, "usr/local/bin/claude").exists()
            "python" -> pythonMarker.exists() && File(rootfs, "usr/bin/python3").exists()
            "android" -> androidMarker.exists() && File(rootfs, "root/android-sdk").isDirectory
            "agy" -> agyMarker.exists() && File(rootfs, "usr/local/bin/agy").exists()
            else -> false
        }
    }

    fun isAgentInstalled(agent: AgentKind): Boolean = when (agent) {
        AgentKind.DEEPSEEK_HARNESS -> isBundleInstalled("dsh")
        AgentKind.CLAUDE_CODE -> isBundleInstalled("claude")
        AgentKind.ANTIGRAVITY -> isBundleInstalled("agy") || File(context.filesDir, "usr/bin/agy").exists()
    }

    suspend fun installCore(): Boolean = installBundle("core")

    suspend fun installBundle(
        bundleName: String,
        onProgress: (message: String, fraction: Float) -> Unit = { _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        val bundle = BUNDLES[bundleName] ?: throw IllegalArgumentException("Unknown bundle: $bundleName")
        rootfs.mkdirs()
        downloads.mkdirs()

        val targetFile = File(downloads, bundle.fileName)
        var sourceStream: InputStream? = null

        // 1. Check if asset exists (offline mode / prepackaged)
        val assetPaths = listOf(
            "offline/runtime/${bundle.fileName}",
            "shared/runtime/${bundle.fileName}",
            "runtime/${bundle.fileName}",
        )
        for (path in assetPaths) {
            try {
                sourceStream = context.assets.open(path)
                Log.i(TAG, "Found pre-packaged runtime asset at: $path")
                break
            } catch (_: Exception) {
            }
        }

        // 2. If not found in assets, download if online or check downloads cache
        if (sourceStream == null) {
            if (targetFile.isFile && targetFile.length() > 0L) {
                if (verifySha256(targetFile, bundle.sha256)) {
                    Log.i(TAG, "Cached bundle file verified: ${targetFile.name}")
                    sourceStream = targetFile.inputStream()
                } else {
                    targetFile.delete()
                }
            }

            if (sourceStream == null) {
                check(!BuildConfig.IS_OFFLINE_MODE) {
                    "Offline mode is active and runtime bundle '${bundle.fileName}' is not pre-packaged or cached."
                }
                onProgress("Downloading ${bundle.name} bundle...", 0.1f)
                val downloaded = downloadWithFallback(bundle, targetFile, onProgress)
                check(downloaded && verifySha256(targetFile, bundle.sha256)) {
                    "Failed to download and verify bundle '${bundle.fileName}'"
                }
                sourceStream = targetFile.inputStream()
            }
        }

        // 3. Extract Zstd Tar into rootfs
        onProgress("Extracting ${bundle.name} bundle...", 0.5f)
        try {
            sourceStream.use { rawIn ->
                extractZstdTarStream(rawIn, rootfs)
            }
            writeMarker(bundleName, bundle.version)
            if (bundleName == "core") {
                coreReadyMarker.writeText("1")
                ensureRootfsPermissions()
            }
            onProgress("Installed ${bundle.name} successfully!", 1.0f)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for $bundleName: ${e.message}", e)
            false
        }
    }

    private fun writeMarker(bundleName: String, version: String) {
        when (bundleName) {
            "core" -> coreMarker.writeText(version)
            "dsh" -> dshMarker.writeText(version)
            "claude" -> claudeMarker.writeText(version)
            "python" -> pythonMarker.writeText(version)
            "android" -> androidMarker.writeText(version)
            "agy" -> agyMarker.writeText(version)
        }
    }

    private fun downloadWithFallback(
        bundle: HarnessRuntimeBundle,
        destination: File,
        onProgress: (message: String, fraction: Float) -> Unit,
    ): Boolean {
        val urls = listOf(
            "$PRIMARY_RELEASE_BASE_URL/${bundle.fileName}",
            "$FALLBACK_RELEASE_BASE_URL/${bundle.fileName}",
        )
        for (urlString in urls) {
            try {
                Log.i(TAG, "Attempting bundle download from: $urlString")
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 180_000
                    instanceFollowRedirects = true
                }
                conn.connect()
                if (conn.responseCode in 200..299) {
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: bundle.compressedBytes
                    var downloaded = 0L
                    conn.inputStream.use { inStream ->
                        FileOutputStream(destination).use { outStream ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            while (inStream.read(buf).also { read = it } != -1) {
                                outStream.write(buf, 0, read)
                                downloaded += read
                                val frac = (downloaded.toFloat() / total.toFloat()).coerceIn(0.1f, 0.5f)
                                onProgress("Downloading ${bundle.name} (${downloaded / 1024 / 1024}MB / ${total / 1024 / 1024}MB)...", frac)
                            }
                        }
                    }
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download attempt failed from $urlString: ${e.message}")
            }
        }
        return false
    }

    private fun extractZstdTarStream(rawInput: InputStream, destination: File) {
        val deferredLinks = mutableListOf<Pair<File, File>>()
        TarArchiveInputStream(
            ZstdCompressorInputStream(BufferedInputStream(rawInput)),
        ).use { tar ->
            var entry: TarArchiveEntry? = tar.nextEntry
            while (entry != null) {
                val cleanName = entry.name.removePrefix("./")
                if (cleanName.isNotBlank() && !cleanName.contains("._")) {
                    val target = File(destination, cleanName).canonicalFile
                    require(target.toPath().startsWith(destination.canonicalFile.toPath())) {
                        "Path traversal attempt: $cleanName"
                    }
                    when {
                        entry.isDirectory -> target.mkdirs()
                        entry.isSymbolicLink -> {
                            target.parentFile?.mkdirs()
                            if (target.exists() || java.nio.file.Files.isSymbolicLink(target.toPath())) target.delete()
                            runCatching { Os.symlink(entry.linkName, target.absolutePath) }
                        }
                        entry.isLink -> {
                            target.parentFile?.mkdirs()
                            val linkTarget = File(destination, entry.linkName.removePrefix("./")).canonicalFile
                            if (linkTarget.exists()) {
                                linkTarget.inputStream().use { input -> FileOutputStream(target).use { input.copyTo(it) } }
                            } else {
                                deferredLinks += target to linkTarget
                            }
                        }
                        entry.isFile -> {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { output -> tar.copyTo(output) }
                            runCatching { Os.chmod(target.absolutePath, entry.mode and 0b111111111) }
                        }
                    }
                }
                entry = tar.nextEntry
            }
        }
        deferredLinks.forEach { (target, linkTarget) ->
            if (linkTarget.isFile) {
                target.parentFile?.mkdirs()
                linkTarget.inputStream().use { input -> FileOutputStream(target).use { input.copyTo(it) } }
                runCatching { Os.chmod(target.absolutePath, Os.stat(linkTarget.absolutePath).st_mode) }
            }
        }
    }

    private fun ensureRootfsPermissions() {
        val binDirs = listOf(
            File(rootfs, "bin"),
            File(rootfs, "usr/bin"),
            File(rootfs, "usr/local/bin"),
        )
        for (dir in binDirs) {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) runCatching { file.setExecutable(true, false) }
            }
        }
    }

    private fun verifySha256(file: File, expectedHash: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inStream ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (inStream.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        val computed = digest.digest().joinToString("") { "%02x".format(it) }
        return computed.equals(expectedHash, ignoreCase = true)
    }
}
