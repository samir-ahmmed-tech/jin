package com.example.gemini.data.agent.direct

import android.app.Application
import android.util.Log
import com.example.gemini.data.agent.AgentChatBackend
import com.example.gemini.data.agent.ChatSessionStore
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.data.remote.DirectApiClient
import com.example.gemini.data.remote.AntigravityApiService
import com.example.gemini.data.remote.StreamEvent
import com.example.gemini.domain.model.AgentKind
import com.example.gemini.domain.model.AiModel
import com.example.gemini.domain.model.ChatMessage
import com.example.gemini.domain.model.Conversation
import com.example.gemini.domain.model.MessageRole
import com.example.gemini.domain.model.ModelFamily
import com.example.gemini.ui.components.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Direct API chat backend for JIN.
 * Powering DeepSeek, OpenAI Codex, and OpenRouter agents with direct API keys and streaming.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)
 */
class DirectAgentChatBackend(
    val application: Application,
    val backendScope: CoroutineScope,
    val store: ChatSessionStore,
    val authPrefs: AuthPreferences,
    val directApiClient: DirectApiClient,
    override val kind: AgentKind
) : AgentChatBackend {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val chatsDir = File(application.filesDir, "direct_agent_chats/${kind.name.lowercase()}").apply { mkdirs() }
    private val conversationsFile = File(chatsDir, "conversations.json")

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    override val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isLoadingConversation = MutableStateFlow(false)
    override val isLoadingConversation: StateFlow<Boolean> = _isLoadingConversation.asStateFlow()

    private val _conversationError = MutableStateFlow<String?>(null)
    override val conversationError: StateFlow<String?> = _conversationError.asStateFlow()

    val isCurrentChatActivelyRunning: StateFlow<Boolean> = _isStreaming.asStateFlow()

    val availableModels: StateFlow<List<AiModel>> = MutableStateFlow(defaultModelsForKind())
    private val _selectedModelId = MutableStateFlow(defaultModelIdForKind())
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val activeStreamJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    init {
        loadConversationsFromDisk()
    }

    private fun defaultModelIdForKind(): String = when (kind) {
        AgentKind.DEEPSEEK_LOCAL -> "deepseek-chat"
        AgentKind.CODEX -> "gpt-4o"
        AgentKind.OPENROUTER -> "meta-llama/llama-3.3-70b-instruct:free"
        AgentKind.OPENCODE -> "opencode-zen-deepseek"
        else -> ""
    }

    private fun defaultModelsForKind(): List<AiModel> = when (kind) {
        AgentKind.DEEPSEEK_LOCAL -> listOf(
            AiModel("deepseek-chat", "DeepSeek V3", ModelFamily.DEEPSEEK, false, "DeepSeek 671B MoE architecture with ultra-high coding capability", isDefault = true),
            AiModel("deepseek-reasoner", "DeepSeek R1 (Hermes)", ModelFamily.DEEPSEEK, true, "DeepSeek R1 Hermes reasoning model with deep chain-of-thought MoE"),
            AiModel("deepseek-coder", "DeepSeek Coder", ModelFamily.DEEPSEEK, false, "Specialized programming LLM supporting 300+ programming languages")
        )
        AgentKind.CODEX -> listOf(
            AiModel("gpt-4o", "ChatGPT 4o", ModelFamily.OPENAI, false, "Omni-modal reasoning and software architecture model", isDefault = true),
            AiModel("gpt-4o-mini", "ChatGPT 4o Mini", ModelFamily.OPENAI, false, "Lightweight ChatGPT model optimized for fast responses"),
            AiModel("o3-mini", "OpenAI o3-Mini", ModelFamily.OPENAI, true, "Advanced OpenAI reasoning model for math, coding, and architecture")
        )
        AgentKind.OPENROUTER -> listOf(
            AiModel("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (Free)", ModelFamily.OTHER, false, "Flagship open reasoning model via OpenRouter", isDefault = true),
            AiModel("mistralai/codestral-2501", "Mistral Codestral 25B", ModelFamily.OTHER, false, "Mistral AI cutting-edge coding model"),
            AiModel("qwen/qwen-2.5-coder-32b-instruct", "Qwen 2.5 Coder 32B", ModelFamily.OTHER, false, "Alibaba Cloud flagship code model for polyglot software engineering"),
            AiModel("custom-api", "Custom OpenAI-Compatible API", ModelFamily.OTHER, false, "Connect to any custom base URL (Ollama, LM Studio, vLLM, OpenRouter)")
        )
        AgentKind.OPENCODE -> listOf(
            AiModel("opencode-zen-deepseek", "OpenCode DeepSeek V3", ModelFamily.OTHER, false, "Direct high-speed coding gateway via OpenCode Zen", isDefault = true),
            AiModel("opencode-zen-coder", "OpenCode Zen Coder", ModelFamily.OTHER, false, "Optimized programming model for polyglot software engineering"),
            AiModel("opencode-zen-qwen", "OpenCode Qwen 2.5 Coder 32B", ModelFamily.OTHER, false, "Alibaba Cloud flagship code model via OpenCode Zen"),
            AiModel("opencode-zen-r1", "OpenCode DeepSeek R1 Hermes", ModelFamily.OTHER, true, "Deep reasoning and chain-of-thought MoE architecture")
        )
        else -> emptyList()
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
        val cur = _currentConversation.value
        if (cur != null && cur.agent == kind) {
            val updated = cur.copy(modelId = modelId)
            _currentConversation.value = updated
            if (store.currentConversation.value?.id == cur.id) {
                store.currentConversation.value = updated
            }
            saveConversationMeta(updated)
        }
    }

    fun detach() {
        _isStreaming.value = false
    }

    override fun startNewChat() {
        stopStreaming()
        val defaultProjPath = com.example.gemini.data.daemon.TermuxDaemonManager.activeProject.value?.path ?: ""
        val defaultWorkspaceUri = if (defaultProjPath.isNotBlank()) {
            if (defaultProjPath.startsWith("file://")) defaultProjPath else "file://$defaultProjPath"
        } else ""

        val newConv = Conversation(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            modelId = _selectedModelId.value,
            sessionId = UUID.randomUUID().toString(),
            workspaceUri = defaultWorkspaceUri,
            agent = kind
        )
        _currentConversation.value = newConv
        _messages.value = emptyList()
        store.currentConversation.value = newConv
        store.messages.value = emptyList()
        _conversationError.value = null
    }

    override fun selectConversation(id: String) {
        val target = _conversations.value.find { it.id == id } ?: return
        _currentConversation.value = target
        store.currentConversation.value = target
        _conversationError.value = null
        loadMessagesForConversation(id)
        _isStreaming.value = activeStreamJobs[id]?.isActive == true
    }

    override fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank() || _isStreaming.value) return

        var conv = _currentConversation.value
        if (conv == null || conv.agent != kind) {
            startNewChat()
            conv = _currentConversation.value ?: return
        }

        val rawModel = conv.modelId.ifBlank { _selectedModelId.value }.ifBlank { defaultModelIdForKind() }

        val (baseUrl, apiKey, effectiveModel) = when (kind) {
            AgentKind.DEEPSEEK_LOCAL -> {
                val key = authPrefs.getDeepSeekApiKeySync().trim()
                val m = when {
                    rawModel.contains("hermes", ignoreCase = true) || rawModel.contains("reasoner", ignoreCase = true) -> "deepseek-reasoner"
                    else -> "deepseek-chat"
                }
                Triple("https://api.deepseek.com", key, m)
            }
            AgentKind.CODEX -> {
                val key = authPrefs.getOpenAiApiKeySync().trim()
                val m = when {
                    rawModel.equals("openai-codex", ignoreCase = true) -> "gpt-4o"
                    else -> rawModel.ifBlank { "gpt-4o" }
                }
                Triple("https://api.openai.com/v1", key, m)
            }
            AgentKind.OPENROUTER -> {
                if (rawModel == "custom-api" || rawModel.startsWith("custom-")) {
                    val key = authPrefs.getCustomApiKeySync().trim()
                    val url = authPrefs.getCustomApiBaseUrlSync().trim().ifBlank { "https://api.openai.com/v1" }
                    val m = authPrefs.getCustomApiModelNameSync().trim().ifBlank { "gpt-4o" }
                    Triple(url, key, m)
                } else {
                    val key = authPrefs.getOpenRouterApiKeySync().trim()
                    val m = when (rawModel) {
                        "openrouter-qwen", "qwen" -> "qwen/qwen-2.5-coder-32b-instruct"
                        "openrouter-codestral", "codestral" -> "mistralai/codestral-2501"
                        "meta-llama/llama-3.3-70b-instruct" -> "meta-llama/llama-3.3-70b-instruct:free"
                        "meta-llama/llama-3.3-70b-instruct:free" -> "meta-llama/llama-3.3-70b-instruct:free"
                        "deepseek-r1", "r1" -> "deepseek/deepseek-r1:free"
                        else -> rawModel.ifBlank { "qwen/qwen-2.5-coder-32b-instruct" }
                    }
                    Triple("https://openrouter.ai/api/v1", key, m)
                }
            }
            AgentKind.OPENCODE -> {
                val key = authPrefs.getOpenCodeApiKeySync().trim()
                val m = when {
                    rawModel.contains("r1", ignoreCase = true) -> "deepseek-r1"
                    rawModel.contains("qwen", ignoreCase = true) -> "qwen2.5-coder-32b"
                    rawModel.contains("coder", ignoreCase = true) -> "opencode-zen-coder"
                    else -> "deepseek-v4-flash"
                }
                Triple("https://opencode.ai/zen/v1", key, m)
            }
            else -> Triple("", "", rawModel)
        }

        val userMsg = ChatMessage(
            conversationId = conv.id,
            role = MessageRole.USER,
            content = trimmed
        )

        // Generate conversation title from first user prompt
        val isFirstUserMsg = _messages.value.none { it.role == MessageRole.USER }
        if (conv.title == "New Chat" || isFirstUserMsg) {
            val autoTitle = trimmed.take(30).trimEnd() + if (trimmed.length > 30) "…" else ""
            conv = conv.copy(title = autoTitle, updatedAt = System.currentTimeMillis())
            _currentConversation.value = conv
            store.currentConversation.value = conv
            saveConversationMeta(conv)
        }

        val currentList = _messages.value + userMsg
        _messages.value = currentList
        store.messages.value = currentList
        saveMessagesToDisk(conv.id, currentList)

        val assistantMsgId = UUID.randomUUID().toString()
        val assistantMsg = ChatMessage(
            id = assistantMsgId,
            conversationId = conv.id,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            modelId = effectiveModel
        )

        val listWithAssistant = currentList + assistantMsg
        _messages.value = listWithAssistant
        store.messages.value = listWithAssistant
        _isStreaming.value = true

        activeStreamJobs[conv.id]?.cancel()
        val streamJob = backendScope.launch(Dispatchers.IO) {
            val responseBuffer = StringBuilder()
            val thoughtBuffer = StringBuilder()
            val history = currentList.filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
                .takeLast(20)
                .map { (if (it.role == MessageRole.USER) "user" else "assistant") to it.content }

            val googleToken = authPrefs.accessToken.firstOrNull()
            val useGoogleProSession = apiKey == "google_ai_session" && !googleToken.isNullOrBlank()

            try {
                if (!useGoogleProSession) {
                    directApiClient.streamChatMessages(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = effectiveModel,
                        messages = history,
                        systemPrompt = "You are JIN (${kind.label()}), an advanced agentic AI coding super-assistant created by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin). Assist the developer with clear, high-quality, production-ready code, explanation, and debugging.",
                        onReasoning = { rToken ->
                            thoughtBuffer.append(rToken)
                            val updatedThought = thoughtBuffer.toString()
                            if (_currentConversation.value?.id == conv.id) {
                                val liveList = _messages.value.map {
                                    if (it.id == assistantMsgId) it.copy(thoughtText = updatedThought) else it
                                }
                                _messages.value = liveList
                                store.messages.value = liveList
                            }
                        }
                    ) { token ->
                        responseBuffer.append(token)
                        val updatedText = responseBuffer.toString()
                        if (_currentConversation.value?.id == conv.id) {
                            val liveList = _messages.value.map {
                                if (it.id == assistantMsgId) it.copy(content = updatedText) else it
                            }
                            _messages.value = liveList
                            store.messages.value = liveList
                        }
                    }
                } else {
                    responseBuffer.append("> ⚡ **${kind.label()} Connected via Google AI Pro**\n\n")
                    val projectId = authPrefs.projectId.firstOrNull() ?: ""
                    AntigravityApiService.instance.streamGenerateContent(
                        token = googleToken ?: "",
                        projectId = projectId,
                        modelId = effectiveModel.ifBlank { "gemini-2.5-flash" },
                        sessionId = conv.sessionId,
                        messages = currentList
                    ).collect { event ->
                        if (event is StreamEvent.TextChunk) {
                            responseBuffer.append(event.text)
                            val updatedText = responseBuffer.toString()
                            if (_currentConversation.value?.id == conv.id) {
                                val liveList = _messages.value.map {
                                    if (it.id == assistantMsgId) it.copy(content = updatedText) else it
                                }
                                _messages.value = liveList
                                store.messages.value = liveList
                            }
                        }
                    }
                }

                val finalContent = responseBuffer.toString().ifBlank {
                    thoughtBuffer.toString().ifBlank { "Task completed." }
                }
                val finalList = (if (_currentConversation.value?.id == conv.id) _messages.value else listWithAssistant).map {
                    if (it.id == assistantMsgId) it.copy(content = finalContent, isStreaming = false) else it
                }
                if (_currentConversation.value?.id == conv.id) {
                    _messages.value = finalList
                    store.messages.value = finalList
                }
                saveMessagesToDisk(conv.id, finalList)
                saveConversationMeta(conv.copy(updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e("DirectAgent", "Error streaming response for ${kind.name}: ${e.message}", e)
                val errMsg = (e.message ?: "").lowercase()
                val isQuotaOrRateLimit = errMsg.contains("429") || errMsg.contains("quota") || errMsg.contains("rate limit") ||
                        errMsg.contains("insufficient") || errMsg.contains("credit") || errMsg.contains("exceeded")

                if (isQuotaOrRateLimit && authPrefs.getAutoFailoverEnabledSync()) {
                    val currentMId = _selectedModelId.value
                    val fallbackModel = availableModels.value.firstOrNull { it.id != currentMId }
                    if (fallbackModel != null) {
                        try {
                            _selectedModelId.value = fallbackModel.id
                            responseBuffer.clear()
                            responseBuffer.append("> ⚡ **Auto-Failover Activated**: Quota limit reached on `$currentMId`. Switched automatically to **${fallbackModel.displayName}**.\n\n")

                            directApiClient.streamChatMessages(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = fallbackModel.id,
                                messages = history,
                                systemPrompt = "You are JIN, an advanced agentic AI coding super-assistant created by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin). Assist the developer with clear, high-quality, production-ready code."
                            ) { token: String ->
                                responseBuffer.append(token)
                                val updatedText = responseBuffer.toString()
                                if (_currentConversation.value?.id == conv.id) {
                                    val liveList = _messages.value.map {
                                        if (it.id == assistantMsgId) it.copy(content = updatedText) else it
                                    }
                                    _messages.value = liveList
                                    store.messages.value = liveList
                                }
                            }

                            val finalList = (if (_currentConversation.value?.id == conv.id) _messages.value else currentList + assistantMsg).map {
                                if (it.id == assistantMsgId) it.copy(content = responseBuffer.toString(), isStreaming = false) else it
                            }
                            if (_currentConversation.value?.id == conv.id) {
                                _messages.value = finalList
                                store.messages.value = finalList
                            }
                            saveMessagesToDisk(conv.id, finalList)
                            saveConversationMeta(conv.copy(updatedAt = System.currentTimeMillis()))
                            return@launch
                        } catch (fallbackEx: Exception) {
                            Log.e("DirectAgent", "Auto-failover fallback also failed: ${fallbackEx.message}")
                        }
                    }
                }

                val errText = responseBuffer.toString().ifBlank {
                    "❌ **Connection Error**: ${e.localizedMessage ?: e.message ?: "Failed to receive response from provider."}"
                }
                val errorList = (if (_currentConversation.value?.id == conv.id) _messages.value else currentList + assistantMsg).map {
                    if (it.id == assistantMsgId) it.copy(content = errText, isStreaming = false) else it
                }
                if (_currentConversation.value?.id == conv.id) {
                    _messages.value = errorList
                    store.messages.value = errorList
                }
                saveMessagesToDisk(conv.id, errorList)
            } finally {
                if (_currentConversation.value?.id == conv.id) {
                    _isStreaming.value = false
                }
            }
        }
        activeStreamJobs[conv.id] = streamJob
        streamJob.invokeOnCompletion {
            activeStreamJobs.remove(conv.id)
            if (_currentConversation.value?.id == conv.id) {
                _isStreaming.value = false
            }
        }
    }

    override fun stopStreaming() {
        val curId = _currentConversation.value?.id
        if (curId != null) {
            activeStreamJobs[curId]?.cancel()
            activeStreamJobs.remove(curId)
        }
        _isStreaming.value = false
        val currentList = _messages.value.map {
            if (it.isStreaming) it.copy(isStreaming = false) else it
        }
        _messages.value = currentList
        store.messages.value = currentList
    }

    override fun deleteConversation(id: String) {
        val file = File(chatsDir, "msg_$id.json")
        if (file.exists()) file.delete()
        val updated = _conversations.value.filter { it.id != id }
        _conversations.value = updated
        persistConversationsList(updated)

        if (_currentConversation.value?.id == id) {
            startNewChat()
        }
    }

    override fun forkConversation(id: String) {
        val original = _conversations.value.find { it.id == id } ?: return
        val forked = original.copy(
            id = UUID.randomUUID().toString(),
            title = "${original.title} (Fork)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val origMsgs = loadMessagesFromFile(id)
        val forkedMsgs = origMsgs.map { it.copy(id = UUID.randomUUID().toString(), conversationId = forked.id) }
        saveMessagesToDisk(forked.id, forkedMsgs)
        saveConversationMeta(forked)
        selectConversation(forked.id)
    }

    override fun retryConnections() {
        // Direct API is stateless per request
    }

    override fun onAppForegrounded() {
        loadConversationsFromDisk()
    }

    fun renameConversation(id: String, title: String) {
        val cur = _conversations.value.find { it.id == id } ?: return
        val updated = cur.copy(title = title, updatedAt = System.currentTimeMillis())
        saveConversationMeta(updated)
        if (_currentConversation.value?.id == id) {
            _currentConversation.value = updated
            store.currentConversation.value = updated
        }
    }

    fun regenerate(messageId: String) {
        val msgs = _messages.value
        val idx = msgs.indexOfFirst { it.id == messageId }
        if (idx == -1) return
        val userPrompt = if (msgs[idx].role == MessageRole.USER) {
            msgs[idx].content
        } else {
            msgs.take(idx).lastOrNull { it.role == MessageRole.USER }?.content ?: return
        }
        // Slice up to before target assistant reply
        val targetIdx = if (msgs[idx].role == MessageRole.USER) idx else idx - 1
        val sliced = msgs.take(targetIdx)
        _messages.value = sliced
        store.messages.value = sliced
        sendMessage(userPrompt)
    }

    private fun loadConversationsFromDisk() {
        backendScope.launch(Dispatchers.IO) {
            try {
                if (!conversationsFile.exists()) {
                    _conversations.value = emptyList()
                    return@launch
                }
                val content = conversationsFile.readText()
                if (content.isBlank()) return@launch
                val list = json.decodeFromString<List<Conversation>>(content)
                _conversations.value = list.filter { it.agent == kind }.sortedByDescending { it.updatedAt }
            } catch (e: Exception) {
                Log.e("DirectAgent", "Failed to load conversations for ${kind.name}", e)
            }
        }
    }

    private fun saveConversationMeta(conv: Conversation) {
        val current = _conversations.value.toMutableList()
        val idx = current.indexOfFirst { it.id == conv.id }
        if (idx >= 0) {
            current[idx] = conv
        } else {
            current.add(0, conv)
        }
        val sorted = current.sortedByDescending { it.updatedAt }
        _conversations.value = sorted
        persistConversationsList(sorted)
    }

    private fun persistConversationsList(list: List<Conversation>) {
        backendScope.launch(Dispatchers.IO) {
            try {
                val str = json.encodeToString(list)
                conversationsFile.writeText(str)
            } catch (e: Exception) {
                Log.e("DirectAgent", "Failed to save conversations for ${kind.name}", e)
            }
        }
    }

    private fun loadMessagesForConversation(convId: String) {
        _isLoadingConversation.value = true
        backendScope.launch(Dispatchers.IO) {
            val list = loadMessagesFromFile(convId)
            withContext(Dispatchers.Main) {
                _messages.value = list
                store.messages.value = list
                _isLoadingConversation.value = false
            }
        }
    }

    private fun loadMessagesFromFile(convId: String): List<ChatMessage> {
        val file = File(chatsDir, "msg_$convId.json")
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            if (text.isBlank()) emptyList() else json.decodeFromString(text)
        } catch (e: Exception) {
            Log.e("DirectAgent", "Failed to read messages for $convId", e)
            emptyList()
        }
    }

    private fun saveMessagesToDisk(convId: String, list: List<ChatMessage>) {
        backendScope.launch(Dispatchers.IO) {
            try {
                val file = File(chatsDir, "msg_$convId.json")
                file.writeText(json.encodeToString(list))
            } catch (e: Exception) {
                Log.e("DirectAgent", "Failed to write messages for $convId", e)
            }
        }
    }
}
