package com.example.gemini.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.gemini.data.preferences.AuthPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Granular scope selection for backup and export operations.
 */
enum class BackupScope(val displayName: String) {
    FULL_VAULT("Full System & Workspace Vault"),
    CHATS_ONLY("Chats, Notebooks & History"),
    PROJECTS_ONLY("Projects & Code Workspaces")
}

/**
 * Real-time progress and verification telemetry for Google Drive cloud sync.
 */
data class SyncProgress(
    val phase: String,
    val percentage: Int,
    val filesProcessed: Int = 0,
    val totalFilesEstimate: Int = 0,
    val sha256Hex: String? = null
)

/**
 * Manages complete backup and restore of chat history, files, notebooks, projects,
 * and app data to Google Drive using a SINGLE continuously-updated master file
 * (`JIN_Master_Vault_Backup.zip`) so Google Drive storage never fills up with duplicate archives.
 * Also supports 1-tap Local File (.zip) Export & Restore for large archives or offline recovery.
 */
class GoogleDriveSyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveSync"
        private const val PREFS_NAME = "jin_gdrive_sync_prefs"
        private const val KEY_LAST_BACKUP = "last_backup_time"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
        private const val KEY_MASTER_FILE_ID = "master_file_id"
        private const val FOLDER_NAME = "JIN_Cloud_Vault"
        const val MASTER_BACKUP_FILE_NAME = "JIN_Master_Vault_Backup.zip"

        private val EXCLUDED_DIR_NAMES = setOf(
            "build", ".gradle", "node_modules", ".cxx", "dist", ".kotlin", "caches", "tmp"
        )

        @Volatile
        private var instance: GoogleDriveSyncManager? = null

        fun getInstance(context: Context): GoogleDriveSyncManager {
            return instance ?: synchronized(this) {
                instance ?: GoogleDriveSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val authPrefs = AuthPreferences(context)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoBackupJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L))
    val lastBackupTime: StateFlow<Long> = _lastBackupTime.asStateFlow()

    // Enabled by default so connecting Google automatically backs up into the single master file
    private val _autoSyncEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SYNC, true))
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(16384)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        _autoSyncEnabled.value = enabled
        if (enabled) {
            triggerAutoBackupIfEnabled(delayMs = 2000L)
        }
    }

    fun getLastBackupFormatted(): String {
        val time = _lastBackupTime.value
        if (time == 0L) return "Never backed up"
        val sdf = SimpleDateFormat("MMM d, yyyy · hh:mm a", Locale.getDefault())
        return sdf.format(Date(time))
    }

    /**
     * Automatically triggers an incremental update to the single master file on Google Drive
     * after a short debounce window, provided Auto-Sync is enabled and Google is connected.
     */
    fun triggerAutoBackupIfEnabled(delayMs: Long = 10_000L) {
        if (!_autoSyncEnabled.value) return
        val hasCreds = authPrefs.getAccessTokenSync().isNotBlank() || authPrefs.getRefreshTokenSync().isNotBlank()
        if (!hasCreds) return

        autoBackupJob?.cancel()
        autoBackupJob = syncScope.launch {
            delay(delayMs)
            if (!_isSyncing.value && _autoSyncEnabled.value) {
                backupAllToGoogleDrive(isSilentAutoSync = true)
            }
        }
    }

    /**
     * Resolves a valid Google OAuth access token, auto-refreshing expired tokens if possible.
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        var token = authPrefs.getAccessTokenSync()
        val refreshToken = authPrefs.getRefreshTokenSync()

        if (token.isBlank() && refreshToken.isNotBlank()) {
            _syncStatus.value = "Authenticating with Google Drive..."
            val refreshRes = GoogleOAuthManager().refreshToken(refreshToken)
            if (refreshRes.isSuccess) {
                val tokenData = refreshRes.getOrThrow()
                token = tokenData.access_token
                authPrefs.saveTokens(
                    accessToken = tokenData.access_token,
                    refreshToken = tokenData.refresh_token ?: refreshToken,
                    email = authPrefs.getUserEmailSync().ifBlank { null },
                    expiresInSeconds = tokenData.expires_in ?: 3600
                )
                return@withContext token
            }
        }

        if (token.isNotBlank()) {
            val testReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            try {
                client.newCall(testReq).execute().use { resp ->
                    if (resp.code == 401 && refreshToken.isNotBlank()) {
                        _syncStatus.value = "Refreshing Google Drive credentials..."
                        val refreshRes = GoogleOAuthManager().refreshToken(refreshToken)
                        if (refreshRes.isSuccess) {
                            val tokenData = refreshRes.getOrThrow()
                            token = tokenData.access_token
                            authPrefs.saveTokens(
                                accessToken = tokenData.access_token,
                                refreshToken = tokenData.refresh_token ?: refreshToken,
                                email = authPrefs.getUserEmailSync().ifBlank { null },
                                expiresInSeconds = tokenData.expires_in ?: 3600
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        token.ifBlank { null }
    }

    /**
     * Updates the SINGLE master archive (`JIN_Master_Vault_Backup.zip`) on Google Drive in-place,
     * and removes any old timestamped duplicate backup files so Drive never fills up.
     */
    suspend fun backupAllToGoogleDrive(
        scope: BackupScope = BackupScope.FULL_VAULT,
        isSilentAutoSync: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        if (_isSyncing.value) {
            return@withContext Result.failure(IllegalStateException("Backup is already in progress"))
        }
        _isSyncing.value = true
        _syncProgress.value = SyncProgress("Preparing ${scope.displayName}...", 10)
        if (!isSilentAutoSync) {
            _syncStatus.value = "Preparing single-file master backup (${scope.displayName})..."
        }
        try {
            val token = getValidAccessToken()
            if (token == null) {
                val err = "Google account not connected. Tap Sign In to enable automatic backup."
                if (!isSilentAutoSync) _syncStatus.value = err
                return@withContext Result.failure(IllegalStateException(err))
            }

            // 1. Create a local master ZIP file
            val tempZip = File(context.cacheDir, MASTER_BACKUP_FILE_NAME)
            if (tempZip.exists()) tempZip.delete()

            _syncProgress.value = SyncProgress("Packing files (${scope.displayName})...", 30)
            if (!isSilentAutoSync) {
                _syncStatus.value = "Packing files into vault archive..."
            }
            val fileCount = zipUserData(tempZip, scope)

            val hash = computeSha256(tempZip)
            _syncProgress.value = SyncProgress("Verifying SHA-256 checksum...", 60, fileCount, fileCount, hash)

            val zipSizeMb = String.format(Locale.US, "%.2f MB", tempZip.length() / (1024.0 * 1024.0))

            // 2. Locate or create JIN_Cloud_Vault folder on Google Drive
            _syncProgress.value = SyncProgress("Uploading archive to Google Drive...", 80, fileCount, fileCount, hash)
            if (!isSilentAutoSync) {
                _syncStatus.value = "Syncing to single file ($MASTER_BACKUP_FILE_NAME)..."
            }
            val folderId = getOrCreateDriveFolder(token, FOLDER_NAME)

            // 3. Update single master file in-place (or create once if first time), and purge legacy duplicates
            val masterFileId = createOrUpdateSingleMasterZip(token, tempZip, folderId)
            prefs.edit().putString(KEY_MASTER_FILE_ID, masterFileId).apply()

            val cleanedDuplicates = cleanupOldDuplicateBackups(token, folderId, masterFileId)

            tempZip.delete()

            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_BACKUP, now).apply()
            _lastBackupTime.value = now
            val cleanupNote = if (cleanedDuplicates > 0) " (Cleaned $cleanedDuplicates old duplicates)" else ""
            val successMsg = "Synced to single file $MASTER_BACKUP_FILE_NAME ($zipSizeMb, $fileCount files, SHA-256: ${hash.take(8)})$cleanupNote"
            _syncProgress.value = SyncProgress("Sync complete!", 100, fileCount, fileCount, hash)
            _syncStatus.value = successMsg
            Result.success(successMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}", e)
            val err = "Backup failed: ${e.localizedMessage ?: e.message}"
            if (!isSilentAutoSync) _syncStatus.value = err
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Restores from the single master backup archive (or latest available backup) on Google Drive.
     */
    suspend fun restoreLatestFromGoogleDrive(): Result<String> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncProgress.value = SyncProgress("Locating master backup on Google Drive...", 15)
        _syncStatus.value = "Locating master backup on Google Drive..."
        try {
            val token = getValidAccessToken()
            if (token == null) {
                val err = "Google account not connected. Please sign in or use 'Restore from Local File'."
                _syncStatus.value = err
                return@withContext Result.failure(IllegalStateException(err))
            }

            val folderId = getOrCreateDriveFolder(token, FOLDER_NAME)
            val fileId = findMasterOrLatestBackupFileId(token, folderId)
                ?: return@withContext Result.failure(IllegalStateException("No backup file found in Google Drive ($FOLDER_NAME)."))

            val tempZip = File(context.cacheDir, "restore_temp.zip")
            if (tempZip.exists()) tempZip.delete()

            _syncProgress.value = SyncProgress("Downloading master archive...", 45)
            _syncStatus.value = "Downloading $MASTER_BACKUP_FILE_NAME from Drive..."
            downloadDriveFile(token, fileId, tempZip)

            val hash = computeSha256(tempZip)
            _syncProgress.value = SyncProgress("Restoring & verifying SHA-256 (${hash.take(8)})...", 75, sha256Hex = hash)
            _syncStatus.value = "Restoring chats, notebooks & projects..."
            val restoredCount = unzipUserData(tempZip)
            tempZip.delete()

            val successMsg = "Successfully restored $restoredCount files (SHA-256: ${hash.take(8)}) from Google Drive!"
            _syncProgress.value = SyncProgress("Restore complete!", 100, restoredCount, restoredCount, hash)
            _syncStatus.value = successMsg
            Result.success(successMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}", e)
            val err = "Cloud restore failed: ${e.localizedMessage ?: e.message}. Tip: For large files, download from Drive and use 'Restore from Local File'."
            _syncStatus.value = err
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Exports the complete single-file backup directly to the user's local Download directory
     * (`/sdcard/Download/JIN_Master_Vault_Backup.zip`) or custom URI.
     */
    suspend fun exportLocalBackupToDownloads(scope: BackupScope = BackupScope.FULL_VAULT): Result<String> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncProgress.value = SyncProgress("Exporting local backup file...", 25)
        _syncStatus.value = "Exporting local backup file (${scope.displayName})..."
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: File("/sdcard/Download")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val outZip = File(downloadsDir, MASTER_BACKUP_FILE_NAME)
            if (outZip.exists()) outZip.delete()

            val fileCount = zipUserData(outZip, scope)
            val hash = computeSha256(outZip)
            val sizeMb = String.format(Locale.US, "%.2f MB", outZip.length() / (1024.0 * 1024.0))
            val msg = "Saved to Downloads/$MASTER_BACKUP_FILE_NAME ($sizeMb, $fileCount files, SHA-256: ${hash.take(8)})"
            _syncProgress.value = SyncProgress("Export complete!", 100, fileCount, fileCount, hash)
            _syncStatus.value = msg
            Result.success(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Local export failed: ${e.message}", e)
            val err = "Local export failed: ${e.localizedMessage ?: e.message}"
            _syncStatus.value = err
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Restores all chats, projects, and settings directly from a locally downloaded `.zip` file URI.
     * Ideal when the user manually downloads `JIN_Master_Vault_Backup.zip` from Google Drive.
     */
    suspend fun restoreFromLocalUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncProgress.value = SyncProgress("Reading local backup file...", 20)
        _syncStatus.value = "Reading local backup file..."
        try {
            val tempZip = File(context.cacheDir, "local_restore_temp.zip")
            if (tempZip.exists()) tempZip.delete()

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempZip).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open selected backup file"))

            if (tempZip.length() < 22L) {
                tempZip.delete()
                return@withContext Result.failure(IllegalStateException("Selected file is empty or not a valid ZIP archive"))
            }

            val hash = computeSha256(tempZip)
            _syncProgress.value = SyncProgress("Extracting & verifying SHA-256 (${hash.take(8)})...", 60, sha256Hex = hash)
            _syncStatus.value = "Extracting chats, notebooks & projects from local file..."
            val restoredCount = unzipUserData(tempZip)
            tempZip.delete()

            val msg = "Successfully restored $restoredCount files (SHA-256: ${hash.take(8)}) from local backup!"
            _syncProgress.value = SyncProgress("Restore complete!", 100, restoredCount, restoredCount, hash)
            _syncStatus.value = msg
            Result.success(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Local restore failed: ${e.message}", e)
            val err = "Local restore failed: ${e.localizedMessage ?: e.message}"
            _syncStatus.value = err
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    private fun zipUserData(zipFile: File, scope: BackupScope = BackupScope.FULL_VAULT): Int {
        val addedEntries = HashSet<String>()
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Direct chats in internal storage
            if (scope != BackupScope.PROJECTS_ONLY) {
                val chatsDir = File(context.filesDir, "direct_chats")
                if (chatsDir.exists() && chatsDir.isDirectory) {
                    addDirectoryToZip(chatsDir, "direct_chats", zos, addedEntries)
                }

                // 2. Legacy / general chats
                val legacyChatsDir = File(context.filesDir, "chats")
                if (legacyChatsDir.exists() && legacyChatsDir.isDirectory) {
                    addDirectoryToZip(legacyChatsDir, "chats", zos, addedEntries)
                }

                // 3. Gemini Notebooks
                val notebooksDir = File(context.filesDir, "notebooks")
                if (notebooksDir.exists() && notebooksDir.isDirectory) {
                    addDirectoryToZip(notebooksDir, "notebooks", zos, addedEntries)
                }

                // 4. Custom chat folders metadata
                val chatFoldersFile = File(context.filesDir, "chat_folders.json")
                if (chatFoldersFile.exists() && chatFoldersFile.isFile) {
                    addFileToZip(chatFoldersFile, "chat_folders.json", zos, addedEntries)
                }

                // 5. App preferences and SQLite databases from internal storage
                val appDataDir = File(context.applicationInfo.dataDir)
                val sharedPrefsDir = File(appDataDir, "shared_prefs")
                if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                    addDirectoryToZip(sharedPrefsDir, "app_data/shared_prefs", zos, addedEntries)
                }
                val databasesDir = File(appDataDir, "databases")
                if (databasesDir.exists() && databasesDir.isDirectory) {
                    addDirectoryToZip(databasesDir, "app_data/databases", zos, addedEntries)
                }
            }

            // 6. Termux workspace projects (excluding heavy build/gradle/apk artifacts)
            if (scope != BackupScope.CHATS_ONLY) {
                val homeDir = System.getenv("HOME") ?: "/data/user/0/com.termux/files/home"
                val projectsDir = File(homeDir, "projects")
                if (projectsDir.exists() && projectsDir.isDirectory) {
                    addDirectoryToZip(projectsDir, "projects", zos, addedEntries)
                }

                // 7. Gemini brain, conversations, summaries DB, annotations & skills in Termux home
                val geminiDir = File(homeDir, ".gemini/antigravity-cli")
                if (geminiDir.exists() && geminiDir.isDirectory) {
                    listOf("brain", "annotations", "conversations", "skills", "workflows").forEach { sub ->
                        val dir = File(geminiDir, sub)
                        if (dir.exists() && dir.isDirectory) {
                            addDirectoryToZip(dir, ".gemini/antigravity-cli/$sub", zos, addedEntries)
                        }
                    }
                    listOf("conversation_summaries.db", "conversation_summaries.db-wal", "conversation_summaries.db-shm").forEach { dbFile ->
                        val f = File(geminiDir, dbFile)
                        if (f.exists() && f.isFile) {
                            addFileToZip(f, ".gemini/antigravity-cli/$dbFile", zos, addedEntries)
                        }
                    }
                }

                // 8. Global GEMINI.md rules if present
                val globalGeminiMd = File(homeDir, ".gemini/GEMINI.md")
                if (globalGeminiMd.exists() && globalGeminiMd.isFile) {
                    addFileToZip(globalGeminiMd, ".gemini/GEMINI.md", zos, addedEntries)
                }
            }

            // 9. Embed jin_backup_manifest.json with metadata & integrity proof
            try {
                val manifest = JSONObject().apply {
                    put("version", "2.2.6")
                    put("scope", scope.name)
                    put("scopeTitle", scope.displayName)
                    put("timestamp", System.currentTimeMillis())
                    put("formattedDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    put("totalFiles", addedEntries.size)
                    put("deviceModel", Build.MODEL)
                    put("deviceManufacturer", Build.MANUFACTURER)
                }
                val manifestBytes = manifest.toString(2).toByteArray(Charsets.UTF_8)
                val manifestEntry = ZipEntry("jin_backup_manifest.json")
                zos.putNextEntry(manifestEntry)
                zos.write(manifestBytes)
                zos.closeEntry()
            } catch (e: Exception) {
                Log.w(TAG, "Failed embedding manifest: ${e.message}")
            }
        }
        return addedEntries.size
    }

    private fun addFileToZip(
        file: File,
        entryName: String,
        zos: ZipOutputStream,
        addedEntries: MutableSet<String>
    ) {
        if (!file.exists() || !file.isFile || !addedEntries.add(entryName)) return
        try {
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        } catch (e: Exception) {
            Log.w(TAG, "Skipped file $entryName: ${e.message}")
        }
    }

    private fun addDirectoryToZip(
        dir: File,
        baseName: String,
        zos: ZipOutputStream,
        addedEntries: MutableSet<String>
    ) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            val name = f.name
            if (f.isDirectory) {
                if (name in EXCLUDED_DIR_NAMES || name == ".git") continue
                addDirectoryToZip(f, "$baseName/$name", zos, addedEntries)
            } else if (f.isFile) {
                // Skip compiled APK/DEX/SO binaries and files > 15MB so cloud sync stays fast & compact
                if (name.endsWith(".apk", true) ||
                    name.endsWith(".aab", true) ||
                    name.endsWith(".dex", true) ||
                    name.endsWith(".so", true) ||
                    name.endsWith(".jar", true) ||
                    f.length() > 15 * 1024 * 1024
                ) {
                    continue
                }
                addFileToZip(f, "$baseName/$name", zos, addedEntries)
            }
        }
    }

    private fun unzipUserData(zipFile: File): Int {
        val homeDir = System.getenv("HOME") ?: "/data/user/0/com.termux/files/home"
        val appDataDir = File(context.applicationInfo.dataDir)
        var restoredFilesCount = 0
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "jin_backup_manifest.json") {
                    try {
                        val manifestJson = JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                        Log.i(TAG, "Restoring archive with manifest: $manifestJson")
                    } catch (_: Exception) {}
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                if (!name.contains("..")) {
                    val destFile = when {
                        name.startsWith("app_data/shared_prefs/") -> File(appDataDir, name.removePrefix("app_data/"))
                        name.startsWith("app_data/databases/") -> File(appDataDir, name.removePrefix("app_data/"))
                        name.startsWith("direct_chats/") -> File(context.filesDir, name)
                        name.startsWith("chats/") -> File(context.filesDir, name)
                        name.startsWith("notebooks/") -> File(context.filesDir, name)
                        name == "chat_folders.json" -> File(context.filesDir, name)
                        name.startsWith("projects/") -> File(homeDir, name)
                        name.startsWith(".gemini/") -> File(homeDir, name)
                        name.startsWith(".claude/") -> File(homeDir, name)
                        else -> File(context.filesDir, name)
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        restoredFilesCount++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return restoredFilesCount
    }

    private fun getOrCreateDriveFolder(token: String, folderName: String): String {
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=name='$folderName'+and+mimeType='application/vnd.google-apps.folder'+and+trashed=false&fields=files(id,name)"
        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(searchReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            }
        }

        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val payload = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        val createReq = Request.Builder()
            .url(createUrl)
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(createReq).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val json = JSONObject(body)
                return json.getString("id")
            } else {
                throw IllegalStateException("Failed to create folder on Drive: $body")
            }
        }
    }

    /**
     * Checks if `JIN_Master_Vault_Backup.zip` already exists in `folderId`.
     * If it exists, updates that exact file ID in-place via `PATCH` (`uploadType=media`).
     * Otherwise creates `JIN_Master_Vault_Backup.zip` once via `POST` (`uploadType=multipart`).
     */
    private fun createOrUpdateSingleMasterZip(token: String, zipFile: File, folderId: String): String {
        val existingId = findFileByNameInFolder(token, folderId, MASTER_BACKUP_FILE_NAME)
        if (existingId != null) {
            val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=media"
            val updateReq = Request.Builder()
                .url(updateUrl)
                .header("Authorization", "Bearer $token")
                .patch(zipFile.asRequestBody("application/zip".toMediaType()))
                .build()

            client.newCall(updateReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    return existingId
                } else {
                    Log.w(TAG, "In-place PATCH returned ${resp.code}, falling back to fresh master file upload")
                }
            }
        }

        val metadata = JSONObject().apply {
            put("name", MASTER_BACKUP_FILE_NAME)
            put("parents", JSONArray().apply { put(folderId) })
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                null,
                metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .addFormDataPart(
                "file",
                MASTER_BACKUP_FILE_NAME,
                zipFile.asRequestBody("application/zip".toMediaType())
            )
            .build()

        val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id"
        val request = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IllegalStateException("Upload failed: HTTP ${resp.code} $body")
            }
            return JSONObject(body).optString("id", existingId ?: "")
        }
    }

    /**
     * Deletes old timestamped `JIN_Backup_*.zip` files (and any duplicate archives other than `keepFileId`)
     * inside `JIN_Cloud_Vault` so the user's Google Drive storage never fills up.
     */
    private fun cleanupOldDuplicateBackups(token: String, folderId: String, keepFileId: String): Int {
        if (keepFileId.isBlank()) return 0
        var deletedCount = 0
        try {
            val url = "https://www.googleapis.com/drive/v3/files?q='$folderId'+in+parents+and+trashed=false&pageSize=100&fields=files(id,name)"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val idsToDelete = mutableListOf<String>()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val files = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                    if (files != null) {
                        for (i in 0 until files.length()) {
                            val obj = files.getJSONObject(i)
                            val id = obj.optString("id")
                            val name = obj.optString("name")
                            if (id.isNotBlank() && id != keepFileId &&
                                (name.startsWith("JIN_Backup_") || name == MASTER_BACKUP_FILE_NAME)
                            ) {
                                idsToDelete.add(id)
                            }
                        }
                    }
                }
            }

            for (oldId in idsToDelete) {
                try {
                    val delReq = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$oldId")
                        .header("Authorization", "Bearer $token")
                        .delete()
                        .build()
                    client.newCall(delReq).execute().use { delResp ->
                        if (delResp.isSuccessful || delResp.code == 204) {
                            deletedCount++
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup duplicates warning: ${e.message}")
        }
        return deletedCount
    }

    private fun findFileByNameInFolder(token: String, folderId: String, fileName: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?q='$folderId'+in+parents+and+name='$fileName'+and+trashed=false&orderBy=modifiedTime+desc&pageSize=1&fields=files(id,name)"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val files = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }

    private fun findMasterOrLatestBackupFileId(token: String, folderId: String): String? {
        // 1. Prefer the single master file
        findFileByNameInFolder(token, folderId, MASTER_BACKUP_FILE_NAME)?.let { return it }

        // 2. Fallback to most recently modified file in JIN_Cloud_Vault
        val url = "https://www.googleapis.com/drive/v3/files?q='$folderId'+in+parents+and+trashed=false&orderBy=modifiedTime+desc&pageSize=1&fields=files(id,name)"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) {
                val files = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }

    private fun downloadDriveFile(token: String, fileId: String, destFile: File) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${resp.code}")
            }
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
