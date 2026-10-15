package com.example.gemini.data.agent.memory

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ProjectMemory(
    val projectName: String,
    val architecturalDecisions: List<String> = emptyList(),
    val codingRules: List<String> = emptyList(),
    val packageConventions: List<String> = emptyList(),
    val learnedFixes: List<String> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Manages persistent per-project memory stored in `.jin/memory.json`.
 * Retains project conventions, decisions, and patterns across agent sessions.
 */
class ProjectMemoryManager(private val context: Context? = null) {

    companion object {
        private const val TAG = "ProjectMemoryManager"
        private const val JIN_DIR = ".jin"
        private const val MEMORY_FILE_NAME = "memory.json"
    }

    suspend fun loadMemory(workspaceDir: File): ProjectMemory = withContext(Dispatchers.IO) {
        val memFile = File(File(workspaceDir, JIN_DIR), MEMORY_FILE_NAME)
        if (!memFile.exists()) {
            return@withContext ProjectMemory(projectName = workspaceDir.name)
        }
        try {
            val json = JSONObject(memFile.readText())
            ProjectMemory(
                projectName = json.optString("projectName", workspaceDir.name),
                architecturalDecisions = json.optJSONArray("architecturalDecisions").toStringList(),
                codingRules = json.optJSONArray("codingRules").toStringList(),
                packageConventions = json.optJSONArray("packageConventions").toStringList(),
                learnedFixes = json.optJSONArray("learnedFixes").toStringList(),
                lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing memory.json for ${workspaceDir.name}: ${e.message}")
            ProjectMemory(projectName = workspaceDir.name)
        }
    }

    suspend fun saveMemory(workspaceDir: File, memory: ProjectMemory) = withContext(Dispatchers.IO) {
        val dir = File(workspaceDir, JIN_DIR)
        if (!dir.exists()) dir.mkdirs()
        val memFile = File(dir, MEMORY_FILE_NAME)
        try {
            val json = JSONObject().apply {
                put("projectName", memory.projectName)
                put("architecturalDecisions", JSONArray(memory.architecturalDecisions))
                put("codingRules", JSONArray(memory.codingRules))
                put("packageConventions", JSONArray(memory.packageConventions))
                put("learnedFixes", JSONArray(memory.learnedFixes))
                put("lastUpdated", System.currentTimeMillis())
            }
            memFile.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing memory.json: ${e.message}", e)
        }
    }

    suspend fun addDecision(workspaceDir: File, decision: String) {
        val current = loadMemory(workspaceDir)
        if (!current.architecturalDecisions.contains(decision.trim())) {
            val updated = current.copy(
                architecturalDecisions = current.architecturalDecisions + decision.trim()
            )
            saveMemory(workspaceDir, updated)
        }
    }

    suspend fun addRule(workspaceDir: File, rule: String) {
        val current = loadMemory(workspaceDir)
        if (!current.codingRules.contains(rule.trim())) {
            val updated = current.copy(
                codingRules = current.codingRules + rule.trim()
            )
            saveMemory(workspaceDir, updated)
        }
    }

    suspend fun clearMemory(workspaceDir: File) = withContext(Dispatchers.IO) {
        val memFile = File(File(workspaceDir, JIN_DIR), MEMORY_FILE_NAME)
        if (memFile.exists()) memFile.delete()
    }

    suspend fun formatMemoryPrompt(workspaceDir: File): String {
        val memory = loadMemory(workspaceDir)
        if (memory.architecturalDecisions.isEmpty() &&
            memory.codingRules.isEmpty() &&
            memory.packageConventions.isEmpty() &&
            memory.learnedFixes.isEmpty()
        ) {
            return ""
        }

        return buildString {
            append("\n### 🧠 Project Memory & Conventions (${memory.projectName})\n")
            if (memory.architecturalDecisions.isNotEmpty()) {
                append("**Decisions**:\n")
                memory.architecturalDecisions.forEach { append("- $it\n") }
            }
            if (memory.codingRules.isNotEmpty()) {
                append("**Rules**:\n")
                memory.codingRules.forEach { append("- $it\n") }
            }
            if (memory.packageConventions.isNotEmpty()) {
                append("**Package Manager**:\n")
                memory.packageConventions.forEach { append("- $it\n") }
            }
            if (memory.learnedFixes.isNotEmpty()) {
                append("**Learned Fixes**:\n")
                memory.learnedFixes.forEach { append("- $it\n") }
            }
            append("\n")
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            list.add(getString(i))
        }
        return list
    }
}
