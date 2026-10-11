package com.example.gemini.data.notebook

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class NotebookItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val sourceUrl: String = "",
    val tags: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

class NotebookManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NotebookManager"
        private const val DIR_NAME = "notebooks"
        private const val FILE_NAME = "notebooks.json"

        @Volatile
        private var instance: NotebookManager? = null

        fun getInstance(context: Context): NotebookManager {
            return instance ?: synchronized(this) {
                instance ?: NotebookManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val notebooksDir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
    private val notebooksFile = File(notebooksDir, FILE_NAME)

    private val _notebooks = MutableStateFlow<List<NotebookItem>>(emptyList())
    val notebooks: StateFlow<List<NotebookItem>> = _notebooks.asStateFlow()

    private val _attachedNotebook = MutableStateFlow<NotebookItem?>(null)
    val attachedNotebook: StateFlow<NotebookItem?> = _attachedNotebook.asStateFlow()

    init {
        loadNotebooks()
    }

    private fun loadNotebooks() {
        try {
            if (!notebooksFile.exists()) {
                // Populate default starter notebooks
                val defaults = listOf(
                    NotebookItem(
                        title = "JIN Project Guidelines & Architecture",
                        content = "This notebook contains key guidelines, Android architecture patterns, and Antigravity CLI workflows for continuous development.",
                        tags = listOf("architecture", "core")
                    ),
                    NotebookItem(
                        title = "Developer Scratchpad & API References",
                        content = "Quick references for Termux bridge APIs, MCP tools, and direct reasoning models.",
                        tags = listOf("scratchpad", "api")
                    )
                )
                _notebooks.value = defaults
                saveNotebooksInternal(defaults)
                return
            }

            val text = notebooksFile.readText()
            val arr = JSONArray(text)
            val list = mutableListOf<NotebookItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
                val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }
                list.add(
                    NotebookItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "Untitled Notebook"),
                        content = obj.optString("content", ""),
                        sourceUrl = obj.optString("source_url", ""),
                        tags = tags,
                        updatedAt = obj.optLong("updated_at", System.currentTimeMillis()),
                        createdAt = obj.optLong("created_at", System.currentTimeMillis())
                    )
                )
            }
            _notebooks.value = list.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load notebooks: ${e.message}", e)
            _notebooks.value = emptyList()
        }
    }

    suspend fun saveNotebook(notebook: NotebookItem) = withContext(Dispatchers.IO) {
        val current = _notebooks.value.toMutableList()
        val index = current.indexOfFirst { it.id == notebook.id }
        val updatedItem = notebook.copy(updatedAt = System.currentTimeMillis())
        if (index >= 0) {
            current[index] = updatedItem
        } else {
            current.add(0, updatedItem)
        }
        _notebooks.value = current.sortedByDescending { it.updatedAt }
        saveNotebooksInternal(_notebooks.value)

        if (_attachedNotebook.value?.id == notebook.id) {
            _attachedNotebook.value = updatedItem
        }
    }

    suspend fun deleteNotebook(id: String) = withContext(Dispatchers.IO) {
        val current = _notebooks.value.filter { it.id != id }
        _notebooks.value = current
        saveNotebooksInternal(current)
        if (_attachedNotebook.value?.id == id) {
            _attachedNotebook.value = null
        }
    }

    fun attachNotebook(notebook: NotebookItem?) {
        _attachedNotebook.value = notebook
    }

    fun detachNotebook() {
        _attachedNotebook.value = null
    }

    fun formatNotebookContext(notebook: NotebookItem): String {
        return buildString {
            append("\n[📓 ATTACHED NOTEBOOK: ")
            append(notebook.title)
            append("]\n")
            if (notebook.sourceUrl.isNotBlank()) {
                append("Source: ")
                append(notebook.sourceUrl)
                append("\n")
            }
            append("Content:\n")
            append(notebook.content)
            append("\n[END NOTEBOOK CONTEXT]\n")
        }
    }

    private fun saveNotebooksInternal(list: List<NotebookItem>) {
        try {
            val arr = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("content", item.content)
                    put("source_url", item.sourceUrl)
                    put("tags", JSONArray(item.tags))
                    put("updated_at", item.updatedAt)
                    put("created_at", item.createdAt)
                }
                arr.put(obj)
            }
            notebooksFile.writeText(arr.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist notebooks: ${e.message}", e)
        }
    }
}
