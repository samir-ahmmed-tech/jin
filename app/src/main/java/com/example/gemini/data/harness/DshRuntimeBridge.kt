package com.example.gemini.data.harness

import android.content.Context
import android.util.Log
import com.example.gemini.data.local.LocalEnvironmentManager
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

sealed interface DshEvent {
    val sessionId: String
    data class SessionStarted(override val sessionId: String) : DshEvent
    data class ReasoningDelta(override val sessionId: String, val text: String) : DshEvent
    data class AssistantDelta(override val sessionId: String, val text: String) : DshEvent
    data class StatusLog(override val sessionId: String, val message: String) : DshEvent
    data class SessionCompleted(override val sessionId: String, val finalAnswer: String) : DshEvent
    data class SessionFailed(override val sessionId: String, val error: String) : DshEvent
}

/**
 * DeepSeek Harness (`dsh`) Runtime Bridge for JIN.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
 *
 * Drives the official DeepSeek Harness coding agent inside JIN's isolated subsystem.
 */
class DshRuntimeBridge(
    private val context: Context,
    private val secretFor: (ProviderProfile) -> String?,
) {
    private val installer = HarnessRuntimeInstaller(context)
    private val checkpoints = WorkspaceCheckpoints(context.filesDir, context)
    private val _events = MutableSharedFlow<DshEvent>(extraBufferCapacity = 64)
    val events: Flow<DshEvent> = _events.asSharedFlow()

    private val activeProcesses = ConcurrentHashMap<String, Process>()

    companion object {
        const val TAG = "DshRuntimeBridge"
    }

    suspend fun executeTask(
        projectId: String,
        prompt: String,
        provider: ProviderProfile,
    ): String = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        _events.emit(DshEvent.SessionStarted(sessionId))

        val apiKey = secretFor(provider).orEmpty()
        if (apiKey.isBlank()) {
            val err = "No API key configured for ${provider.kind.title}."
            _events.emit(DshEvent.SessionFailed(sessionId, err))
            return@withContext sessionId
        }

        val workspace = checkpoints.ensureWorkspace(projectId)
        checkpoints.createCheckpoint(projectId, workspace)
        val beforeSnapshot = checkpoints.snapshot(workspace)

        var openRouterGateway: OpenRouterRoutingGateway? = null
        if (provider.kind == ProviderKind.LLM_ROUTER && provider.openRouterProviders.isNotEmpty()) {
            openRouterGateway = OpenRouterRoutingGateway(provider, apiKey).start()
        }

        try {
            val dshHomeDir = File(installer.rootfs, "root/.dsh").apply { mkdirs() }
            val settingsFile = File(dshHomeDir, "settings.yaml")
            val effectiveUrl = openRouterGateway?.url ?: provider.resolvedBaseUrl

            val yamlConfig = buildString {
                appendLine("version: 1")
                appendLine("default_model: \"${provider.model}\"")
                appendLine("routes:")
                appendLine("  primary:")
                appendLine("    base_url: \"$effectiveUrl\"")
                appendLine("    protocol: \"${provider.dshApi}\"")
            }
            settingsFile.writeText(yamlConfig)

            _events.emit(DshEvent.StatusLog(sessionId, "Starting DeepSeek Harness with model ${provider.model}..."))

            val command = listOf(
                "dsh",
                "--profile", "headless",
                prompt,
            )

            val envMap = mapOf(
                "DSH_HOME" to "/root/.dsh",
                "DSH_PERMISSION_MODE" to "danger-full-access",
                "DEEPSEEK_API_KEY" to apiKey,
                "OPENROUTER_API_KEY" to apiKey,
                "ANTHROPIC_API_KEY" to apiKey,
            )

            val dshExecutable = File(installer.rootfs, "usr/local/bin/dsh")
            if (!dshExecutable.exists()) {
                val err = "DeepSeek Harness runtime is not installed. Please install the 'dsh' bundle."
                _events.emit(DshEvent.SessionFailed(sessionId, err))
                return@withContext sessionId
            }

            val processBuilder = ProcessBuilder(
                listOf(
                    "/system/bin/sh",
                    "-c",
                    "export DSH_HOME=/root/.dsh DSH_PERMISSION_MODE=danger-full-access; " +
                        command.joinToString(" ") { "\"${it.replace("\"", "\\\"")}\"" },
                ),
            )
            processBuilder.directory(workspace)
            processBuilder.environment().putAll(envMap)
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            activeProcesses[sessionId] = process

            val outputBuffer = StringBuilder()
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    when {
                        line.startsWith("dsh: reasoning:") -> {
                            val reasoning = line.removePrefix("dsh: reasoning:").trim()
                            _events.emit(DshEvent.ReasoningDelta(sessionId, reasoning))
                        }
                        line.startsWith("dsh:") -> {
                            _events.emit(DshEvent.StatusLog(sessionId, line))
                        }
                        else -> {
                            outputBuffer.appendLine(line)
                            _events.emit(DshEvent.AssistantDelta(sessionId, line))
                        }
                    }
                }
            }

            val exitCode = process.waitFor()
            activeProcesses.remove(sessionId)

            // Check for file modifications
            val changed = checkpoints.changedFiles(workspace, beforeSnapshot)
            if (changed.isNotEmpty()) {
                checkpoints.saveChangedPaths(projectId, changed)
                _events.emit(DshEvent.StatusLog(sessionId, "Workspace updated: ${changed.size} files changed."))
            }

            if (exitCode == 0) {
                _events.emit(DshEvent.SessionCompleted(sessionId, outputBuffer.toString().trim()))
            } else {
                _events.emit(DshEvent.SessionFailed(sessionId, "Process exited with code $exitCode"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "DSH session execution failed: ${e.message}", e)
            _events.emit(DshEvent.SessionFailed(sessionId, e.message ?: "Execution failed"))
        } finally {
            openRouterGateway?.close()
        }

        sessionId
    }

    fun stopSession(sessionId: String) {
        activeProcesses.remove(sessionId)?.destroyForcibly()
    }
}
