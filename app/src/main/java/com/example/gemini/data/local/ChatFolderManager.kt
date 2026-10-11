package com.example.gemini.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.gemini.domain.model.Conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages user-created folders for organizing chats and projects.
 * Persists folders and chat assignments across app launches.
 */
class ChatFolderManager private constructor(context: Context) {

    companion object {
        const val DEFAULT_FOLDER = "Main Workspace"
        private const val PREFS_NAME = "jin_chat_folders_prefs"
        private const val KEY_FOLDERS = "custom_folders_list"
        private const val KEY_ASSIGNMENTS = "conversation_folder_map"

        @Volatile
        private var instance: ChatFolderManager? = null

        fun getInstance(context: Context): ChatFolderManager {
            return instance ?: synchronized(this) {
                instance ?: ChatFolderManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _folders = MutableStateFlow<List<String>>(loadFolders())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _assignments = MutableStateFlow<Map<String, String>>(loadAssignments())
    val assignments: StateFlow<Map<String, String>> = _assignments.asStateFlow()

    private fun loadFolders(): List<String> {
        val jsonStr = prefs.getString(KEY_FOLDERS, null) ?: return listOf(DEFAULT_FOLDER)
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val f = arr.getString(i).trim()
                if (f.isNotBlank() && !list.contains(f)) {
                    list.add(f)
                }
            }
            if (!list.contains(DEFAULT_FOLDER)) {
                list.add(0, DEFAULT_FOLDER)
            }
            list
        } catch (_: Exception) {
            listOf(DEFAULT_FOLDER)
        }
    }

    private fun saveFolders(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_FOLDERS, arr.toString()).apply()
        _folders.value = list
    }

    private fun loadAssignments(): Map<String, String> {
        val jsonStr = prefs.getString(KEY_ASSIGNMENTS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getString(k)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveAssignments(map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_ASSIGNMENTS, obj.toString()).apply()
        _assignments.value = map
    }

    fun createFolder(folderName: String): Boolean {
        val clean = folderName.trim()
        if (clean.isBlank()) return false
        val current = _folders.value.toMutableList()
        if (current.any { it.equals(clean, ignoreCase = true) }) return false
        current.add(clean)
        saveFolders(current)
        return true
    }

    fun deleteFolder(folderName: String) {
        val clean = folderName.trim()
        if (clean == DEFAULT_FOLDER) return
        val current = _folders.value.filter { !it.equals(clean, ignoreCase = true) }
        saveFolders(current)

        // Move any conversations in this folder back to DEFAULT_FOLDER
        val currentAssign = _assignments.value.toMutableMap()
        val toUpdate = currentAssign.filter { it.value.equals(clean, ignoreCase = true) }
        toUpdate.forEach { (convId, _) ->
            currentAssign[convId] = DEFAULT_FOLDER
        }
        saveAssignments(currentAssign)
    }

    fun renameFolder(oldName: String, newName: String): Boolean {
        val cleanOld = oldName.trim()
        val cleanNew = newName.trim()
        if (cleanOld == DEFAULT_FOLDER || cleanNew.isBlank()) return false
        val current = _folders.value.toMutableList()
        val index = current.indexOfFirst { it.equals(cleanOld, ignoreCase = true) }
        if (index == -1) return false
        if (current.any { it.equals(cleanNew, ignoreCase = true) }) return false
        current[index] = cleanNew
        saveFolders(current)

        val currentAssign = _assignments.value.toMutableMap()
        currentAssign.forEach { (k, v) ->
            if (v.equals(cleanOld, ignoreCase = true)) {
                currentAssign[k] = cleanNew
            }
        }
        saveAssignments(currentAssign)
        return true
    }

    fun moveConversationToFolder(conversationId: String, folderName: String) {
        val currentAssign = _assignments.value.toMutableMap()
        val target = if (_folders.value.any { it.equals(folderName, ignoreCase = true) }) {
            folderName
        } else {
            DEFAULT_FOLDER
        }
        currentAssign[conversationId] = target
        saveAssignments(currentAssign)
    }

    fun getFolderForConversation(conversationId: String, defaultWorkspaceName: String = DEFAULT_FOLDER): String {
        return _assignments.value[conversationId] ?: defaultWorkspaceName.ifBlank { DEFAULT_FOLDER }
    }

    fun groupConversations(
        conversations: List<Conversation>,
        defaultWorkspaceName: String = DEFAULT_FOLDER
    ): Map<String, List<Conversation>> {
        val map = mutableMapOf<String, MutableList<Conversation>>()
        // Ensure all folders exist in the map
        for (f in _folders.value) {
            map[f] = mutableListOf()
        }

        for (c in conversations) {
            val assigned = getFolderForConversation(c.id, defaultWorkspaceName)
            val list = map.getOrPut(assigned) { mutableListOf() }
            list.add(c)
        }

        return map
    }
}
