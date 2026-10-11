package com.example.gemini.data.harness

import java.io.File
import org.json.JSONObject

/**
 * On-device Android project inspector and Gradle build manager for JIN.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
 */
object AndroidBuildSupport {

    fun androidGradleCommand(root: File, task: String = "assembleDebug"): String {
        val launcher = File(root, "gradlew").takeIf(File::isFile)?.let { "bash ./gradlew" } ?: "gradle"
        return "$launcher -Dorg.gradle.jvmargs= --no-daemon --max-workers=2 " +
            "$task --console=plain --stacktrace"
    }

    fun findAndroidProjectRoot(workspace: File): File? {
        val settingsNames = setOf("settings.gradle", "settings.gradle.kts")
        return workspace.walkTopDown().maxDepth(4)
            .filter { it.isFile && it.name in settingsNames }
            .mapNotNull(File::getParentFile)
            .sortedBy { it.absolutePath.length }
            .firstOrNull { root ->
                root.walkTopDown().maxDepth(5)
                    .any { it.isFile && it.invariantSeparatorsPath.endsWith("src/main/AndroidManifest.xml") }
            }
    }

    fun findDebugApk(projectRoot: File): File? {
        val metadata = projectRoot.walkTopDown().maxDepth(8)
            .filter { it.isFile && it.name == "output-metadata.json" && "/outputs/apk/" in it.invariantSeparatorsPath }
            .maxByOrNull(File::lastModified)
        if (metadata != null) {
            runCatching {
                val json = JSONObject(metadata.readText())
                val elements = json.optJSONArray("elements") ?: return@runCatching null
                (0 until elements.length()).asSequence()
                    .mapNotNull { elements.optJSONObject(it)?.optString("outputFile") }
                    .map { File(metadata.parentFile, it) }
                    .firstOrNull { it.isFile && it.length() > 0L }
            }.getOrNull()?.let { return it }
        }
        return projectRoot.walkTopDown().maxDepth(10)
            .filter { it.isFile && it.extension.equals("apk", true) && "/outputs/apk/debug/" in it.invariantSeparatorsPath }
            .maxByOrNull(File::lastModified)
    }

    fun findReusableDebugApk(projectRoot: File): File? {
        val apk = findDebugApk(projectRoot) ?: return null
        val ignoredDirectories = setOf("build", ".gradle", ".idea", ".git")
        val buildInputExtensions = setOf(
            "kt", "java", "xml", "gradle", "kts", "properties", "toml", "pro", "json", "jar", "aar",
        )
        val changedAfterApk = projectRoot.walkTopDown()
            .onEnter { directory -> directory == projectRoot || directory.name !in ignoredDirectories }
            .filter { file ->
                if (!file.isFile) return@filter false
                val relative = file.relativeTo(projectRoot).invariantSeparatorsPath
                val isAndroidSource = relative.startsWith("src/") || "/src/" in relative
                isAndroidSource || file.extension.lowercase() in buildInputExtensions ||
                    file.name in setOf("gradlew", "gradlew.bat")
            }
            .any { it.lastModified() > apk.lastModified() }
        return if (changedAfterApk) null else apk
    }
}
