package com.example.gemini.data.agent.diff

import android.content.Context
import com.example.gemini.data.harness.WorkspaceCheckpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class FileChangeType {
    MODIFIED,
    CREATED,
    DELETED
}

data class FileDiffReviewItem(
    val path: String,
    val changeType: FileChangeType,
    val additionsCount: Int,
    val deletionsCount: Int,
    val rawUnifiedDiff: String,
    val isAccepted: Boolean = false
)

/**
 * Reviews multi-file edits performed by autonomous coding agents.
 * Generates unified diffs and provides granular rollback / acceptance controls.
 */
class MultiFileDiffReviewer(
    private val context: Context,
    private val checkpoints: WorkspaceCheckpoints
) {

    companion object {
        @Volatile
        private var INSTANCE: MultiFileDiffReviewer? = null

        fun getInstance(context: Context): MultiFileDiffReviewer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MultiFileDiffReviewer(
                    context.applicationContext,
                    WorkspaceCheckpoints(context.applicationContext.filesDir, context.applicationContext)
                ).also { INSTANCE = it }
            }
        }
    }

    private val _diffItems = MutableStateFlow<List<FileDiffReviewItem>>(emptyList())
    val diffItems: StateFlow<List<FileDiffReviewItem>> = _diffItems.asStateFlow()
    val pendingDiffs: StateFlow<List<FileDiffReviewItem>> = _diffItems.asStateFlow()

    suspend fun rollbackFile(path: String) = withContext(Dispatchers.IO) {
        val current = _diffItems.value.toMutableList()
        current.removeAll { it.path == path }
        _diffItems.value = current
    }

    suspend fun acceptFile(path: String) = withContext(Dispatchers.IO) {
        val current = _diffItems.value.toMutableList()
        current.removeAll { it.path == path }
        _diffItems.value = current
    }

    suspend fun rollbackAll() = withContext(Dispatchers.IO) {
        _diffItems.value = emptyList()
    }

    suspend fun acceptAll() = withContext(Dispatchers.IO) {
        _diffItems.value = emptyList()
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    suspend fun computeWorkspaceDiffs(projectId: String, workspaceDir: File): List<FileDiffReviewItem> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            val checkpointDir = checkpoints.checkpointDir(projectId)
            val backupProjectDir = File(checkpointDir, "project")
            val changedPaths = checkpoints.readChangedPaths(projectId)

            val results = mutableListOf<FileDiffReviewItem>()
            for (relPath in changedPaths) {
                val currentFile = File(workspaceDir, relPath)
                val originalFile = File(backupProjectDir, relPath)

                val (changeType, oldLines, newLines) = when {
                    !originalFile.exists() && currentFile.exists() -> {
                        Triple(FileChangeType.CREATED, emptyList(), currentFile.readLinesSafely())
                    }
                    originalFile.exists() && !currentFile.exists() -> {
                        Triple(FileChangeType.DELETED, originalFile.readLinesSafely(), emptyList())
                    }
                    else -> {
                        Triple(FileChangeType.MODIFIED, originalFile.readLinesSafely(), currentFile.readLinesSafely())
                    }
                }

                val diffText = buildUnifiedDiff(relPath, oldLines, newLines)
                var adds = 0
                var dels = 0
                diffText.lines().forEach { l ->
                    if (l.startsWith("+") && !l.startsWith("+++")) adds++
                    else if (l.startsWith("-") && !l.startsWith("---")) dels++
                }

                results.add(
                    FileDiffReviewItem(
                        path = relPath,
                        changeType = changeType,
                        additionsCount = adds,
                        deletionsCount = dels,
                        rawUnifiedDiff = diffText
                    )
                )
            }
            _diffItems.value = results
            results
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun rollbackFile(projectId: String, workspaceDir: File, relPath: String) = withContext(Dispatchers.IO) {
        checkpoints.undoChange(projectId, workspaceDir, relPath)
        computeWorkspaceDiffs(projectId, workspaceDir)
    }

    suspend fun acceptFile(projectId: String, workspaceDir: File, relPath: String) = withContext(Dispatchers.IO) {
        checkpoints.keepChange(projectId, relPath)
        computeWorkspaceDiffs(projectId, workspaceDir)
    }

    suspend fun rollbackAll(projectId: String, workspaceDir: File) = withContext(Dispatchers.IO) {
        val currentPaths = _diffItems.value.map { it.path }
        for (path in currentPaths) {
            checkpoints.undoChange(projectId, workspaceDir, path)
        }
        computeWorkspaceDiffs(projectId, workspaceDir)
    }

    suspend fun acceptAll(projectId: String, workspaceDir: File) = withContext(Dispatchers.IO) {
        checkpoints.checkpointDir(projectId).deleteRecursively()
        checkpoints.createCheckpoint(projectId, workspaceDir)
        _diffItems.value = emptyList()
    }

    private fun File.readLinesSafely(): List<String> {
        return if (exists() && isFile) {
            runCatching { readLines() }.getOrDefault(emptyList())
        } else emptyList()
    }

    private fun buildUnifiedDiff(relPath: String, oldLines: List<String>, newLines: List<String>): String {
        return buildString {
            append("--- a/$relPath\n")
            append("+++ b/$relPath\n")
            append("@@ -1,${oldLines.size.coerceAtLeast(1)} +1,${newLines.size.coerceAtLeast(1)} @@\n")

            // Simple line-level unified diff generator
            var oldIdx = 0
            var newIdx = 0
            while (oldIdx < oldLines.size || newIdx < newLines.size) {
                val oldLine = oldLines.getOrNull(oldIdx)
                val newLine = newLines.getOrNull(newIdx)

                if (oldLine == newLine) {
                    append(" $oldLine\n")
                    oldIdx++
                    newIdx++
                } else if (oldLine != null && (newLine == null || !newLines.contains(oldLine))) {
                    append("-$oldLine\n")
                    oldIdx++
                } else if (newLine != null) {
                    append("+$newLine\n")
                    newIdx++
                }
            }
        }
    }
}
