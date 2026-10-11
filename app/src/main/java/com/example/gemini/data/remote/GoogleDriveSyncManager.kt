package com.example.gemini.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.gemini.data.preferences.AuthPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages complete backup and restore of chat history, files, images, videos, projects,
 * and app data to Google Drive.
 */
class GoogleDriveSyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveSync"
        private const val PREFS_NAME = "jin_gdrive_sync_prefs"
        private const val KEY_LAST_BACKUP = "last_backup_time"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
        private const val FOLDER_NAME = "JIN_Cloud_Vault"

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
    private val client = OkHttpClient()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L))
    val lastBackupTime: StateFlow<Long> = _lastBackupTime.asStateFlow()

    private val _autoSyncEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SYNC, false))
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        _autoSyncEnabled.value = enabled
    }

    fun getLastBackupFormatted(): String {
        val time = _lastBackupTime.value
        if (time == 0L) return "Never backed up"
        val sdf = SimpleDateFormat("MMM d, yyyy · hh:mm a", Locale.getDefault())
        return sdf.format(Date(time))
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
     * Creates a full archive of chats, files, and projects, and uploads to Google Drive.
     */
    suspend fun backupAllToGoogleDrive(): Result<String> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncStatus.value = "Preparing backup archive..."
        try {
            val token = getValidAccessToken()
            if (token == null) {
                val err = "Google account not signed in. Please connect your Google account in Cloud Vault or Accounts."
                _syncStatus.value = err
                return@withContext Result.failure(IllegalStateException(err))
            }

            // 1. Create a local backup ZIP file
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val tempZip = File(context.cacheDir, "JIN_Backup_$timeStamp.zip")
            if (tempZip.exists()) tempZip.delete()

            _syncStatus.value = "Compressing chats, files, and projects..."
            zipUserData(tempZip)

            val zipSizeMb = String.format(Locale.US, "%.1f MB", tempZip.length() / (1024.0 * 1024.0))

            // 2. Locate or create JIN_Cloud_Vault folder on Google Drive
            _syncStatus.value = "Connecting to Google Drive..."
            val folderId = getOrCreateDriveFolder(token, FOLDER_NAME)

            // 3. Upload ZIP archive to Google Drive
            _syncStatus.value = "Uploading backup ($zipSizeMb) to Drive..."
            uploadZipToFolder(token, tempZip, folderId)

            tempZip.delete()

            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_BACKUP, now).apply()
            _lastBackupTime.value = now
            val successMsg = "Successfully backed up $zipSizeMb to Google Drive ($FOLDER_NAME)!"
            _syncStatus.value = successMsg
            Result.success(successMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}", e)
            val err = "Backup failed: ${e.localizedMessage ?: e.message}"
            _syncStatus.value = err
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Restores the latest backup archive from Google Drive.
     */
    suspend fun restoreLatestFromGoogleDrive(): Result<String> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncStatus.value = "Locating latest backup on Drive..."
        try {
            val token = getValidAccessToken()
            if (token == null) {
                val err = "Google account not signed in. Please connect your Google account in Cloud Vault or Accounts."
                _syncStatus.value = err
                return@withContext Result.failure(IllegalStateException(err))
            }

            val folderId = getOrCreateDriveFolder(token, FOLDER_NAME)
            val fileId = getLatestBackupFileId(token, folderId)
                ?: return@withContext Result.failure(IllegalStateException("No backups found in Google Drive ($FOLDER_NAME)."))

            val tempZip = File(context.cacheDir, "restore_temp.zip")
            if (tempZip.exists()) tempZip.delete()

            _syncStatus.value = "Downloading backup archive..."
            downloadDriveFile(token, fileId, tempZip)

            _syncStatus.value = "Extracting chats, files, and projects..."
            unzipUserData(tempZip)
            tempZip.delete()

            val successMsg = "Successfully restored all data from Google Drive!"
            _syncStatus.value = successMsg
            Result.success(successMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}", e)
            val err = "Restore failed: ${e.localizedMessage ?: e.message}"
            _syncStatus.value = err
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    private fun zipUserData(zipFile: File) {
        val zos = ZipOutputStream(FileOutputStream(zipFile))

        // 1. Direct chats in internal storage
        val chatsDir = File(context.filesDir, "direct_chats")
        if (chatsDir.exists() && chatsDir.isDirectory) {
            addDirectoryToZip(chatsDir, "direct_chats", zos)
        }

        // 2. Legacy / general chats
        val legacyChatsDir = File(context.filesDir, "chats")
        if (legacyChatsDir.exists() && legacyChatsDir.isDirectory) {
            addDirectoryToZip(legacyChatsDir, "chats", zos)
        }

        // 3. Gemini Notebooks
        val notebooksDir = File(context.filesDir, "notebooks")
        if (notebooksDir.exists() && notebooksDir.isDirectory) {
            addDirectoryToZip(notebooksDir, "notebooks", zos)
        }

        // 4. Custom chat folders metadata
        val chatFoldersFile = File(context.filesDir, "chat_folders.json")
        if (chatFoldersFile.exists() && chatFoldersFile.isFile) {
            addFileToZip(chatFoldersFile, "chat_folders.json", zos)
        }

        // 5. Termux workspace projects
        val homeDir = System.getenv("HOME") ?: "/data/user/0/com.termux/files/home"
        val projectsDir = File(homeDir, "projects")
        if (projectsDir.exists() && projectsDir.isDirectory) {
            addDirectoryToZip(projectsDir, "projects", zos)
        }

        // 6. Gemini brain & annotations in Termux home
        val geminiDir = File(homeDir, ".gemini/antigravity-cli")
        if (geminiDir.exists() && geminiDir.isDirectory) {
            val brain = File(geminiDir, "brain")
            if (brain.exists() && brain.isDirectory) {
                addDirectoryToZip(brain, ".gemini/antigravity-cli/brain", zos)
            }
            val annotations = File(geminiDir, "annotations")
            if (annotations.exists() && annotations.isDirectory) {
                addDirectoryToZip(annotations, ".gemini/antigravity-cli/annotations", zos)
            }
        }

        // 7. Claude workspace state/configs if present
        val claudeDir = File(homeDir, ".claude")
        if (claudeDir.exists() && claudeDir.isDirectory) {
            addDirectoryToZip(claudeDir, ".claude", zos)
        }

        zos.close()
    }

    private fun addFileToZip(file: File, entryName: String, zos: ZipOutputStream) {
        if (!file.exists() || !file.isFile) return
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    }

    private fun addDirectoryToZip(dir: File, baseName: String, zos: ZipOutputStream) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                addDirectoryToZip(f, "$baseName/${f.name}", zos)
            } else if (f.isFile && f.length() < 50 * 1024 * 1024) { // skip massive files > 50MB
                val entry = ZipEntry("$baseName/${f.name}")
                zos.putNextEntry(entry)
                FileInputStream(f).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    private fun unzipUserData(zipFile: File) {
        val homeDir = System.getenv("HOME") ?: "/data/user/0/com.termux/files/home"
        val zis = ZipInputStream(FileInputStream(zipFile))
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val name = entry.name
            val destFile = when {
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
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()
    }

    private fun getOrCreateDriveFolder(token: String, folderName: String): String {
        // Search if folder exists
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

        // Create folder
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

    private fun uploadZipToFolder(token: String, zipFile: File, folderId: String) {
        val metadata = JSONObject().apply {
            put("name", zipFile.name)
            put("parents", org.json.JSONArray().apply { put(folderId) })
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
                zipFile.name,
                zipFile.asRequestBody("application/zip".toMediaType())
            )
            .build()

        val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        val request = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                throw IllegalStateException("Upload failed: $err")
            }
        }
    }

    private fun getLatestBackupFileId(token: String, folderId: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?q='$folderId'+in+parents+and+trashed=false&orderBy=createdTime+desc&pageSize=1&fields=files(id,name,createdTime)"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
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
