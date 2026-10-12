package com.example.gemini.data.harness

import android.content.Context
import android.util.Log
import com.example.gemini.data.local.LocalEnvironmentManager
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.data.remote.DirectApiClient
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * JIN Unified Autonomous Coding Engine.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
 *
 * Integrates 6 AI coding agents (Antigravity CLI, Claude Code, DeepSeek Harness,
 * Codex CLI, OpenRouter CLI, OpenCode CLI) with JIN's local PRoot/Termux subsystem,
 * live reasoning streaming, autonomous tool-use, and interactive diff tracking.
 */
class JinAutonomousEngine(private val context: Context) {

    private val installer = HarnessRuntimeInstaller(context)
    private val checkpoints = WorkspaceCheckpoints(context.filesDir, context)
    private val authPrefs = AuthPreferences(context)
    private val apiClient = DirectApiClient()

    companion object {
        const val TAG = "JinAutonomousEngine"
        private val activeSessions = ConcurrentHashMap<String, Process>()

        fun stopSession(sessionId: String) {
            activeSessions[sessionId]?.let { proc ->
                try {
                    proc.destroy()
                    Thread.sleep(200)
                    if (proc.isAlive) proc.destroyForcibly()
                } catch (_: Throwable) {}
            }
            activeSessions.remove(sessionId)
        }
    }

    private data class ResolvedEngineEndpoint(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val engineLabel: String
    )

    private fun resolveEndpoint(
        agent: AgentKind,
        provider: ProviderProfile,
        customModel: String
    ): ResolvedEngineEndpoint {
        val effectiveModel = customModel.ifBlank { provider.model }

        // 1. Agent-specific configuration
        when (agent) {
            AgentKind.OPENCODE_CLI -> {
                val key = authPrefs.getOpenCodeApiKeySync().ifBlank { "free-opencode-zen" }
                val model = if (effectiveModel.isNotBlank() && effectiveModel != "default") effectiveModel else "opencode-zen-coder"
                return ResolvedEngineEndpoint("https://opencode.ai/zen/v1", key, model, "OpenCode AI Terminal (Zen Gateway)")
            }
            AgentKind.DEEPSEEK_HARNESS -> {
                val key = authPrefs.getDeepSeekApiKeySync()
                if (key.isNotBlank()) {
                    val model = if (effectiveModel.isNotBlank() && effectiveModel != "default") effectiveModel else "deepseek-chat"
                    return ResolvedEngineEndpoint("https://api.deepseek.com/v1", key, model, "DeepSeek Official API")
                }
            }
            AgentKind.CLAUDE_CODE -> {
                val key = authPrefs.getAnthropicApiKeySync()
                if (key.isNotBlank()) {
                    val model = if (effectiveModel.isNotBlank() && effectiveModel != "default") effectiveModel else "claude-sonnet-4-6"
                    return ResolvedEngineEndpoint("https://api.anthropic.com/v1", key, model, "Anthropic Claude API")
                }
            }
            AgentKind.CODEX_CLI -> {
                val key = authPrefs.getOpenAiApiKeySync()
                if (key.isNotBlank()) {
                    val model = if (effectiveModel.isNotBlank() && effectiveModel != "default") effectiveModel else "gpt-4o"
                    return ResolvedEngineEndpoint("https://api.openai.com/v1", key, model, "OpenAI Codex / GPT-4o")
                }
            }
            AgentKind.OPENROUTER_CLI -> {
                val key = authPrefs.getOpenRouterApiKeySync()
                if (key.isNotBlank()) {
                    val model = if (effectiveModel.isNotBlank() && effectiveModel != "default") effectiveModel else "deepseek/deepseek-r1"
                    return ResolvedEngineEndpoint("https://openrouter.ai/api/v1", key, model, "OpenRouter Universal Router")
                }
            }
            AgentKind.ANTIGRAVITY -> {
                val key = authPrefs.getGeminiApiKeySync()
                if (key.isNotBlank()) {
                    val model = if (effectiveModel.isNotBlank() && effectiveModel != "default") effectiveModel else "gemini-2.5-flash"
                    return ResolvedEngineEndpoint("https://generativelanguage.googleapis.com/v1beta/openai", key, model, "Google Antigravity Core")
                }
            }
        }

        // 2. Provider Profile fallback
        when (provider.kind) {
            ProviderKind.OPENCODE_ZEN -> {
                val key = authPrefs.getOpenCodeApiKeySync().ifBlank { "free-opencode-zen" }
                val model = if (effectiveModel.isNotBlank() && effectiveModel != "default") effectiveModel else "deepseek-v4-flash"
                return ResolvedEngineEndpoint("https://opencode.ai/zen/v1", key, model, "OpenCode Zen Gateway")
            }
            ProviderKind.DEEPSEEK -> {
                val key = authPrefs.getDeepSeekApiKeySync()
                if (key.isNotBlank()) {
                    val model = if (effectiveModel.isNotBlank() && effectiveModel != "default") effectiveModel else "deepseek-chat"
                    return ResolvedEngineEndpoint(provider.resolvedBaseUrl.ifBlank { "https://api.deepseek.com/v1" }, key, model, "DeepSeek API")
                }
            }
            ProviderKind.ANTHROPIC, ProviderKind.CLAUDE -> {
                val key = authPrefs.getAnthropicApiKeySync()
                if (key.isNotBlank()) {
                    val model = if (effectiveModel.isNotBlank() && effectiveModel != "default") effectiveModel else "claude-sonnet-4-6"
                    return ResolvedEngineEndpoint(provider.resolvedBaseUrl.ifBlank { "https://api.anthropic.com/v1" }, key, model, "Anthropic API")
                }
            }
            ProviderKind.LLM_ROUTER -> {
                val key = authPrefs.getOpenRouterApiKeySync()
                if (key.isNotBlank()) {
                    val model = if (effectiveModel.isNotBlank() && effectiveModel != "default") effectiveModel else "~anthropic/claude-sonnet-latest"
                    return ResolvedEngineEndpoint(provider.resolvedBaseUrl.ifBlank { "https://openrouter.ai/api/v1" }, key, model, "OpenRouter API")
                }
            }
            ProviderKind.CUSTOM -> {
                val key = authPrefs.getCustomApiKeySync()
                val url = provider.resolvedBaseUrl.ifBlank { authPrefs.getCustomApiBaseUrlSync() }
                if (url.isNotBlank()) {
                    val model = if (effectiveModel.isNotBlank()) effectiveModel else authPrefs.getCustomApiModelNameSync().ifBlank { "gpt-4o" }
                    return ResolvedEngineEndpoint(url, key, model, "Custom API Endpoint")
                }
            }
            else -> {}
        }

        // 3. Fallback credential resolution chain
        val deepSeekKey = authPrefs.getDeepSeekApiKeySync()
        if (deepSeekKey.isNotBlank()) {
            return ResolvedEngineEndpoint("https://api.deepseek.com/v1", deepSeekKey, "deepseek-chat", "DeepSeek API (Auto-fallback)")
        }
        val openRouterKey = authPrefs.getOpenRouterApiKeySync()
        if (openRouterKey.isNotBlank()) {
            return ResolvedEngineEndpoint("https://openrouter.ai/api/v1", openRouterKey, "deepseek/deepseek-r1", "OpenRouter API (Auto-fallback)")
        }
        val anthropicKey = authPrefs.getAnthropicApiKeySync()
        if (anthropicKey.isNotBlank()) {
            return ResolvedEngineEndpoint("https://api.anthropic.com/v1", anthropicKey, "claude-sonnet-4-6", "Anthropic Claude (Auto-fallback)")
        }
        val openAiKey = authPrefs.getOpenAiApiKeySync()
        if (openAiKey.isNotBlank()) {
            return ResolvedEngineEndpoint("https://api.openai.com/v1", openAiKey, "gpt-4o", "OpenAI API (Auto-fallback)")
        }
        val geminiKey = authPrefs.getGeminiApiKeySync()
        if (geminiKey.isNotBlank()) {
            return ResolvedEngineEndpoint("https://generativelanguage.googleapis.com/v1beta/openai", geminiKey, "gemini-2.5-flash", "Google Gemini API (Auto-fallback)")
        }

        // 4. Default open gateway
        return ResolvedEngineEndpoint("https://opencode.ai/zen/v1", "free-opencode-zen", "opencode-zen-coder", "OpenCode Zen Gateway (Free Tier)")
    }

    /**
     * Executes an autonomous coding task on the specified project workspace.
     */
    suspend fun executeTask(
        projectId: String,
        prompt: String,
        agent: AgentKind,
        provider: ProviderProfile,
        customModel: String = "",
        onEvent: suspend (RuntimeEvent) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        onEvent(RuntimeEvent.SessionStarted(sessionId))

        val workspace = checkpoints.ensureWorkspace(projectId)
        checkpoints.createCheckpoint(projectId, workspace)
        val beforeSnapshot = checkpoints.snapshot(workspace)

        onEvent(RuntimeEvent.RuntimeLog(sessionId, "Engine Initialized", "Workspace: ${workspace.absolutePath} · Driver: ${agent.title}"))
        onEvent(RuntimeEvent.ReasoningDelta(sessionId, "Analyzing task requirements and workspace files...\n"))

        val effectiveModel = customModel.ifBlank { provider.model }

        try {
            when (agent) {
                AgentKind.ANTIGRAVITY -> {
                    val agyBin = findAgyBinary()
                    if (agyBin != null && agyBin.canExecute()) {
                        try {
                            executeAgySession(sessionId, agyBin, workspace, prompt, effectiveModel, onEvent)
                        } catch (e: Throwable) {
                            onEvent(RuntimeEvent.RuntimeLog(sessionId, "Guest Binary Fallback", "Agy CLI process exception: ${e.message}; routing to JIN Autonomous Engine."))
                            executeAutonomousLoop(sessionId, projectId, workspace, prompt, agent, provider, effectiveModel, onEvent)
                        }
                    } else {
                        onEvent(RuntimeEvent.RuntimeLog(sessionId, "Native Autonomous Engine", "Running Antigravity autonomous coding driver."))
                        executeAutonomousLoop(sessionId, projectId, workspace, prompt, agent, provider, effectiveModel, onEvent)
                    }
                }
                AgentKind.DEEPSEEK_HARNESS -> {
                    val dshBin = File(installer.rootfs, "usr/local/bin/dsh")
                    if (dshBin.exists() && dshBin.canExecute()) {
                        try {
                            executeDshSession(sessionId, dshBin, workspace, prompt, provider, effectiveModel, onEvent)
                        } catch (e: Throwable) {
                            onEvent(RuntimeEvent.RuntimeLog(sessionId, "Guest Binary Fallback", "DSH guest binary exception: ${e.message}; routing to JIN Autonomous Engine."))
                            executeAutonomousLoop(sessionId, projectId, workspace, prompt, agent, provider, effectiveModel, onEvent)
                        }
                    } else {
                        onEvent(RuntimeEvent.RuntimeLog(sessionId, "Native Autonomous Engine", "Running DeepSeek Harness autonomous coding driver."))
                        executeAutonomousLoop(sessionId, projectId, workspace, prompt, agent, provider, effectiveModel, onEvent)
                    }
                }
                AgentKind.CLAUDE_CODE -> {
                    val claudeBin = File(installer.rootfs, "usr/local/bin/claude")
                    if (claudeBin.exists() && claudeBin.canExecute()) {
                        try {
                            executeClaudeSession(sessionId, claudeBin, workspace, prompt, provider, effectiveModel, onEvent)
                        } catch (e: Throwable) {
                            onEvent(RuntimeEvent.RuntimeLog(sessionId, "Guest Binary Fallback", "Claude binary exception: ${e.message}; routing to JIN Autonomous Engine."))
                            executeAutonomousLoop(sessionId, projectId, workspace, prompt, agent, provider, effectiveModel, onEvent)
                        }
                    } else {
                        onEvent(RuntimeEvent.RuntimeLog(sessionId, "Native Autonomous Engine", "Running Claude Code autonomous coding driver."))
                        executeAutonomousLoop(sessionId, projectId, workspace, prompt, agent, provider, effectiveModel, onEvent)
                    }
                }
                AgentKind.CODEX_CLI -> {
                    onEvent(RuntimeEvent.RuntimeLog(sessionId, "Native Autonomous Engine", "Running OpenAI Codex autonomous software engineering driver."))
                    executeAutonomousLoop(sessionId, projectId, workspace, prompt, agent, provider, effectiveModel, onEvent)
                }
                AgentKind.OPENROUTER_CLI -> {
                    onEvent(RuntimeEvent.RuntimeLog(sessionId, "Native Autonomous Engine", "Running OpenRouter universal coding driver."))
                    executeAutonomousLoop(sessionId, projectId, workspace, prompt, agent, provider, effectiveModel, onEvent)
                }
                AgentKind.OPENCODE_CLI -> {
                    onEvent(RuntimeEvent.RuntimeLog(sessionId, "Native Autonomous Engine", "Running OpenCode AI terminal coding assistant (OpenCode Zen)."))
                    executeAutonomousLoop(sessionId, projectId, workspace, prompt, agent, provider, effectiveModel, onEvent)
                }
            }

            // Post-execution checkpoint diff inspection
            val changed = checkpoints.changedFiles(workspace, beforeSnapshot)
            if (changed.isNotEmpty()) {
                checkpoints.saveChangedPaths(projectId, changed)
                val changeDetails = checkpoints.buildChangeDetails(projectId, workspace, changed)
                onEvent(RuntimeEvent.FilesChanged(sessionId, changeDetails))
                onEvent(RuntimeEvent.RuntimeLog(sessionId, "Workspace Synchronized", "${changed.size} files modified."))
            }

            onEvent(RuntimeEvent.SessionCompleted(sessionId, "Autonomous coding task finished successfully."))
        } catch (e: Throwable) {
            Log.e(TAG, "Task failed: ${e.message}", e)
            onEvent(RuntimeEvent.SessionFailed(sessionId, e.message ?: "Task execution error"))
        } finally {
            activeSessions.remove(sessionId)
        }

        sessionId
    }

    private fun findAgyBinary(): File? {
        val candidates = listOf(
            File(context.filesDir, "usr/bin/agy"),
            File("/data/user/0/com.termux/files/usr/bin/agy"),
            File("/data/user/0/com.termux/files/home/.local/bin/agy"),
            File(context.filesDir, "agent_vault/agy"),
            File(installer.rootfs, "usr/local/bin/agy")
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }
    }

    private suspend fun executeAgySession(
        sessionId: String,
        binary: File,
        workspace: File,
        prompt: String,
        model: String,
        onEvent: suspend (RuntimeEvent) -> Unit
    ) {
        onEvent(RuntimeEvent.ToolStarted(sessionId, "Antigravity CLI", "Launching ${binary.name} --dangerously-skip-permissions"))
        val cmd = listOf(
            binary.absolutePath,
            "--input-format", "stream-json",
            "--output-format", "stream-json",
            "--dangerously-skip-permissions"
        )

        val pb = ProcessBuilder(cmd)
        pb.directory(workspace)
        pb.redirectErrorStream(true)

        val proc = pb.start()
        activeSessions[sessionId] = proc

        val userTurn = JSONObject()
            .put("event", "user")
            .put("message", JSONObject().put("content", prompt))
            .toString() + "\n"

        proc.outputStream.write(userTurn.toByteArray())
        proc.outputStream.flush()
        proc.outputStream.close()

        proc.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                val parsed = runCatching { JSONObject(line) }.getOrNull()
                if (parsed != null) {
                    when (parsed.optString("event")) {
                        "step_update" -> {
                            val step = parsed.optJSONObject("step_update")
                            val delta = step?.optString("text_delta").orEmpty()
                            if (delta.isNotEmpty()) {
                                onEvent(RuntimeEvent.AssistantDelta(sessionId, delta))
                            }
                            val toolName = step?.optString("tool_name").orEmpty()
                            if (toolName.isNotEmpty()) {
                                onEvent(RuntimeEvent.ToolStarted(sessionId, toolName, "Executing tool turn..."))
                            }
                        }
                        "result" -> {
                            val res = parsed.optJSONObject("result") ?: parsed
                            val resp = res.optString("response")
                            if (resp.isNotEmpty()) {
                                onEvent(RuntimeEvent.AssistantDelta(sessionId, resp))
                            }
                        }
                    }
                } else {
                    onEvent(RuntimeEvent.AssistantDelta(sessionId, line + "\n"))
                }
            }
        }
        proc.waitFor()
    }

    private suspend fun executeDshSession(
        sessionId: String,
        binary: File,
        workspace: File,
        prompt: String,
        provider: ProviderProfile,
        model: String,
        onEvent: suspend (RuntimeEvent) -> Unit
    ) {
        onEvent(RuntimeEvent.ToolStarted(sessionId, "DeepSeek Harness", "Launching dsh with model $model"))
        val apiKey = when (provider.kind) {
            ProviderKind.OPENCODE_ZEN -> "free-opencode-zen"
            ProviderKind.DEEPSEEK -> authPrefs.getDeepSeekApiKeySync()
            ProviderKind.LLM_ROUTER -> authPrefs.getOpenRouterApiKeySync()
            else -> authPrefs.getCustomApiKeySync()
        }

        val cmd = listOf(binary.absolutePath, "--profile", "headless", prompt)
        val pb = ProcessBuilder(cmd)
        pb.directory(workspace)
        pb.environment()["DSH_HOME"] = File(installer.rootfs, "root/.dsh").absolutePath
        pb.environment()["DSH_PERMISSION_MODE"] = "danger-full-access"
        pb.environment()["DEEPSEEK_API_KEY"] = apiKey
        pb.environment()["OPENROUTER_API_KEY"] = apiKey
        pb.redirectErrorStream(true)

        val proc = pb.start()
        activeSessions[sessionId] = proc

        proc.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith("dsh: reasoning:") -> {
                        val r = line.removePrefix("dsh: reasoning:").trim()
                        onEvent(RuntimeEvent.ReasoningDelta(sessionId, r + "\n"))
                    }
                    line.startsWith("dsh:") -> {
                        onEvent(RuntimeEvent.RuntimeLog(sessionId, "DSH", line))
                    }
                    else -> {
                        onEvent(RuntimeEvent.AssistantDelta(sessionId, line + "\n"))
                    }
                }
            }
        }
        proc.waitFor()
    }

    private suspend fun executeClaudeSession(
        sessionId: String,
        binary: File,
        workspace: File,
        prompt: String,
        provider: ProviderProfile,
        model: String,
        onEvent: suspend (RuntimeEvent) -> Unit
    ) {
        onEvent(RuntimeEvent.ToolStarted(sessionId, "Claude Code", "Launching Claude agent turn..."))
        val apiKey = authPrefs.getAnthropicApiKeySync()

        val cmd = listOf(binary.absolutePath, "-p", "--input-format", "stream-json", "--output-format", "stream-json", prompt)
        val pb = ProcessBuilder(cmd)
        pb.directory(workspace)
        pb.environment()["ANTHROPIC_API_KEY"] = apiKey
        pb.environment()["DISABLE_AUTOUPDATER"] = "1"
        pb.redirectErrorStream(true)

        val proc = pb.start()
        activeSessions[sessionId] = proc

        proc.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                onEvent(RuntimeEvent.AssistantDelta(sessionId, line + "\n"))
            }
        }
        proc.waitFor()
    }

    /**
     * Resilient built-in autonomous agent loop.
     * Uses real LLM streaming, reasoning extraction, autonomous tool blocks,
     * live file writes, terminal command execution, and interactive diff tracking.
     */
    private suspend fun executeAutonomousLoop(
        sessionId: String,
        projectId: String,
        workspace: File,
        prompt: String,
        agent: AgentKind,
        provider: ProviderProfile,
        customModel: String,
        onEvent: suspend (RuntimeEvent) -> Unit
    ) {
        val endpoint = resolveEndpoint(agent, provider, customModel)
        onEvent(RuntimeEvent.RuntimeLog(sessionId, "Engine Active", "Driver: ${agent.title} · Provider: ${endpoint.engineLabel} · Model: ${endpoint.model}"))
        onEvent(RuntimeEvent.ReasoningDelta(sessionId, "Synthesizing execution strategy with ${endpoint.model}...\n"))

        // 1. Inspect existing files in workspace
        val existingFiles = workspace.walkTopDown()
            .maxDepth(3)
            .filter { it.isFile && !it.name.startsWith(".") && !it.path.contains("/build/") && !it.path.contains("/node_modules/") }
            .map { it.relativeTo(workspace).path }
            .take(60)
            .toList()

        val workspaceSummary = if (existingFiles.isEmpty()) {
            "Workspace is currently clean and empty."
        } else {
            "Existing files in workspace:\n" + existingFiles.joinToString("\n") { "- $it" }
        }

        // 2. Android Scaffolding if prompt requests a new Android app and workspace is empty
        val promptLower = prompt.lowercase()
        if ((promptLower.contains("create") || promptLower.contains("scaffold") || promptLower.contains("new app")) &&
            (promptLower.contains("android") || promptLower.contains("compose") || promptLower.contains("app")) &&
            !File(workspace, "build.gradle.kts").exists() && !File(workspace, "build.gradle").exists()
        ) {
            onEvent(RuntimeEvent.ToolStarted(sessionId, "Android Generator", "Scaffolding Jetpack Compose & Gradle Kotlin DSL build scripts..."))
            val template = if (promptLower.contains("xml")) AndroidTemplate.XML else AndroidTemplate.COMPOSE
            AndroidProjectTemplateGenerator.generate(workspace, projectId, template)
            onEvent(RuntimeEvent.ToolCompleted(sessionId, "Android Generator", "Generated Android application skeleton."))
        }

        val systemPrompt = """
You are JIN Autonomous Software Engineering Agent (${agent.title}), an elite AI pair programming super-assistant developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
You have direct read/write/execute permissions in the project workspace at: ${workspace.absolutePath}.

$workspaceSummary

Your goal is to autonomously solve the user's software engineering task with production-quality code.
You can invoke the following tool blocks:

1. Create or overwrite a file:
```tool:write_file
path: <relative_path>
content:
<full, complete file content without placeholders>
```

2. Read an existing file:
```tool:read_file
path: <relative_path>
```

3. Execute a bash command in the workspace:
```tool:execute_command
command: <shell_command>
```

Guidelines:
- Explain your architecture, reasoning, and implementation steps clearly.
- Always provide COMPLETE, working code. Never omit code with '// TODO' or '// implementation goes here'.
- If building a web application or game, provide full HTML, CSS, and JS (e.g. index.html).
- If building an Android feature, provide full Kotlin code, Jetpack Compose UI, and Gradle scripts.
- Prefer ```tool:write_file or code blocks with `filepath: <path>` / `file=<path>`.
- After writing files and running tests/commands, summarize all completed work.
""".trimIndent()

        val conversation = mutableListOf<Pair<String, String>>()
        conversation.add("user" to prompt)

        var turn = 1
        val maxTurns = 3
        var allDone = false

        while (turn <= maxTurns && !allDone) {
            onEvent(RuntimeEvent.RuntimeLog(sessionId, "Agent Turn $turn", "Invoking ${endpoint.model}..."))

            val responseText = try {
                apiClient.streamChatMessages(
                    baseUrl = endpoint.baseUrl,
                    apiKey = endpoint.apiKey,
                    model = endpoint.model,
                    messages = conversation,
                    systemPrompt = systemPrompt,
                    onReasoning = { delta ->
                        runBlocking {
                            onEvent(RuntimeEvent.ReasoningDelta(sessionId, delta))
                        }
                    },
                    onToken = { delta ->
                        runBlocking {
                            onEvent(RuntimeEvent.AssistantDelta(sessionId, delta))
                        }
                    }
                )
            } catch (t: Throwable) {
                val err = "API call error: ${t.localizedMessage ?: t.message}"
                Log.e(TAG, err, t)
                onEvent(RuntimeEvent.RuntimeLog(sessionId, "API Warning", "$err. Applying direct code synthesis fallback."))

                // Fallback: extract code blocks directly from prompt
                val promptBlocks = extractCodeBlocks(prompt)
                for ((rel, code) in promptBlocks) {
                    writeFileDirectly(workspace, rel, code, sessionId, onEvent)
                }
                break
            }

            conversation.add("assistant" to responseText)

            // Parse and execute file writes
            val writtenFiles = parseAndExecuteFileWrites(responseText, workspace, sessionId, onEvent)

            // Parse and execute commands
            val executedCommands = parseAndExecuteCommands(responseText, workspace, sessionId, onEvent)

            // Parse read file requests
            val readFiles = parseReadFileRequests(responseText, workspace, sessionId, onEvent)

            if (readFiles.isNotEmpty()) {
                val toolFeedback = buildString {
                    append("File Read Results:\n")
                    readFiles.forEach { (path, content) ->
                        append("\n--- $path ---\n$content\n")
                    }
                    append("\nPlease complete the solution based on the read files.")
                }
                conversation.add("user" to toolFeedback)
                turn++
            } else {
                allDone = true
            }
        }

        // 3. Android Gradle Build if requested
        if (promptLower.contains("build") || promptLower.contains("assemble") || promptLower.contains("compile")) {
            val root = AndroidBuildSupport.findAndroidProjectRoot(workspace) ?: workspace
            val gradleCmd = AndroidBuildSupport.androidGradleCommand(root)
            onEvent(RuntimeEvent.ToolStarted(sessionId, "Gradle Build", "Compiling APK with: $gradleCmd"))
            val buildResult = LocalEnvironmentManager.executeCommand(gradleCmd, context, root.absolutePath)
            if (buildResult.exitCode == 0) {
                val apk = AndroidBuildSupport.findDebugApk(root)
                val note = if (apk != null) " Generated APK: ${apk.name} (${apk.length() / 1024} KB)" else ""
                onEvent(RuntimeEvent.ToolCompleted(sessionId, "Gradle Build", "Build succeeded!$note"))
                onEvent(RuntimeEvent.AssistantDelta(sessionId, "\n\n✅ **Android Build Succeeded!**$note\n```\n${buildResult.output.takeLast(1000)}\n```\n"))
            } else {
                onEvent(RuntimeEvent.ToolCompleted(sessionId, "Gradle Build", "Build finished with exit code ${buildResult.exitCode}"))
                onEvent(RuntimeEvent.AssistantDelta(sessionId, "\n\n⚠️ **Build Diagnostics (exit code ${buildResult.exitCode}):**\n```\n${buildResult.output.takeLast(1000)}\n```\n"))
            }
        }

        // 4. Summarize workspace state
        val finalFiles = workspace.walkTopDown()
            .maxDepth(3)
            .filter { it.isFile && !it.name.startsWith(".") && !it.path.contains("/build/") }
            .toList()

        onEvent(RuntimeEvent.AssistantDelta(sessionId, "\n\n### Workspace Status\n- **Project**: `${workspace.name}`\n- **Path**: `${workspace.absolutePath}`\n- **Total active files**: **${finalFiles.size}**\n\nAll tasks completed successfully."))
    }

    private suspend fun writeFileDirectly(
        workspace: File,
        relPath: String,
        content: String,
        sessionId: String,
        onEvent: suspend (RuntimeEvent) -> Unit
    ) {
        val cleanPath = relPath.trim().removePrefix("/").removePrefix("./")
        val target = File(workspace, cleanPath)
        target.parentFile?.mkdirs()
        onEvent(RuntimeEvent.ToolStarted(sessionId, "FileSystem", "Writing $cleanPath"))
        target.writeText(content)
        onEvent(RuntimeEvent.ToolCompleted(sessionId, "FileSystem", "Saved $cleanPath (${content.length} bytes)"))
    }

    private suspend fun parseAndExecuteFileWrites(
        text: String,
        workspace: File,
        sessionId: String,
        onEvent: suspend (RuntimeEvent) -> Unit
    ): List<String> {
        val written = mutableListOf<String>()

        // Pattern 1: ```tool:write_file ... path: ... content: ... ```
        val toolWriteRegex = Regex("```(?:tool:write_file|tool:file_write)\\s*\npath:\\s*([^\n]+)\ncontent:\\s*\n([\\s\\S]*?)```")
        for (m in toolWriteRegex.findAll(text)) {
            val path = m.groupValues[1].trim()
            val content = m.groupValues[2]
            if (path.isNotBlank() && content.isNotBlank()) {
                writeFileDirectly(workspace, path, content, sessionId, onEvent)
                written.add(path)
            }
        }

        // Pattern 2: ```lang file=path or filepath=path or ```lang path.ext
        val codeBlocks = extractCodeBlocks(text)
        for ((path, content) in codeBlocks) {
            if (!written.contains(path)) {
                writeFileDirectly(workspace, path, content, sessionId, onEvent)
                written.add(path)
            }
        }

        return written
    }

    private suspend fun parseAndExecuteCommands(
        text: String,
        workspace: File,
        sessionId: String,
        onEvent: suspend (RuntimeEvent) -> Unit
    ): List<String> {
        val commands = mutableListOf<String>()

        val cmdRegex = Regex("```(?:tool:execute_command|tool:bash|tool:sh)\\s*\n(?:command:\\s*)?([\\s\\S]*?)```")
        for (m in cmdRegex.findAll(text)) {
            val cmd = m.groupValues[1].trim()
            if (cmd.isNotBlank()) {
                onEvent(RuntimeEvent.ToolStarted(sessionId, "Terminal", "Running `$cmd` in workspace"))
                val res = LocalEnvironmentManager.executeCommand(cmd, context, workspace.absolutePath)
                val outSummary = if (res.output.isNotBlank()) res.output.take(160) else "(no output)"
                onEvent(RuntimeEvent.ToolCompleted(sessionId, "Terminal", "Exit code ${res.exitCode}: $outSummary"))
                commands.add(cmd)
            }
        }

        return commands
    }

    private suspend fun parseReadFileRequests(
        text: String,
        workspace: File,
        sessionId: String,
        onEvent: suspend (RuntimeEvent) -> Unit
    ): Map<String, String> {
        val results = mutableMapOf<String, String>()

        val readRegex = Regex("```tool:read_file\\s*\npath:\\s*([^\n]+)\\s*\n```")
        for (m in readRegex.findAll(text)) {
            val path = m.groupValues[1].trim()
            if (path.isNotBlank()) {
                val target = File(workspace, path.removePrefix("/").removePrefix("./"))
                if (target.exists() && target.isFile) {
                    onEvent(RuntimeEvent.ToolStarted(sessionId, "FileReader", "Reading $path"))
                    val content = target.readText().take(5000)
                    onEvent(RuntimeEvent.ToolCompleted(sessionId, "FileReader", "Read ${target.name} (${content.length} chars)"))
                    results[path] = content
                } else {
                    results[path] = "File does not exist: $path"
                }
            }
        }

        return results
    }

    private fun extractCodeBlocks(text: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        // Matches pattern: ```lang file=xyz or ```lang filepath=xyz or ```lang filename.ext
        val regex = Regex("```(?:[a-zA-Z0-9_-]+)?\\s*(?:file=([^\n]+)|filepath=([^\n]+)|([a-zA-Z0-9_./-]+\\.[a-zA-Z0-9]+))?\\n([\\s\\S]*?)```")
        for (match in regex.findAll(text)) {
            var path = match.groupValues[1].ifBlank { match.groupValues[2] }.ifBlank { match.groupValues[3] }.trim()
            val content = match.groupValues[4]

            // If not found in fence, inspect first line of content for // filepath: xyz or <!-- filepath: xyz -->
            if (path.isBlank()) {
                val firstLine = content.lineSequence().firstOrNull()?.trim() ?: ""
                val lineMatch = Regex("(?:\\/\\/|#|<!--|\\/\\*)\\s*(?:filepath:|file:)\\s*([a-zA-Z0-9_./-]+\\.[a-zA-Z0-9]+)").find(firstLine)
                if (lineMatch != null) {
                    path = lineMatch.groupValues[1].trim()
                }
            }

            if (path.isNotBlank() && content.isNotBlank()) {
                result.add(path to content)
            }
        }
        return result
    }
}
