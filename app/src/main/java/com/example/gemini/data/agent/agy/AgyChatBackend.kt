package com.example.gemini.data.agent.agy

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.data.remote.AgyHubClient
import com.example.gemini.data.remote.AntigravityApiService
import com.example.gemini.data.remote.GoogleOAuthManager
import com.example.gemini.data.remote.StreamEvent
import com.example.gemini.domain.model.AiModel
import com.example.gemini.domain.model.ChatMessage
import com.example.gemini.domain.model.ChatAttachment
import com.example.gemini.domain.model.Conversation
import com.example.gemini.domain.model.MessageRole
import com.example.gemini.domain.model.ModelQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import android.util.Log
import org.json.JSONObject
import java.util.UUID
import com.example.gemini.data.agent.AgentChatBackend
import com.example.gemini.domain.model.AgentKind
import com.example.gemini.data.agent.ChatSessionStore
import com.example.gemini.ui.chat.PendingToolApproval
import kotlinx.coroutines.CoroutineScope

/**
 * Antigravity (agy --hub) chat backend: conversation sync, live trajectory stream, prompts, approvals,
 * auth, models and quotas. Moved out of ChatViewModel unchanged; it writes the on-screen chat state into [store].
 */
class AgyChatBackend(
    private val application: Application,
    private val backendScope: CoroutineScope,
    private val authPrefs: AuthPreferences,
    private val store: ChatSessionStore,
    private val isNetworkConnectedProvider: () -> Boolean,
    private val onHubOnline: () -> Unit
) : AgentChatBackend {

    override val kind = AgentKind.AGY

    private val _conversations = store.conversations
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversation = store.currentConversation
    override val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _messages = store.messages
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _conversationError = store.conversationError
    override val conversationError: StateFlow<String?> = _conversationError.asStateFlow()

    private val _userEmail = store.userEmail
    private val _attachments = store.attachments
    private val _terminatedToolDialog = store.terminatedToolDialog

    private fun isNetworkConnected(): Boolean = isNetworkConnectedProvider()

    val agyBridgeService = com.example.gemini.data.remote.AgyBridgeService.instance
    val systemConnectionState: StateFlow<com.example.gemini.data.remote.SystemConnectionState> = agyBridgeService.systemConnectionState
    val hubStatus: StateFlow<com.example.gemini.data.remote.AgyHubStatus> = agyBridgeService.hubStatus
    val agyHubClient = com.example.gemini.data.remote.AgyHubClient()

    private val trajectoryEngines = java.util.concurrent.ConcurrentHashMap<String, com.example.gemini.domain.chat.TrajectoryEngine>()
    private val fallbackTrajectoryEngine = com.example.gemini.domain.chat.TrajectoryEngine()

    fun getTrajectoryEngine(convId: String): com.example.gemini.domain.chat.TrajectoryEngine {
        if (convId.isBlank()) return fallbackTrajectoryEngine
        return trajectoryEngines.computeIfAbsent(convId) { com.example.gemini.domain.chat.TrajectoryEngine() }
    }

    val trajectoryEngine: com.example.gemini.domain.chat.TrajectoryEngine
        get() = _currentConversation.value?.id?.let { getTrajectoryEngine(it) } ?: fallbackTrajectoryEngine

    val speechManager = com.example.gemini.data.audio.AgyAudioTranscriptionManager(agyHubClient) {
        AuthPreferences.currentHubUrl
    }

    private val _hasReceivedInitialSync = MutableStateFlow(false)
    val hasReceivedInitialSync: StateFlow<Boolean> = _hasReceivedInitialSync.asStateFlow()

    private val _isConversationsLoading = MutableStateFlow(true)
    val isConversationsLoading: StateFlow<Boolean> = _isConversationsLoading.asStateFlow()

    private val _artifacts = MutableStateFlow<List<com.example.gemini.domain.model.ArtifactSnapshot>>(emptyList())
    val artifacts: StateFlow<List<com.example.gemini.domain.model.ArtifactSnapshot>> = _artifacts.asStateFlow()

    val pendingApprovals: StateFlow<List<PendingToolApproval>> = _messages.map { msgs ->
        msgs.filter { it.role == com.example.gemini.domain.model.MessageRole.ASSISTANT }
            .flatMap { msg ->
                msg.toolCalls.filter { it.status == "PENDING_APPROVAL" && it.toolType != com.example.gemini.domain.model.ToolType.ASK_CHOICE }
                    .distinctBy { it.stepIndex }
                    .map { PendingToolApproval(it, msg.id) }
            }
    }.stateIn(backendScope, SharingStarted.Eagerly, emptyList())

    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _availableModels = MutableStateFlow<List<AiModel>>(AiModel.DEFAULT_MODELS)
    val availableModels: StateFlow<List<AiModel>> = _availableModels.asStateFlow()

    private val _enabledModelIds = MutableStateFlow<Set<String>?>(null)
    val enabledModelIds: StateFlow<Set<String>?> = _enabledModelIds.asStateFlow()

    private val _enabledModels = MutableStateFlow<List<AiModel>>(AiModel.DEFAULT_MODELS)
    val enabledModels: StateFlow<List<AiModel>> = _enabledModels.asStateFlow()

    private val _isRefreshingModels = MutableStateFlow(false)
    val isRefreshingModels: StateFlow<Boolean> = _isRefreshingModels.asStateFlow()

    private val _selectedModelId = MutableStateFlow("")
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    val preferredModelName: StateFlow<String> = authPrefs.preferredModelName
        .map { it ?: "" }
        .stateIn(backendScope, SharingStarted.Eagerly, "")

    private val _quotas = MutableStateFlow<List<ModelQuota>>(emptyList())
    val quotas: StateFlow<List<ModelQuota>> = _quotas.asStateFlow()

    private val _quotaSummary = MutableStateFlow<com.example.gemini.domain.model.QuotaSummaryResponse?>(null)
    val quotaSummary: StateFlow<com.example.gemini.domain.model.QuotaSummaryResponse?> = _quotaSummary.asStateFlow()

    private val _isLoadingConversation = MutableStateFlow(true)
    override val isLoadingConversation: StateFlow<Boolean> = _isLoadingConversation.asStateFlow()

    fun setModelEnabled(modelId: String, isEnabled: Boolean) {
        val currentIds = _enabledModelIds.value?.toMutableSet() ?: _availableModels.value.map { it.id }.toMutableSet()
        if (isEnabled) {
            currentIds.add(modelId)
        } else {
            if (currentIds.size > 1) { // Don't allow disabling all models
                currentIds.remove(modelId)
            }
        }
        backendScope.launch {
            authPrefs.saveEnabledModelIds(currentIds)
        }
    }

    fun enableAllModels() {
        val allIds = _availableModels.value.map { it.id }.toSet()
        backendScope.launch {
            authPrefs.saveEnabledModelIds(allIds)
        }
    }

    private fun recomputeEnabledModels() {
        val all = _availableModels.value
        val enabledSet = _enabledModelIds.value
        val filtered = if (enabledSet.isNullOrEmpty()) {
            all
        } else {
            all.filter { it.id in enabledSet || it.key in enabledSet }
        }
        _enabledModels.value = if (filtered.isNotEmpty()) filtered else all

        // Only switch if user has an explicit filter AND current selection is not in the filtered enabled list
        val currentSelected = _selectedModelId.value
        if (!enabledSet.isNullOrEmpty() && currentSelected.isNotBlank()) {
            val isCurrentEnabled = _enabledModels.value.any { it.id == currentSelected || it.key == currentSelected }
            if (!isCurrentEnabled) {
                _enabledModels.value.firstOrNull()?.let {
                    selectModel(it.id)
                }
            }
        }
    }

    private val _bridgeStatusMessage = MutableStateFlow<String?>(null)
    val bridgeStatusMessage: StateFlow<String?> = _bridgeStatusMessage.asStateFlow()

    private val knownDaemonCascadeIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile
    private var isPromptInFlight = false
    @Volatile
    private var hasSeenTurnActivity = false
    @Volatile
    private var hasStartedRunning = false

    private val _isServerOnline = MutableStateFlow<Boolean?>(null)
    val isServerOnline: StateFlow<Boolean?> = _isServerOnline.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val _isBridgeOnline = MutableStateFlow<Boolean?>(null)
    val isBridgeOnline: StateFlow<Boolean?> = _isBridgeOnline.asStateFlow()

    fun checkBridgeHealth() {
        backendScope.launch(Dispatchers.IO) {
            val bridgeUrl = authPrefs.agyBridgeHttpUrl.firstOrNull() ?: AuthPreferences.currentBridgeHttpUrl
            val reachable = com.example.gemini.data.remote.AgyBridgeService.instance.checkServerHealth(bridgeUrl)
            _isBridgeOnline.value = reachable
        }
    }

    private val _agyAuthInfo = MutableStateFlow(com.example.gemini.data.remote.AgyHubClient.AgyAuthInfo(status = com.example.gemini.data.remote.AgyHubClient.AgyAuthStatus.CHECKING))
    val agyAuthInfo: StateFlow<com.example.gemini.data.remote.AgyHubClient.AgyAuthInfo> = _agyAuthInfo.asStateFlow()

    private val _isAuthBusy = MutableStateFlow(false)
    val isAuthBusy: StateFlow<Boolean> = _isAuthBusy.asStateFlow()

    private val _authFeedbackMessage = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val authFeedbackMessage: SharedFlow<String> = _authFeedbackMessage.asSharedFlow()

    fun emitFeedback(message: String) {
        _authFeedbackMessage.tryEmit(message)
    }

    private val _pendingLoginUrl = MutableStateFlow<String?>(null)
    val pendingLoginUrl: StateFlow<String?> = _pendingLoginUrl.asStateFlow()

    fun clearPendingLoginUrl() {
        _pendingLoginUrl.value = null
    }

    private var loginPollJob: Job? = null
    private var lastAuthCheckTimeMs = 0L

    fun onDrawerOpened() {
        val isLoggedIn = _agyAuthInfo.value.isLoggedIn
        val now = System.currentTimeMillis()
        if (!isLoggedIn) {
            // Not authenticated: check every single time user opens sidebar
            checkAgyAuthStatus(userInitiated = false)
        } else {
            // Authenticated: only check after 2 minutes (120,000ms) have passed
            if (now - lastAuthCheckTimeMs >= 120_000L) {
                checkAgyAuthStatus(userInitiated = false)
            }
        }
    }

    fun cancelAgyLogin() {
        loginPollJob?.cancel()
        loginPollJob = null
        _isAuthBusy.value = false
        _pendingLoginUrl.value = null
        checkAgyAuthStatus(userInitiated = false)
    }

    fun checkAgyAuthStatus(userInitiated: Boolean = false) {
        backendScope.launch {
            if (userInitiated) {
                agyBridgeService.updateAuthChecking(true)
            }
            try {
                if (!isNetworkConnected()) {
                    android.util.Log.d("ChatViewModel", "Skipping auth status check: device is offline.")
                    _agyAuthInfo.value = _agyAuthInfo.value.copy(
                        status = com.example.gemini.data.remote.AgyHubClient.AgyAuthStatus.OFFLINE,
                        isOffline = true
                    )
                    _isAuthBusy.value = false
                    if (userInitiated) {
                        _authFeedbackMessage.tryEmit("Cannot check status: device is offline.")
                    }
                    return@launch
                }
                val hubUrl = AuthPreferences.currentHubUrl
                var res = kotlinx.coroutines.withTimeoutOrNull(3500L) {
                    agyHubClient.fetchDetailedAuthInfo(hubUrl)
                } ?: Result.failure(Exception("Auth check timed out"))

                // If failed on non-manual check during startup/connection phase, retry once shortly
                if (res.isFailure && !userInitiated && isNetworkConnected()) {
                    delay(600)
                    res = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                        agyHubClient.fetchDetailedAuthInfo(hubUrl)
                    } ?: res
                }

                if (res.isSuccess) {
                    val info = res.getOrThrow()
                    _agyAuthInfo.value = info
                    agyBridgeService.updateAuthState(isAuth = info.isLoggedIn, isChecking = false)
                    if (info.isLoggedIn) {
                        lastAuthCheckTimeMs = System.currentTimeMillis()
                        _isAuthBusy.value = false
                        _pendingLoginUrl.value = null
                        loginPollJob?.cancel()
                        if (userInitiated) {
                            _authFeedbackMessage.tryEmit("Signed in as ${info.displayName.ifBlank { info.email }}")
                        }
                        refreshQuotas(force = false)
                    } else if (userInitiated) {
                        _authFeedbackMessage.tryEmit("Not signed in.")
                    }
                } else {
                    _agyAuthInfo.value = _agyAuthInfo.value.copy(
                        status = com.example.gemini.data.remote.AgyHubClient.AgyAuthStatus.OFFLINE,
                        isOffline = true
                    )
                    if (userInitiated) {
                        _authFeedbackMessage.tryEmit("Unable to reach server.")
                    }
                }
            } finally {
                agyBridgeService.updateAuthChecking(false)
            }
        }
    }

    fun loginToAgyHub(force: Boolean = false) {
        if (_isAuthBusy.value && !force) return
        if (!isNetworkConnected()) {
            _authFeedbackMessage.tryEmit("Cannot sign in: no internet connection.")
            return
        }
        _isAuthBusy.value = true
        agyBridgeService.updateAuthChecking(true)
        loginPollJob?.cancel()

        backendScope.launch {
            val hubUrl = AuthPreferences.currentHubUrl

            // Pre-check: if user is already authenticated, finish immediately!
            val preCheck = agyHubClient.fetchDetailedAuthInfo(hubUrl)
            if (preCheck.isSuccess && preCheck.getOrThrow().isLoggedIn) {
                val authed = preCheck.getOrThrow()
                _agyAuthInfo.value = authed
                agyBridgeService.updateAuthState(isAuth = true, isChecking = false)
                lastAuthCheckTimeMs = System.currentTimeMillis()
                _isAuthBusy.value = false
                _authFeedbackMessage.tryEmit("Already signed in as ${authed.displayName}!")
                refreshQuotas(force = true)
                return@launch
            }

            _authFeedbackMessage.tryEmit("Initiating sign-in with Antigravity...")

            // 1. Poll for successful auth completion concurrently every 800ms
            loginPollJob = launch {
                val startTime = System.currentTimeMillis()
                while (isActive && System.currentTimeMillis() - startTime < 120_000) {
                    delay(800)

                    if (isNetworkConnected()) {
                        val res = agyHubClient.fetchDetailedAuthInfo(hubUrl)
                        if (res.isSuccess && res.getOrThrow().isLoggedIn) {
                            val authed = res.getOrThrow()
                            _agyAuthInfo.value = authed
                            agyBridgeService.updateAuthState(isAuth = true, isChecking = false)
                            lastAuthCheckTimeMs = System.currentTimeMillis()
                            _isAuthBusy.value = false
                            _pendingLoginUrl.value = null
                            _authFeedbackMessage.tryEmit("Signed in successfully!")
                            refreshQuotas(force = true)
                            break
                        }
                    }
                }
                _isAuthBusy.value = false
                agyBridgeService.updateAuthChecking(false)
            }

            // 2. Kick off login RPC on daemon (Login RPC opens browser / triggers auth flow)
            launch {
                try {
                    agyHubClient.login(hubUrl)
                } catch (e: Exception) {
                    android.util.Log.d("ChatViewModel", "Hub login RPC finished/interrupted: ${e.message}")
                }
            }
        }
    }

    fun logoutFromAgyHub() {
        if (_isAuthBusy.value) return
        _isAuthBusy.value = true
        loginPollJob?.cancel()

        backendScope.launch {
            val hubUrl = AuthPreferences.currentHubUrl
            try {
                agyHubClient.authLogout(hubUrl)
                _agyAuthInfo.value = com.example.gemini.data.remote.AgyHubClient.AgyAuthInfo(
                    status = com.example.gemini.data.remote.AgyHubClient.AgyAuthStatus.UNAUTHENTICATED,
                    isLoggedIn = false
                )
                agyBridgeService.updateAuthState(isAuth = false, isChecking = false)
                _authFeedbackMessage.tryEmit("Logged out successfully.")
                refreshQuotas(force = true)
            } catch (e: Exception) {
                _authFeedbackMessage.tryEmit("Logout error: ${e.message}")
            } finally {
                _isAuthBusy.value = false
            }
        }
    }

    private val _activeInstances = MutableStateFlow<List<com.example.gemini.data.remote.AgyActiveInstance>>(emptyList())
    val activeInstances: StateFlow<List<com.example.gemini.data.remote.AgyActiveInstance>> = _activeInstances.asStateFlow()

    val isAnyGenerationOrTaskActive: StateFlow<Boolean> = combine(
        _activeInstances,
        _conversations
    ) { instances, convs ->
        instances.isNotEmpty() || convs.any { it.isRunning || it.notFullyIdle || it.hasActivity }
    }.stateIn(backendScope, SharingStarted.WhileSubscribed(5000), false)

    // Authoritative conversation running & background states derived from the sidebar summary stream (_conversations).
    // Note: We use the daemon's sidebar summaries (JetboxSubscribeToSummaries) as the single source of truth rather than
    // per-frame stream updates from StreamAgentStateUpdates because the fine-grained gRPC stream in AGY can be buggier
    // (e.g. continuing to report RUNNING state even when the turn has ended, particularly in long chats with multiple subagents/tools).
    val isCurrentChatActivelyRunning: StateFlow<Boolean> = combine(
        _conversations,
        _currentConversation,
        systemConnectionState
    ) { convList, current, conn ->
        val curId = current?.id ?: return@combine false
        val conv = convList.find { it.id == curId } ?: current
        (conv.isRunning) && conn.isHubOnline
    }.distinctUntilChanged()
    .stateIn(backendScope, SharingStarted.Eagerly, false)

    val isCurrentChatBackgroundActive: StateFlow<Boolean> = combine(
        _conversations,
        _currentConversation,
        systemConnectionState,
        _activeInstances
    ) { convList, current, conn, instances ->
        val curId = current?.id ?: return@combine false
        val conv = convList.find { it.id == curId } ?: current
        val isActivelyRunning = (conv.isRunning) && conn.isHubOnline
        val activeInst = instances.find { it.conversationId == curId }
        conn.isHubOnline && !isActivelyRunning && (conv.notFullyIdle || conv.hasActivity || activeInst != null)
    }.distinctUntilChanged()
    .stateIn(backendScope, SharingStarted.Eagerly, false)

    val connectionState: StateFlow<com.example.gemini.data.remote.BridgeConnectionState> = combine(_isServerOnline, isCurrentChatActivelyRunning) { online, streaming ->
        when {
            online == false -> com.example.gemini.data.remote.BridgeConnectionState.OFFLINE_ERROR
            streaming -> com.example.gemini.data.remote.BridgeConnectionState.STREAMING
            online == true -> com.example.gemini.data.remote.BridgeConnectionState.CONNECTED_READY
            else -> com.example.gemini.data.remote.BridgeConnectionState.CONNECTING
        }
    }.stateIn(backendScope, SharingStarted.Eagerly, com.example.gemini.data.remote.BridgeConnectionState.CONNECTING)

    private var currentConvPage = 1
    private var hasMoreConversations = false

    private var lastPrewarmedConvId: String? = null
    private var lastPrewarmedModelId: String? = null
    private var lastPrewarmedProjectPath: String? = null

    private var streamingJob: Job? = null
    private val activeStreamJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    val persistentStreamJob: Job?
        get() = activeStreamConversationId?.let { activeStreamJobs[it] } ?: activeStreamJobs.values.firstOrNull()
    private val conversationMessagesCache = java.util.concurrent.ConcurrentHashMap<String, List<ChatMessage>>()
    @Volatile private var activeStreamConversationId: String? = null
    @Volatile private var currentTurnStartStep: Int = 0
    @Volatile private var totalStepsCount: Int = 0
    @Volatile private var currentAssistantMsgId: String? = null
    @Volatile private var currentTrajectoryId: String = ""

    /** Starts hub monitoring, conversation sync and auth checks (formerly ChatViewModel.init). */
    fun start() {
        AntigravityOptimizer.init(application)
        AntigravityOptimizer.onScheduledPromptTriggered = { scheduledPrompt ->
            sendMessage(scheduledPrompt)
        }

        // 1. Hub status live monitoring via WebSocket
        backendScope.launch {
            while (currentCoroutineContext().isActive) {
                try {
                    val wsUrl = AuthPreferences.currentBridgeWsUrl
                    agyBridgeService.monitorHubStatus(wsUrl).collect { status ->
                        when (status.status) {
                            "online" -> {
                                val wasOnline = (_isServerOnline.value == true)
                                _isServerOnline.value = true
                                _isBridgeOnline.value = true
                                _conversationError.value = null
                                if (!wasOnline || syncJob?.isActive != true) {
                                    syncAgyConversations(force = !wasOnline)
                                    val curConv = _currentConversation.value
                                    val curId = curConv?.id
                                    if (!curId.isNullOrBlank() && curId != "new" && curConv.title != "New Chat") {
                                        _conversationError.value = null
                                        _isLoadingConversation.value = true
                                        startPersistentStream(curId)
                                    }
                                    checkAgyAuthStatus(userInitiated = false)
                                    refreshQuotas()
                                    onHubOnline()
                                }
                            }
                            "starting" -> {
                                _isBridgeOnline.value = true
                                _conversationError.value = null
                                _isConversationsLoading.value = true
                            }
                            "error" -> {
                                _isServerOnline.value = false
                                _isConversationsLoading.value = false
                                _conversationError.value = "Antigravity Hub failed to start: ${status.error ?: "Check server logs"}"
                            }
                            "stopped" -> {
                                _isServerOnline.value = false
                                _isBridgeOnline.value = true
                                if (_conversations.value.any { it.isRunning }) {
                                    _conversations.value = _conversations.value.map { if (it.isRunning) it.copy(isRunning = false) else it }
                                }
                                if (_currentConversation.value?.isRunning == true) {
                                    _currentConversation.value = _currentConversation.value?.copy(isRunning = false)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    _isServerOnline.value = false
                    _isBridgeOnline.value = false
                    agyBridgeService.resetState()
                    delay(1000)
                }
            }
        }

        backendScope.launch {
            agyBridgeService.loginUrlEvents.collect { url ->
                if (url.isNotBlank()) {
                    _pendingLoginUrl.value = url
                }
            }
        }

        backendScope.launch {
            val savedId = authPrefs.preferredModelId.firstOrNull()
            val savedKey = authPrefs.preferredModelKey.firstOrNull()
            val initial = savedId ?: savedKey ?: ""
            if (initial.isNotBlank() && _selectedModelId.value.isBlank()) {
                _selectedModelId.value = initial
            }
            // the start-up chat: only when nothing is open yet or it is still an empty Antigravity chat (a Claude
            // chat opened meanwhile must not be replaced)
            val cur = _currentConversation.value
            if (cur == null || (cur.agent == AgentKind.AGY && cur.title == "New Chat" && _messages.value.isEmpty())) {
                startNewChat()
            }
        }

        backendScope.launch {
            authPrefs.enabledModelIds.collect { ids ->
                _enabledModelIds.value = ids
                recomputeEnabledModels()
            }
        }

        // Keep active conversation title and summary live-synced when conversation list updates
        backendScope.launch {
            _conversations.collect { convList ->
                val activeId = _currentConversation.value?.id ?: return@collect
                val activeInList = convList.find { it.id == activeId } ?: return@collect
                val curr = _currentConversation.value ?: return@collect
                val newTitle = if (activeInList.title.isNotBlank() &&
                    activeInList.title != "Conversation" &&
                    activeInList.title != "New Chat") {
                    activeInList.title
                } else {
                    curr.title
                }
                if (newTitle != curr.title) {
                    _currentConversation.value = curr.copy(
                        title = newTitle,
                        summary = activeInList.summary ?: curr.summary
                    )
                }
            }
        }

        // Periodic auto-reconnect monitor for Hub RPC streams & Bridge (every 20 seconds)
        backendScope.launch {
            delay(20_000)
            while (currentCoroutineContext().isActive) {
                checkBridgeHealth()
                if (agyBridgeService.hubStatus.value.status != "starting") {
                    if (_isServerOnline.value == true && syncJob?.isActive != true) {
                        android.util.Log.d("ChatViewModel", "Periodic check: reconnecting hub streams...")
                        syncAgyConversations(force = false)
                    }
                }
                delay(20_000)
            }
        }

        // Initial auth status check
        checkAgyAuthStatus(userInitiated = false)
    }

    fun refreshActiveInstances() {
        backendScope.launch {
            try {
                val httpUrl = AuthPreferences.currentBridgeHttpUrl
                val res = agyBridgeService.fetchActiveInstances(httpUrl)
                if (res.isSuccess) {
                    _activeInstances.value = res.getOrThrow()
                }
            } catch (e: Exception) {
                android.util.Log.w("GeminiApp", "refreshActiveInstances failed: ${e.message}")
            }
        }
    }

    fun terminateInstance(conversationId: String) {
        backendScope.launch {
            val httpUrl = AuthPreferences.currentBridgeHttpUrl
            val ok = agyBridgeService.terminateInstance(conversationId, httpUrl)
            if (ok) {
                _activeInstances.value = _activeInstances.value.filter { it.conversationId != conversationId }
            }
        }
    }

    private var syncJob: Job? = null

    fun syncAgyConversations(force: Boolean = false) {
        if (!force && syncJob?.isActive == true) return
        syncJob?.cancel()
        syncJob = backendScope.launch {
            _isConversationsLoading.value = true
            while (currentCoroutineContext().isActive) {
                try {
                    val hubUrl = AuthPreferences.currentHubUrl
                    agyHubClient.subscribeToSummaries(hubUrl).collect { update ->
                        val currentMap = _conversations.value.associateBy { it.id }.toMutableMap()
                        val activeId = _currentConversation.value?.id

                        // Remove empty, deleted, or abandoned sessions
                        for (delId in update.removedIds) {
                            if (delId != activeId || _messages.value.isEmpty()) {
                                currentMap.remove(delId)
                                knownDaemonCascadeIds.remove(delId)
                            }
                        }

                        // Add or update valid conversations
                        for (conv in update.updated) {
                            currentMap[conv.id] = conv
                            knownDaemonCascadeIds.add(conv.id)
                        }

                        val sortedConvs = currentMap.values.sortedByDescending { it.updatedAt }
                        _conversations.value = sortedConvs
                        com.example.gemini.data.daemon.TermuxDaemonManager.loadProjects(sortedConvs)

                        // Automatically synchronize active conversation title and metadata from daemon
                        if (activeId != null) {
                            val activeInMap = currentMap[activeId]
                            val curr = _currentConversation.value
                            if (activeInMap != null && curr != null && curr.id == activeId) {
                                val newTitle = if (activeInMap.title.isNotBlank() &&
                                    activeInMap.title != "Conversation" &&
                                    activeInMap.title != "New Chat") {
                                    activeInMap.title
                                } else {
                                    curr.title
                                }
                                val newWorkspaceUri = curr.workspaceUri.ifBlank { activeInMap.workspaceUri }
                                if (newTitle != curr.title ||
                                    newWorkspaceUri != curr.workspaceUri ||
                                    curr.isRunning != activeInMap.isRunning ||
                                    curr.notFullyIdle != activeInMap.notFullyIdle ||
                                    curr.hasActivity != activeInMap.hasActivity ||
                                    (activeInMap.stepCount > curr.stepCount)) {
                                    _currentConversation.value = curr.copy(
                                        title = newTitle,
                                        summary = activeInMap.summary ?: curr.summary,
                                        workspaceUri = newWorkspaceUri,
                                        stepCount = if (activeInMap.stepCount > 0) activeInMap.stepCount else curr.stepCount,
                                        isRunning = activeInMap.isRunning,
                                        notFullyIdle = activeInMap.notFullyIdle,
                                        hasActivity = activeInMap.hasActivity
                                    )
                                }
                            }
                        }
                        _hasReceivedInitialSync.value = true
                        if (systemConnectionState.value.isBridgeOnline) {
                            _isServerOnline.value = true
                            _isBridgeOnline.value = true
                        }
                        _conversationError.value = null
                        _isConversationsLoading.value = false
                        if (!_agyAuthInfo.value.isLoggedIn || _agyAuthInfo.value.status == com.example.gemini.data.remote.AgyHubClient.AgyAuthStatus.OFFLINE || _agyAuthInfo.value.status == com.example.gemini.data.remote.AgyHubClient.AgyAuthStatus.CHECKING) {
                            checkAgyAuthStatus(userInitiated = false)
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        break
                    }
                    android.util.Log.e("ChatViewModel", "subscribeToSummaries failed: ${e.message}")
                    _isServerOnline.value = false
                    if (_conversations.value.any { it.isRunning }) {
                        _conversations.value = _conversations.value.map { if (it.isRunning) it.copy(isRunning = false) else it }
                    }
                    if (_currentConversation.value?.isRunning == true) {
                        _currentConversation.value = _currentConversation.value?.copy(isRunning = false)
                    }
                    val rawErr = e.message ?: "Connection failed"
                    val helpfulMsg = if (rawErr.contains("Connect", ignoreCase = true) || rawErr.contains("Failed to connect", ignoreCase = true)) {
                        "Cannot connect to Antigravity Hub (${AuthPreferences.currentHubUrl}). Make sure 'agy --hub' is running."
                    } else {
                        "Antigravity Hub unreachable: $rawErr"
                    }
                    val isStarting = agyBridgeService.hubStatus.value.status == "starting"
                    if (_conversations.value.isEmpty() && !isStarting) {
                        _conversationError.value = helpfulMsg
                        _isConversationsLoading.value = false
                    } else if (isStarting || _conversations.value.isEmpty()) {
                        _isConversationsLoading.value = true
                    } else {
                        _isConversationsLoading.value = false
                    }
                    delay(20_000) // Auto-retry conversation sync every 20 seconds while offline
                }
            }
        }
    }

    /**
     * Retries all connections: both sidebar conversation list and active chat stream
     */
    override fun retryConnections() {
        _conversationError.value = null
        val conv = _currentConversation.value
        val isExistingConv = conv != null && conv.title != "New Chat" && _conversations.value.any { it.id == conv.id }
        if (_messages.value.isEmpty() && isExistingConv) {
            _isLoadingConversation.value = true
        }
        _isReconnecting.value = true
        syncAgyConversations(force = true)
        val convId = conv?.id
        if (!convId.isNullOrBlank() && isExistingConv) {
            startPersistentStream(convId)
        }
        refreshQuotas()
        checkBridgeHealth()
        checkAgyAuthStatus()
        backendScope.launch {
            com.example.gemini.data.daemon.TermuxDaemonManager.checkHealthAndReconnect(isSilent = false)
        }
    }

    /**
     * Called when the app comes into focus / foreground.
     * Checks both RPC streams and reconnects if dropped.
     */
    override fun onAppForegrounded() {
        android.util.Log.d("ChatViewModel", "App foregrounded: inspecting RPC streams...")
        backendScope.launch {
            val conv = _currentConversation.value
            val isExistingConv = conv != null && conv.title != "New Chat" && _conversations.value.any { it.id == conv.id }
            val isSyncActive = syncJob?.isActive == true
            val isStreamActive = persistentStreamJob?.isActive == true

            if (!isSyncActive || _isServerOnline.value != true) {
                syncAgyConversations(force = true)
            }
            if (conv != null && isExistingConv && (!isStreamActive || _isServerOnline.value != true)) {
                startPersistentStream(conv.id)
            }
            refreshQuotas()
            if (!_agyAuthInfo.value.isLoggedIn || _agyAuthInfo.value.status == com.example.gemini.data.remote.AgyHubClient.AgyAuthStatus.CHECKING) {
                checkAgyAuthStatus(userInitiated = false)
            }
            com.example.gemini.data.daemon.TermuxDaemonManager.checkHealthAndReconnect(isSilent = true)
        }
    }

    fun onProjectChanged(projectPath: String) {
        _bridgeStatusMessage.value = null
        val conv = _currentConversation.value
        if (conv != null) {
            val uri = if (projectPath.isNotBlank()) {
                if (projectPath.startsWith("file://")) projectPath else "file://$projectPath"
            } else ""
            _currentConversation.value = conv.copy(workspaceUri = uri)
        }
        backendScope.launch {
            val httpUrl = AuthPreferences.currentBridgeHttpUrl
            val model = _selectedModelId.value.ifBlank { _availableModels.value.firstOrNull()?.id ?: "" }
            agyBridgeService.prewarm(
                conversationId = _currentConversation.value?.id,
                model = model,
                workspaceDir = projectPath,
                httpBaseUrl = httpUrl
            )
        }
    }

    fun onUserStartedTyping() {
        // No-op: agy --hub manages models dynamically without prewarm
    }

    private fun checkPrewarmCandidate(conv: Conversation) {
        // Official agy --hub manages models dynamically without prewarm
    }

    override fun startNewChat() {
        activeStreamConversationId = null
        currentAssistantMsgId = null
        currentTurnStartStep = 0
        totalStepsCount = 0
        isPromptInFlight = false
        hasStartedRunning = false
        hasSeenTurnActivity = false
        _isStreaming.value = false
        _bridgeStatusMessage.value = null

        val modelToUse = if (_selectedModelId.value.isNotBlank()) {
            com.example.gemini.data.remote.AgyHubClient.resolveModelEnum(_selectedModelId.value)
        } else {
            (_enabledModels.value.firstOrNull()?.id ?: "")
        }
        val defaultProjPath = com.example.gemini.data.daemon.TermuxDaemonManager.activeProject.value?.path ?: ""
        val defaultWorkspaceUri = if (defaultProjPath.isNotBlank()) {
            if (defaultProjPath.startsWith("file://")) defaultProjPath else "file://$defaultProjPath"
        } else ""
        val newConv = Conversation(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            modelId = modelToUse,
            sessionId = UUID.randomUUID().toString(),
            workspaceUri = defaultWorkspaceUri
        )
        _currentConversation.value = newConv
        val newEngine = getTrajectoryEngine(newConv.id)
        newEngine.reset(newConv.id, force = true)
        com.example.gemini.ui.components.ToolCallExpansionCache.setChat(newConv.id)
        com.example.gemini.ui.components.CodeBlockExpansionCache.setChat(newConv.id)
        _messages.value = emptyList()
        _artifacts.value = emptyList()
        _isLoadingConversation.value = false
        _conversationError.value = null
    }

    override fun selectConversation(id: String) {
        Log.d("CHAT_OPEN_DEBUG", "👉 [ChatViewModel.selectConversation] id=$id, activeStreamId=$activeStreamConversationId, isLoading=${_isLoadingConversation.value}, msgCount=${_messages.value.size}")
        if (id == activeStreamConversationId && !_isLoadingConversation.value && _messages.value.isNotEmpty()) {
            Log.d("CHAT_OPEN_DEBUG", "👉 [ChatViewModel.selectConversation] Skipped: already active conversation with messages")
            return
        }

        activeStreamConversationId = id
        currentAssistantMsgId = null
        currentTurnStartStep = 0
        totalStepsCount = 0
        isPromptInFlight = false
        hasStartedRunning = false
        hasSeenTurnActivity = false
        _bridgeStatusMessage.value = null

        val engine = getTrajectoryEngine(id)
        com.example.gemini.ui.components.ToolCallExpansionCache.setChat(id)
        com.example.gemini.ui.components.CodeBlockExpansionCache.setChat(id)
        currentTrajectoryId = engine.trajectoryId

        val cached = conversationMessagesCache[id]?.takeIf { it.isNotEmpty() } ?: engine.toChatMessages(id)
        if (cached.isNotEmpty()) {
            _messages.value = cached
            _isLoadingConversation.value = false
        } else {
            _messages.value = emptyList()
            _isLoadingConversation.value = true
        }

        _isStreaming.value = engine.isRunning && !engine.isWaitingInteraction
        _artifacts.value = engine.artifacts.value

        backendScope.launch {
            _conversationError.value = null

            val conv = _conversations.value.find { it.id == id }
                ?: Conversation(id = id, title = "Antigravity Chat", sessionId = id)
            Log.d("CHAT_OPEN_DEBUG", "👉 [ChatViewModel.selectConversation] Launching stream for conv: title='${conv.title}', id=${conv.id}")
            _currentConversation.value = conv
            knownDaemonCascadeIds.add(id)

            // Connect persistent stream for real-time live streaming.
            startPersistentStream(id)
        }
    }

    fun startPersistentStream(conversationId: String) {
        Log.d("CHAT_OPEN_DEBUG", "🌊 [ChatViewModel.startPersistentStream] convId=$conversationId, current activeId=$activeStreamConversationId")
        if (conversationId.isBlank()) return

        // Another agent's chat is on screen: never stream it from the AGY hub
        if (_currentConversation.value?.let { it.id == conversationId && it.agent != AgentKind.AGY } == true) return

        // Brand new unsaved conversation: do not fetch trajectory from backend
        val isNewUnsaved = (_currentConversation.value?.id == conversationId && _currentConversation.value?.title == "New Chat" && _messages.value.isEmpty()) && !_conversations.value.any { it.id == conversationId }
        if (isNewUnsaved) {
            Log.d("CHAT_OPEN_DEBUG", "🌊 [ChatViewModel.startPersistentStream] Skipped: $conversationId is a brand new unsaved chat")
            _isLoadingConversation.value = false
            _conversationError.value = null
            return
        }

        activeStreamConversationId = conversationId

        val existingJob = activeStreamJobs[conversationId]
        if (existingJob != null && existingJob.isActive) {
            Log.d("CHAT_OPEN_DEBUG", "🌊 [ChatViewModel.startPersistentStream] Already actively running in background for $conversationId")
            val engine = getTrajectoryEngine(conversationId)
            val cached = conversationMessagesCache[conversationId]?.takeIf { it.isNotEmpty() } ?: engine.toChatMessages(conversationId)
            if (cached.isNotEmpty()) {
                _messages.value = cached
                _artifacts.value = engine.artifacts.value
                _isLoadingConversation.value = false
                _isStreaming.value = engine.isRunning && !engine.isWaitingInteraction
            }
            return
        }

        currentTurnStartStep = 0
        totalStepsCount = 0

        val engine = getTrajectoryEngine(conversationId)
        val cached = conversationMessagesCache[conversationId]?.takeIf { it.isNotEmpty() } ?: engine.toChatMessages(conversationId)
        if (cached.isNotEmpty()) {
            _messages.value = cached
            _isLoadingConversation.value = false
        } else if (_messages.value.isEmpty()) {
            _isLoadingConversation.value = true
            _conversationError.value = null
        }

        val job = backendScope.launch(Dispatchers.IO) {
            val hubUrl = AuthPreferences.currentHubUrl
            var isFirstChunk = true

            if (engine.conversationId != conversationId || engine.toChatMessages(conversationId).isEmpty()) {
                engine.reset(conversationId)
            }

            Log.d("CHAT_OPEN_DEBUG", "🌊 [ChatViewModel.startPersistentStream] Starting loop for $conversationId on $hubUrl")

            try {
                agyHubClient.streamAgentStateUpdates(conversationId, hubUrl).collect { resp ->
                    val update = resp.update
                    val statusStr = update?.status?.name ?: ""
                    val stepsCount = update?.main_trajectory_update?.steps_update?.steps?.size ?: 0
                    Log.d("CHAT_OPEN_DEBUG", "📥 [ChatViewModel Stream Frame] convId=$conversationId, status=$statusStr, stepsCount=$stepsCount, active=$activeStreamConversationId")
                    isFirstChunk = false
                    val turns = if (update != null) engine.ingestAgentStateUpdate(update) else emptyList()
                    val msgs = engine.toChatMessages(conversationId)
                    conversationMessagesCache[conversationId] = msgs

                    val isRunning = engine.isRunning
                    val isWaiting = engine.isWaitingInteraction

                    // Keep conversation list running status updated across all active chats
                    val currentList = _conversations.value
                    val convIdx = currentList.indexOfFirst { it.id == conversationId }
                    if (convIdx >= 0 && currentList[convIdx].isRunning != isRunning) {
                        val updatedList = currentList.toMutableList()
                        updatedList[convIdx] = updatedList[convIdx].copy(isRunning = isRunning)
                        _conversations.value = updatedList
                    }

                    withContext(Dispatchers.Main) {
                        if (activeStreamConversationId == conversationId) {
                            _isServerOnline.value = true
                            _conversationError.value = null
                            _isLoadingConversation.value = false
                            _isReconnecting.value = false
                            _isStreaming.value = isRunning && !isWaiting
                            _messages.value = msgs
                            _artifacts.value = engine.artifacts.value
                        }

                        if (msgs.isNotEmpty()) {
                            com.example.gemini.ui.chat.ChatFeedCache.prewarm(msgs)

                            msgs.flatMap { it.toolCalls }.filter {
                                it.toolType == com.example.gemini.domain.model.ToolType.GENERATE_IMAGE && it.output.isNotBlank()
                            }.forEach { tc ->
                                com.example.gemini.data.remote.HubMediaResolver.resolveMediaUri(application, tc.output, agyHubClient, hubUrl)
                            }
                            msgs.flatMap { it.attachments }.filter { it.isImage && it.path.isNotBlank() }.forEach { att ->
                                val rawUri = if (att.path.startsWith("file://") || att.path.startsWith("http")) att.path else "file://${att.path}"
                                com.example.gemini.data.remote.HubMediaResolver.resolveMediaUri(application, rawUri, agyHubClient, hubUrl)
                            }
                        }
                    }
                }

                if (isFirstChunk) {
                    val isHubOnline = agyBridgeService.hubStatus.value.status == "online"
                    Log.w("CHAT_OPEN_DEBUG", "⚠️ [ChatViewModel Stream Loop] Stream finished without delivering frames: isFirstChunk=true, isHubOnline=$isHubOnline")
                    withContext(Dispatchers.Main) {
                        if (activeStreamConversationId == conversationId) {
                            _isLoadingConversation.value = false
                            _isReconnecting.value = false
                            val isKnownExisting = _conversations.value.any { it.id == conversationId && it.title != "New Chat" }
                            if (isKnownExisting && _messages.value.isEmpty()) {
                                _conversationError.value = "No messages received from Antigravity. Tap Retry."
                                _isServerOnline.value = isHubOnline
                            } else {
                                _conversationError.value = null
                            }
                        }
                    }
                } else {
                    // Normal disconnection after receiving data
                    withContext(Dispatchers.Main) {
                        if (activeStreamConversationId == conversationId) {
                            _isServerOnline.value = false
                            _isReconnecting.value = false
                            if (isPromptInFlight || _isStreaming.value) {
                                isPromptInFlight = false
                                _isStreaming.value = false
                                engine.cancelRunning()
                                markLastAssistantMessageDisconnected()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("CHAT_OPEN_DEBUG", "❌ [ChatViewModel Stream Exception] convId=$conversationId: ${e.message}", e)
                    Log.w("ChatViewModel", "Persistent stream error ($conversationId): ${e.message}")
                    val rawErr = e.localizedMessage ?: e.message ?: e.toString()
                    val helpfulMsg = if (rawErr.contains("Connect", ignoreCase = true) || rawErr.contains("Failed to connect", ignoreCase = true)) {
                        "Cannot connect to Antigravity. Make sure Antigravity is running and tap Retry."
                    } else {
                        "Error loading conversation: $rawErr"
                    }

                    withContext(Dispatchers.Main) {
                        if (activeStreamConversationId == conversationId) {
                            _isServerOnline.value = false
                            _isReconnecting.value = false
                            _isLoadingConversation.value = false
                            if (_messages.value.isEmpty()) {
                                _conversationError.value = helpfulMsg
                            }
                            if (isPromptInFlight || _isStreaming.value) {
                                isPromptInFlight = false
                                _isStreaming.value = false
                                engine.cancelRunning()
                                markLastAssistantMessageDisconnected()
                            }
                        }
                    }
                }
            } finally {
                activeStreamJobs.remove(conversationId)
            }
        }
        activeStreamJobs[conversationId] = job
    }

    private fun markLastAssistantMessageDisconnected() {
        val list = _messages.value.toMutableList()
        var updated = false
        for (i in list.indices.reversed()) {
            val msg = list[i]
            if (msg.role == MessageRole.ASSISTANT && msg.isStreaming) {
                val newContent = if (msg.content.isBlank() && msg.toolCalls.isEmpty()) {
                    "⚠️ Connection to Antigravity daemon was interrupted."
                } else {
                    msg.content
                }
                list[i] = msg.copy(content = newContent, isStreaming = false)
                updated = true
            }
        }
        if (updated) {
            _messages.value = list
            com.example.gemini.ui.chat.ChatFeedCache.prewarm(list)
        }
    }

    override fun deleteConversation(id: String) {
        backendScope.launch {
            _conversations.value = _conversations.value.filter { it.id != id }
            knownDaemonCascadeIds.remove(id)
            val hubUrl = AuthPreferences.currentHubUrl
            agyHubClient.deleteCascadeTrajectory(id, hubUrl)
            
            // Thoroughly clean up local Termux disk state so the chat never reappears on app restart
            try {
                val homeDir = System.getenv("HOME") ?: "/data/user/0/com.termux/files/home"
                java.io.File("$homeDir/.gemini/antigravity-cli/brain/$id").deleteRecursively()
                java.io.File("$homeDir/.gemini/antigravity-cli/conversations/$id.db").delete()
                java.io.File("$homeDir/.gemini/antigravity-cli/annotations/$id.pbtxt").delete()
                java.io.File("$homeDir/.gemini/antigravity-cli/presence/$id.lock").delete()
            } catch (e: Exception) {
                Log.w("AgyChatBackend", "Disk cleanup error for $id: ${e.message}")
            }

            if (_currentConversation.value?.id == id) {
                val remaining = _conversations.value
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    startNewChat()
                }
            }
        }
    }

    override fun forkConversation(id: String) {
        backendScope.launch {
            _isLoadingConversation.value = true
            _conversationError.value = null
            try {
                val hubUrl = AuthPreferences.currentHubUrl
                val res = agyHubClient.forkConversation(sourceCascadeId = id, forkAtStepIndex = null, hubUrl = hubUrl)
                if (res.isSuccess) {
                    val newCascadeId = res.getOrThrow()
                    if (newCascadeId.isNotBlank()) {
                        selectConversation(newCascadeId)
                    }
                } else {
                    _conversationError.value = "Fork failed: ${res.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Fork failed: ${e.message}")
                _conversationError.value = "Fork error: ${e.message}"
            } finally {
                _isLoadingConversation.value = false
            }
        }
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
        _bridgeStatusMessage.value = null
        val conv = _currentConversation.value
        if (conv != null && conv.agent == AgentKind.AGY) {
            val updated = conv.copy(modelId = modelId)
            _currentConversation.value = updated
            _conversations.value = _conversations.value.map { if (it.id == updated.id) updated else it }
        }
        val modelObj = _availableModels.value.find { it.id == modelId || it.key == modelId }
        val modelKey = modelObj?.key ?: ""
        val modelName = modelObj?.displayName ?: ""
        backendScope.launch {
            authPrefs.savePreferredModel(modelId = modelId, modelKey = modelKey, displayName = modelName)
        }
    }


    override fun sendMessage(content: String) {
        if ((content.isBlank() && _attachments.value.isEmpty()) || isCurrentChatActivelyRunning.value) return

        val currentAtts = _attachments.value
        val rawPrompt = content.trim().ifBlank {
            if (currentAtts.any { it.isAudio }) "Voice note" else if (currentAtts.any { it.isImage }) "Image" else "Attachment"
        }
        _attachments.value = emptyList()

        val conv = _currentConversation.value ?: return
        val finalPrompt = AntigravityOptimizer.expandMentionsInPrompt(rawPrompt, conv.workspaceUri)

        var updatedConv = conv
        val isFirstUserMsg = _messages.value.none { it.role == MessageRole.USER }
        val isGenericTitle = updatedConv.title == "New Chat" || updatedConv.title == "Antigravity Chat" || updatedConv.title.isBlank()
        if (isGenericTitle || isFirstUserMsg) {
            val firstLine = rawPrompt.trim().lines().firstOrNull { it.isNotBlank() } ?: "Chat"
            val cleanTitle = firstLine.take(40) + if (firstLine.length > 40) "..." else ""
            updatedConv = updatedConv.copy(title = cleanTitle)
        }

        updatedConv = updatedConv.copy(updatedAt = System.currentTimeMillis())
        _currentConversation.value = updatedConv

        trajectoryEngine.submitUserPrompt(rawPrompt, currentAtts, conv.id)
        val updatedList = trajectoryEngine.toChatMessages(conv.id)
        conversationMessagesCache[conv.id] = updatedList
        _messages.value = updatedList
        com.example.gemini.ui.chat.ChatFeedCache.prewarm(updatedList)

        backendScope.launch {
            val exists = _conversations.value.any { it.id == updatedConv.id }
            val newConversations = if (exists) {
                _conversations.value.map { if (it.id == updatedConv.id) updatedConv else it }
            } else {
                listOf(updatedConv) + _conversations.value
            }
            _conversations.value = newConversations.sortedByDescending { it.updatedAt }

            // Prepare media payload for all attachments (images, audio, files)
            val mediaList = mutableListOf<com.example.gemini.data.remote.AgyHubClient.AgyMediaItem>()
            val hubUrl = AuthPreferences.currentHubUrl

            for (att in currentAtts) {
                var hostPath = if (att.path.isNotBlank() && !att.path.startsWith("/data/") && !att.path.startsWith("content://")) att.path else ""
                val fileObj = if (att.path.isNotBlank()) java.io.File(att.path) else null
                val localUriFile = if (!att.localUri.isNullOrBlank()) java.io.File(android.net.Uri.parse(att.localUri).path ?: "") else null
                val b64 = when {
                    !att.base64.isNullOrBlank() -> att.base64
                    fileObj != null && fileObj.exists() && fileObj.length() <= 4 * 1024 * 1024 -> {
                        try {
                            android.util.Base64.encodeToString(fileObj.readBytes(), android.util.Base64.NO_WRAP)
                        } catch (e: Throwable) {
                            null
                        }
                    }
                    localUriFile != null && localUriFile.exists() && localUriFile.length() <= 4 * 1024 * 1024 -> {
                        try {
                            android.util.Base64.encodeToString(localUriFile.readBytes(), android.util.Base64.NO_WRAP)
                        } catch (e: Throwable) {
                            null
                        }
                    }
                    else -> null
                }

                val ext = (att.name.ifBlank { hostPath }).substringAfterLast('.', "").lowercase()
                val resolvedMime = when {
                    !att.mimeType.isNullOrBlank() -> att.mimeType
                    att.isAudio -> "audio/mp4"
                    att.isImage -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/jpeg"
                    else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                }

                if (hostPath.isBlank() && !b64.isNullOrBlank() && systemConnectionState.value.canSend) {
                    try {
                        val saveRes = agyHubClient.saveMediaAsArtifact(
                            mimeType = resolvedMime,
                            base64Data = b64,
                            description = att.name,
                            thumbnailBase64 = if (att.isImage) b64 else "",
                            hubUrl = hubUrl
                        )
                        if (saveRes.isSuccess) {
                            hostPath = saveRes.getOrThrow()
                        }
                    } catch (e: Exception) {
                        Log.w("ChatViewModel", "SaveMediaAsArtifact failed: ${e.message}")
                    }
                }
                if (hostPath.isBlank() && att.path.isNotBlank()) {
                    hostPath = att.path
                }

                val isImageOrAudio = resolvedMime.startsWith("image/") || resolvedMime.startsWith("audio/")
                if (!b64.isNullOrBlank() || hostPath.isNotBlank()) {
                    mediaList.add(
                        com.example.gemini.data.remote.AgyHubClient.AgyMediaItem(
                            mimeType = resolvedMime,
                            base64 = if (isImageOrAudio) (b64 ?: "") else "",
                            durationSeconds = att.durationSeconds,
                            description = att.name.ifBlank { if (att.isAudio) "Voice note" else if (att.isImage) "Image" else "Attachment" },
                            uri = if (hostPath.isNotBlank()) hostPath else null,
                            thumbnail = if (att.isImage) b64 else null
                        )
                    )
                }
            }

            executeStream(updatedConv, updatedList, mediaItems = mediaList, expandedPromptOverride = finalPrompt)
        }
    }

    val isTranscribingAudio = MutableStateFlow(false)

    fun transcribeAudioFile(
        file: java.io.File,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!file.exists() || file.length() == 0L) {
            onError("Audio file empty or not found")
            return
        }
        backendScope.launch {
            isTranscribingAudio.value = true
            try {
                val bytes = file.readBytes()
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val hub = AuthPreferences.currentHubUrl
                val res = agyHubClient.getTranscription(audioBase64 = b64, hubUrl = hub)
                if (res.isSuccess) {
                    val text = res.getOrThrow().trim()
                    if (text.isNotBlank()) {
                        onDone(text)
                    } else {
                        onError("No speech recognized in recording")
                    }
                } else {
                    val fallback = com.example.gemini.data.remote.DirectApiClient.transcribeAudioWav(application, bytes)
                    if (!fallback.isNullOrBlank()) {
                        onDone(fallback.trim())
                    } else {
                        val msg = res.exceptionOrNull()?.message ?: "Transcription failed"
                        onError(msg)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "transcribeAudioFile error: ${e.message}", e)
                onError(e.message ?: "Transcription error")
            } finally {
                isTranscribingAudio.value = false
            }
        }
    }

    fun retryMessage(messageId: String) {
        if (_isStreaming.value) return
        val conv = _currentConversation.value ?: return
        val current = _messages.value
        val index = current.indexOfFirst { it.id == messageId }
        if (index < 0) return

        val targetMsg = current[index]
        if (targetMsg.role == MessageRole.USER) {
            // Truncate following assistant responses and re-stream
            val truncated = current.take(index + 1)
            _messages.value = truncated
            backendScope.launch {
                executeStream(conv, truncated)
            }
        } else {
            // Assistant response retry: truncate this response and re-execute
            val truncated = current.take(index)
            _messages.value = truncated
            backendScope.launch {
                executeStream(conv, truncated)
            }
        }
    }

    fun prepareEditMessage(messageId: String): String? {
        val conv = _currentConversation.value ?: return null
        val current = _messages.value
        val index = current.indexOfFirst { it.id == messageId }
        if (index < 0) return null

        val targetMsg = current[index]
        val truncated = current.take(index)
        _messages.value = truncated
        return targetMsg.content
    }

    /**
     * Reverts a user message: restores prompt text and attachments to the input box,
     * immediately prunes it from the local UI, and sends an undo RPC to the AGY hub daemon.
     */
    fun revertAndEditLastUserMessage(targetMsg: ChatMessage, onRestored: (String) -> Unit) {
        val conv = _currentConversation.value ?: return
        val current = _messages.value
        val index = current.indexOfFirst { it.id == targetMsg.id }
        if (index < 0) return

        activeStreamJobs[conv.id]?.cancel()
        activeStreamJobs.remove(conv.id)
        _isStreaming.value = false
        isPromptInFlight = false

        val truncated = current.take(index)
        _messages.value = truncated

        val imageRegex = Regex("""\[Attached Image:\s*([^\]]+)\]\([^\)]+\)""", RegexOption.IGNORE_CASE)
        val fileRegex = Regex("""\[Attached File:\s*([^\]]+)\]\([^\)]+\)""", RegexOption.IGNORE_CASE)
        var cleanText = targetMsg.content
            .replace(imageRegex, "")
            .replace(fileRegex, "")
            .trim()
        if (cleanText == "Voice note" || cleanText == "Voice message") {
            cleanText = ""
        }

        if (targetMsg.attachments.isNotEmpty()) {
            _attachments.value = targetMsg.attachments
        }

        onRestored(cleanText)

        val isFirstUserMsg = index == 0 || current.none { it.role == MessageRole.USER && it.id != targetMsg.id }
        if (isFirstUserMsg) {
            // Optimistically clean up sidebar & switch to fresh new chat right away
            _conversations.value = _conversations.value.filter { it.id != conv.id }
            knownDaemonCascadeIds.remove(conv.id)
            activeStreamConversationId = null
            val modelToUse = if (_selectedModelId.value.isNotBlank()) {
                com.example.gemini.data.remote.AgyHubClient.resolveModelEnum(_selectedModelId.value)
            } else {
                (_enabledModels.value.firstOrNull()?.id ?: "")
            }
            val defaultProjPath = com.example.gemini.data.daemon.TermuxDaemonManager.activeProject.value?.path ?: ""
            val defaultWorkspaceUri = if (defaultProjPath.isNotBlank()) {
                if (defaultProjPath.startsWith("file://")) defaultProjPath else "file://$defaultProjPath"
            } else ""
            _currentConversation.value = Conversation(
                id = UUID.randomUUID().toString(),
                title = "New Chat",
                modelId = modelToUse,
                sessionId = UUID.randomUUID().toString(),
                workspaceUri = defaultWorkspaceUri
            )
        }

        backendScope.launch(Dispatchers.IO) {
            val hubUrl = AuthPreferences.currentHubUrl
            val modelEnum = com.example.gemini.data.remote.AgyHubClient.resolveModelEnum(_selectedModelId.value)
            val res = agyHubClient.revertUserMessage(conv.id, modelEnum, targetMsg.stepIndex, hubUrl)
            if (res.isSuccess) {
                val targetStep = res.getOrThrow()
                if (targetStep < 0) {
                    withContext(Dispatchers.Main) {
                        _conversations.value = _conversations.value.filter { it.id != conv.id }
                        knownDaemonCascadeIds.remove(conv.id)
                        if (_currentConversation.value?.id == conv.id) {
                            startNewChat()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        startPersistentStream(conv.id)
                    }
                }
            } else {
                android.util.Log.w("ChatViewModel", "revertUserMessage failed on hub: ${res.exceptionOrNull()?.message}")
                if (!isFirstUserMsg) {
                    withContext(Dispatchers.Main) {
                        startPersistentStream(conv.id)
                    }
                }
            }
        }
    }

    private suspend fun executeStream(
        conv: Conversation, 
        currentHistory: List<ChatMessage>,
        isRetryAfterRefresh: Boolean = false,
        existingAssistantMsgId: String? = null,
        existingToolCalls: List<com.example.gemini.domain.model.ToolCall> = emptyList(),
        priorTextPrefix: String = "",
        mediaItems: List<com.example.gemini.data.remote.AgyHubClient.AgyMediaItem> = emptyList(),
        expandedPromptOverride: String? = null
    ) {
        _bridgeStatusMessage.value = null

        val assistantMsgId = existingAssistantMsgId ?: UUID.randomUUID().toString()
        currentAssistantMsgId = assistantMsgId

        if (existingAssistantMsgId == null) {
            val hasStreamingAssistant = _messages.value.any { it.role == MessageRole.ASSISTANT && it.isStreaming }
            if (!hasStreamingAssistant) {
                val assistantMsg = ChatMessage(
                    id = assistantMsgId,
                    conversationId = conv.id,
                    role = MessageRole.ASSISTANT,
                    content = priorTextPrefix,
                    toolCalls = existingToolCalls,
                    isStreaming = true
                )
                _messages.value = _messages.value + assistantMsg
            }
        } else {
            updateAssistantMessage(
                msgId = assistantMsgId,
                content = priorTextPrefix,
                thought = "",
                thoughtDuration = null,
                toolCalls = existingToolCalls,
                isStreaming = true,
                forceImmediate = true
            )
        }

        val hubUrl = AuthPreferences.currentHubUrl
        val rawUserPrompt = currentHistory.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val convWorkspace = conv.workspaceUri
        val workspaceUri = if (convWorkspace.isNotBlank()) {
            if (convWorkspace.startsWith("file://")) convWorkspace else "file://$convWorkspace"
        } else ""
        val userPrompt = expandedPromptOverride ?: AntigravityOptimizer.expandMentionsInPrompt(rawUserPrompt, workspaceUri)

        // If local AGY Hub is offline or unauthenticated, immediately use the built-in Direct Antigravity Engine
        if (!systemConnectionState.value.canSend) {
            streamDirectAntigravityFallback(
                conv = conv,
                currentHistory = currentHistory,
                assistantMsgId = assistantMsgId,
                workspaceUri = workspaceUri,
                expandedUserPrompt = userPrompt
            )
            return
        }

        val modelEnum = com.example.gemini.data.remote.AgyHubClient.resolveModelEnum(_selectedModelId.value)
        val selectedModel = _enabledModels.value.find { it.id == modelEnum }
        val supportsThinking = selectedModel?.supportsThinking ?: (modelEnum.contains("thinking", ignoreCase = true) || modelEnum.contains("flash", ignoreCase = true) || modelEnum.contains("pro", ignoreCase = true))
        val thinkingBudget = AntigravityOptimizer.resolveThinkingBudget(supportsThinking)

        val isKnownOnDaemon = knownDaemonCascadeIds.contains(conv.id) ||
                (totalStepsCount > 0 && conv.title != "New Chat" && conv.title != "Antigravity Chat" && conv.title.isNotBlank())

        backendScope.launch(Dispatchers.IO) {
            try {
                // If brand new conversation not yet on daemon, call startCascade FIRST and await it!
                if (!isKnownOnDaemon) {
                    val startRes = agyHubClient.startCascade(
                        cascadeId = conv.id,
                        modelEnum = modelEnum,
                        workspaceUri = workspaceUri,
                        hubUrl = hubUrl
                    )
                    if (startRes.isFailure) {
                        val err = startRes.exceptionOrNull()?.message ?: "Failed to start conversation on daemon"
                        Log.w("ChatViewModel", "startCascade failed ($err), switching to Direct Antigravity Engine fallback")
                        streamDirectAntigravityFallback(
                            conv = conv,
                            currentHistory = currentHistory,
                            assistantMsgId = assistantMsgId,
                            workspaceUri = workspaceUri,
                            expandedUserPrompt = userPrompt
                        )
                        return@launch
                    }
                    knownDaemonCascadeIds.add(conv.id)
                    if (workspaceUri.isNotBlank()) {
                        val projName = java.io.File(workspaceUri.removePrefix("file://")).name
                        agyHubClient.updateProjectSettings(
                            projectId = "default-cli-project",
                            projectName = projName,
                            folderUris = listOf(workspaceUri),
                            hubUrl = hubUrl
                        )
                    }
                    val updatedWithWs = conv.copy(workspaceUri = workspaceUri)
                    withContext(Dispatchers.Main) {
                        _currentConversation.value = updatedWithWs
                        _conversations.value = _conversations.value.map { if (it.id == updatedWithWs.id) updatedWithWs else it }
                    }
                }

                // Ensure persistent stream is active for this conversation
                if (activeStreamConversationId != conv.id || persistentStreamJob?.isActive != true) {
                    startPersistentStream(conv.id)
                    delay(50)
                }

                // Reset turn boundary and state for this active turn
                currentTurnStartStep = totalStepsCount
                hasStartedRunning = false
                hasSeenTurnActivity = false

                val currentAutoExecPolicy = authPrefs.commandAutoExecutionPolicy.firstOrNull() ?: "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER"

                var sendRes = agyHubClient.sendUserPrompt(
                    cascadeId = conv.id,
                    text = userPrompt,
                    modelEnum = modelEnum,
                    thinkingBudget = thinkingBudget,
                    autoExecutionPolicy = currentAutoExecPolicy,
                    media = mediaItems,
                    hubUrl = hubUrl
                )

                if (sendRes.isFailure) {
                    val err = sendRes.exceptionOrNull()?.message ?: "Failed to send message"
                    Log.e("ChatViewModel", "sendUserPrompt error: $err")
                    if (err.contains("trajectory not found", ignoreCase = true) || err.contains("not found", ignoreCase = true)) {
                        Log.i("ChatViewModel", "Trajectory not found on daemon. Attempting to restart cascade ${conv.id} and retry...")
                        val restartRes = agyHubClient.startCascade(
                            cascadeId = conv.id,
                            modelEnum = modelEnum,
                            workspaceUri = workspaceUri,
                            hubUrl = hubUrl
                        )
                        if (restartRes.isSuccess) {
                            knownDaemonCascadeIds.add(conv.id)
                            sendRes = agyHubClient.sendUserPrompt(
                                cascadeId = conv.id,
                                text = userPrompt,
                                modelEnum = modelEnum,
                                thinkingBudget = thinkingBudget,
                                autoExecutionPolicy = currentAutoExecPolicy,
                                media = mediaItems,
                                hubUrl = hubUrl
                            )
                        }
                    }
                }

                if (sendRes.isSuccess) {
                    isPromptInFlight = false
                } else {
                    val err = sendRes.exceptionOrNull()?.message ?: "Failed to send message"
                    Log.w("ChatViewModel", "sendUserPrompt failed ($err), switching to Direct Antigravity Engine fallback")
                    streamDirectAntigravityFallback(
                        conv = conv,
                        currentHistory = currentHistory,
                        assistantMsgId = assistantMsgId,
                        workspaceUri = workspaceUri,
                        expandedUserPrompt = userPrompt
                    )
                }
            } catch (e: Exception) {
                Log.w("ChatViewModel", "sendUserPrompt exception (${e.message}), switching to Direct Antigravity Engine fallback")
                streamDirectAntigravityFallback(
                    conv = conv,
                    currentHistory = currentHistory,
                    assistantMsgId = assistantMsgId,
                    workspaceUri = workspaceUri,
                    expandedUserPrompt = userPrompt
                )
            }
        }
    }

    /**
     * Built-in High-Speed Direct Antigravity Engine fallback when local agy --hub daemon is offline or unauthenticated.
     * Injects active workspace rules (GEMINI.md / AGENTS.md) and executes file/terminal tools automatically.
     */
    private fun streamDirectAntigravityFallback(
        conv: Conversation,
        currentHistory: List<ChatMessage>,
        assistantMsgId: String,
        workspaceUri: String,
        expandedUserPrompt: String
    ) {
        streamingJob?.cancel()
        _isStreaming.value = true
        streamingJob = backendScope.launch(Dispatchers.IO) {
            val systemContext = AntigravityOptimizer.buildAccuracyEnrichedSystemContext(workspaceUri)
            val localMessages: List<Pair<String, String>> = currentHistory.filter { it.id != assistantMsgId }.mapIndexed { idx, m ->
                val text = if (idx == currentHistory.lastIndex && m.role == MessageRole.USER) {
                    expandedUserPrompt
                } else {
                    m.content
                }
                (if (m.role == MessageRole.USER) "user" else "assistant") to text
            }

            val accumulated = StringBuilder()
            val thoughtBuilder = StringBuilder()
            val startTime = System.currentTimeMillis()
            var isInsideThink = false

            com.example.gemini.data.remote.DirectApiClient.streamChatMessages(
                baseUrl = com.example.gemini.data.remote.DirectApiClient.ZERO_KEY_GATEWAY_URL,
                apiKey = "",
                model = "openai",
                messages = localMessages,
                systemInstruction = systemContext,
                onChunk = { chunk: String ->
                    var rem = chunk
                    while (rem.isNotEmpty()) {
                        if (!isInsideThink) {
                            val idx = rem.indexOf("<think>")
                            if (idx >= 0) {
                                accumulated.append(rem.substring(0, idx))
                                isInsideThink = true
                                rem = rem.substring(idx + 7)
                            } else {
                                accumulated.append(rem)
                                rem = ""
                            }
                        } else {
                            val idx = rem.indexOf("</think>")
                            if (idx >= 0) {
                                thoughtBuilder.append(rem.substring(0, idx))
                                isInsideThink = false
                                rem = rem.substring(idx + 8)
                            } else {
                                thoughtBuilder.append(rem)
                                rem = ""
                            }
                        }
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    backendScope.launch(Dispatchers.Main) {
                        updateAssistantMessage(
                            msgId = assistantMsgId,
                            content = accumulated.toString(),
                            thought = thoughtBuilder.toString().ifBlank { null },
                            thoughtDuration = if (thoughtBuilder.isNotEmpty()) elapsed else null,
                            isStreaming = true
                        )
                    }
                },
                onComplete = {
                    val elapsed = System.currentTimeMillis() - startTime
                    val finalContent = accumulated.toString().ifBlank { "Task completed." }
                    withContext(Dispatchers.Main) {
                        _isStreaming.value = false
                        isPromptInFlight = false
                        updateAssistantMessage(
                            msgId = assistantMsgId,
                            content = finalContent,
                            thought = thoughtBuilder.toString().ifBlank { null },
                            thoughtDuration = if (thoughtBuilder.isNotEmpty()) elapsed else null,
                            isStreaming = false,
                            forceImmediate = true
                        )
                    }
                },
                onError = { err: String ->
                    withContext(Dispatchers.Main) {
                        _isStreaming.value = false
                        isPromptInFlight = false
                        updateAssistantMessage(
                            msgId = assistantMsgId,
                            content = accumulated.toString().ifBlank { "⚠️ Antigravity connection error: $err" },
                            isStreaming = false,
                            forceImmediate = true
                        )
                    }
                }
            )
        }
    }

    private fun formatStructuredErrorMessage(
        title: String,
        userMessage: String,
        shortError: String = "",
        fullError: String = "",
        errorCode: Int? = null,
        errorId: String = ""
    ): String {
        return try {
            val json = org.json.JSONObject().apply {
                put("title", title)
                put("userMessage", userMessage)
                put("shortError", shortError.ifBlank { userMessage })
                put("fullError", fullError)
                if (errorCode != null) put("errorCode", errorCode)
                if (errorId.isNotBlank()) put("errorId", errorId)
            }.toString()
            "<!-- error -->\n$json\n<!-- /error -->"
        } catch (_: Exception) {
            "⚠️ $title: $userMessage"
        }
    }

    private var lastStreamUpdateTime = 0L

    private fun updateAssistantMessage(
        msgId: String,
        content: String,
        thought: String? = null,
        thoughtDuration: Long? = null,
        toolCalls: List<com.example.gemini.domain.model.ToolCall> = emptyList(),
        isStreaming: Boolean,
        tokenUsage: com.example.gemini.domain.model.TokenUsage? = null,
        rawPayload: String? = null,
        forceImmediate: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val frameThrottleMs = if (AntigravityOptimizer.state.value.highFrameRateStreaming) 16L else 30L
        if (!forceImmediate && isStreaming && (now - lastStreamUpdateTime < frameThrottleMs)) {
            return
        }
        lastStreamUpdateTime = now
        // Auto-extract chat title if present
        if (content.contains("<chat_title>", ignoreCase = true)) {
            val titleMatch = Regex("<chat_title>([\\s\\S]*?)</chat_title>", RegexOption.IGNORE_CASE).find(content)
            if (titleMatch != null) {
                val extractedTitle = titleMatch.groupValues[1].trim().replace("\"", "").replace("'", "")
                val conv = _currentConversation.value
                if (conv != null && extractedTitle.isNotBlank() && conv.title != extractedTitle) {
                    val updated = conv.copy(title = extractedTitle)
                    _currentConversation.value = updated
                    _conversations.value = _conversations.value.map { if (it.id == updated.id) updated else it }
                }
            }
        }

        val list = _messages.value.toMutableList()
        val index = list.indexOfFirst { it.id == msgId }
        if (index >= 0) {
            val existing = list[index]
            val finalToolCalls = if (toolCalls.isNotEmpty()) toolCalls else existing.toolCalls
            list[index] = existing.copy(
                content = content,
                thoughtText = thought?.ifEmpty { null } ?: existing.thoughtText,
                thoughtDurationMs = thoughtDuration ?: existing.thoughtDurationMs,
                toolCalls = finalToolCalls,
                isStreaming = isStreaming,
                tokenUsage = tokenUsage ?: existing.tokenUsage,
                rawPayload = rawPayload ?: existing.rawPayload
            )
            _messages.value = list
            android.util.Log.d("PERF_TRACE", "🌊 [Streaming Emit] ID=${msgId.take(8)}, len=${content.length}, isStreaming=$isStreaming")
        } else {
            val conv = _currentConversation.value
            if (conv != null) {
                val newMsg = ChatMessage(
                    id = msgId,
                    conversationId = conv.id,
                    role = MessageRole.ASSISTANT,
                    content = content,
                    toolCalls = toolCalls,
                    isStreaming = isStreaming
                )
                list.add(newMsg)
                _messages.value = list
            }
        }
        if (!isStreaming) {
            com.example.gemini.ui.chat.ChatFeedCache.prewarm(_messages.value)
        }
    }

    override fun stopStreaming() {
        val conv = _currentConversation.value
        streamingJob?.cancel()
        isPromptInFlight = false
        _isStreaming.value = false
        hasStartedRunning = false
        hasSeenTurnActivity = false
        currentAssistantMsgId = null
        _bridgeStatusMessage.value = null
        val engine = conv?.id?.let { getTrajectoryEngine(it) } ?: fallbackTrajectoryEngine
        engine.cancelRunning()

        if (conv != null) {
            activeStreamJobs[conv.id]?.cancel()
            activeStreamJobs.remove(conv.id)
            backendScope.launch {
                val hubUrl = AuthPreferences.currentHubUrl
                agyHubClient.cancelCascadeInvocation(conv.id, hubUrl)
            }

            val list = _messages.value.map {
                if (it.isStreaming) it.copy(isStreaming = false) else it
            }
            _messages.value = list
            conversationMessagesCache[conv.id] = list
            val currentList = _conversations.value
            val convIdx = currentList.indexOfFirst { it.id == conv.id }
            if (convIdx >= 0) {
                val updatedList = currentList.toMutableList()
                updatedList[convIdx] = updatedList[convIdx].copy(isRunning = false)
                _conversations.value = updatedList
            }
        }
    }

    fun applyQuotaSummary(summary: com.example.gemini.domain.model.QuotaSummaryResponse) {
        _quotaSummary.value = summary
        val geminiGroup = summary.groups.find {
            it.groupId == "gemini" || it.groupName.contains("gemini", ignoreCase = true)
        }
        val claudeGroup = summary.groups.find {
            it.groupId == "claude_gpt" || it.groupName.contains("claude", ignoreCase = true) || it.groupName.contains("gpt", ignoreCase = true)
        }

        val updatedQuotas = _availableModels.value.map { model ->
            val isClaude = model.family == com.example.gemini.domain.model.ModelFamily.CLAUDE
            val group = if (isClaude) claudeGroup else geminiGroup
            com.example.gemini.domain.model.ModelQuota(
                modelId = model.id,
                remainingFraction = group?.fiveHour?.remainingFraction,
                resetTime = group?.fiveHour?.resetTime,
                usedPercentage = group?.fiveHour?.usedPct,
                resetCountdown = group?.fiveHour?.countdown,
                weeklyRemainingFraction = group?.weekly?.remainingFraction,
                weeklyUsedPercentage = group?.weekly?.usedPct,
                weeklyResetCountdown = group?.weekly?.countdown
            )
        }
        _quotas.value = updatedQuotas
    }

    fun refreshQuotas(force: Boolean = true, showToastFeedback: Boolean = false) {
        backendScope.launch {
            _isRefreshingModels.value = true
            var failureError: String? = null
            try {
                val hubUrl = AuthPreferences.currentHubUrl
                val modelsDeferred = async { agyHubClient.getAvailableModels(forceRefresh = force, hubUrl = hubUrl) }
                val quotasDeferred = async { agyHubClient.retrieveUserQuotaSummary(forceRefresh = force, hubUrl = hubUrl) }

                val modelsRes = modelsDeferred.await()
                val quotasRes = quotasDeferred.await()

                if (modelsRes.isSuccess) {
                    val models = modelsRes.getOrThrow()
                    if (models.isNotEmpty()) {
                        com.example.gemini.data.remote.AgyHubClient.updateModelRegistry(models)
                        _availableModels.value = models
                        val currentSelected = _selectedModelId.value
                        val existingValidModel = if (currentSelected.isNotBlank()) {
                            models.find { it.id == currentSelected || it.key == currentSelected }
                        } else null

                        if (existingValidModel != null) {
                            // Current selection is already valid! Keep it and ensure canonical id
                            if (_selectedModelId.value != existingValidModel.id) {
                                _selectedModelId.value = existingValidModel.id
                            }
                        } else {
                            // First run or need to restore saved preference
                            val savedId = authPrefs.preferredModelId.firstOrNull()
                            val savedKey = authPrefs.preferredModelKey.firstOrNull()
                            val savedName = authPrefs.preferredModelName.firstOrNull()

                            val matched = models.find {
                                (!savedKey.isNullOrBlank() && (it.key.equals(savedKey, ignoreCase = true) || it.id == savedKey)) ||
                                (!savedName.isNullOrBlank() && it.displayName.equals(savedName, ignoreCase = true)) ||
                                (!savedId.isNullOrBlank() && (it.id == savedId || it.key == savedId))
                            }

                            if (matched != null) {
                                _selectedModelId.value = matched.id
                                authPrefs.savePreferredModel(modelId = matched.id, modelKey = matched.key, displayName = matched.displayName)
                            } else {
                                val preferred = models.find { it.key.contains("3.8") || it.key.contains("flash") } ?: models.first()
                                _selectedModelId.value = preferred.id
                            }
                        }

                        recomputeEnabledModels()
                    }
                } else {
                    failureError = modelsRes.exceptionOrNull()?.message
                }

                if (quotasRes.isSuccess) {
                    applyQuotaSummary(quotasRes.getOrThrow())
                } else if (failureError == null) {
                    failureError = quotasRes.exceptionOrNull()?.message
                }

                val userRes = agyHubClient.getLocalUserInfo(hubUrl)
                if (userRes.isSuccess) {
                    val (username, _) = userRes.getOrThrow()
                    if (username.isNotBlank()) {
                        _userEmail.value = username
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GeminiApp", "Error refreshing models & quotas from hub: ${e.message}")
                failureError = e.message
            } finally {
                _isRefreshingModels.value = false
                if (showToastFeedback) {
                    withContext(Dispatchers.Main) {
                        if (failureError != null) {
                            val msg = if (failureError.contains("Connect", ignoreCase = true) || failureError.contains("failed to connect", ignoreCase = true)) {
                                "Failed to refresh: Antigravity Hub (${AuthPreferences.currentHubUrl}) unreachable"
                            } else {
                                "Failed to refresh: $failureError"
                            }
                            android.widget.Toast.makeText(application, msg, android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(application, "Quotas & models refreshed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun approveAndExecuteTerminalTool(
        toolCall: com.example.gemini.domain.model.ToolCall,
        messageId: String,
        scope: String? = null
    ) {
        val conv = _currentConversation.value ?: return
        val stepIndex = toolCall.stepIndex ?: return
        val trajectoryId = toolCall.trajectoryId?.takeIf { it.isNotBlank() }
            ?: trajectoryEngine.trajectoryId

        trajectoryEngine.markStepResponded(stepIndex)
        trajectoryEngine.optimisticUpdateStepStatus(stepIndex, com.example.gemini.data.remote.dto.CortexStepStatuses.RUNNING)
        _messages.value = trajectoryEngine.toChatMessages(conv.id)

        backendScope.launch {
            val hubUrl = AuthPreferences.currentHubUrl
            val resolvedScope = scope ?: authPrefs.defaultApprovalScope.firstOrNull() ?: "PERMISSION_SCOPE_ONCE"
            val resolvedInteractionType = toolCall.interactionType
                ?: if (toolCall.name.startsWith("mcp_") || toolCall.name == "call_mcp_tool" || toolCall.name == "read_resource" || toolCall.name == "list_resources") "mcp" else "permission"
            val res = agyHubClient.handleCascadeUserInteraction(
                cascadeId = conv.id,
                stepIndex = stepIndex,
                trajectoryId = trajectoryId,
                allow = true,
                scope = resolvedScope,
                interactionType = resolvedInteractionType,
                hubUrl = hubUrl
            )
            if (res.isFailure) {
                val err = res.exceptionOrNull()?.message ?: "Failed to approve tool"
                Log.e("ChatViewModel", "handleCascadeUserInteraction (approve) error: $err")
                _conversationError.value = "Approval failed: $err"
                _isStreaming.value = false

                // Roll back optimistic state on failure so UI accurately reflects reality
                trajectoryEngine.unmarkStepResponded(stepIndex)
                trajectoryEngine.optimisticUpdateStepStatus(stepIndex, com.example.gemini.data.remote.dto.CortexStepStatuses.WAITING)
                _messages.value = trajectoryEngine.toChatMessages(conv.id)
            }
        }
    }

    fun rejectTerminalTool(toolCall: com.example.gemini.domain.model.ToolCall, messageId: String, reason: String? = null) {
        val conv = _currentConversation.value ?: return
        val stepIndex = toolCall.stepIndex ?: return
        val trajectoryId = toolCall.trajectoryId?.takeIf { it.isNotBlank() }
            ?: trajectoryEngine.trajectoryId

        val denyInstruction = reason ?: "User rejected this command."
        trajectoryEngine.markStepResponded(stepIndex)
        trajectoryEngine.optimisticUpdateStepStatus(stepIndex, com.example.gemini.data.remote.dto.CortexStepStatuses.CANCELED, output = denyInstruction)
        _messages.value = trajectoryEngine.toChatMessages(conv.id)
        _isStreaming.value = false

        backendScope.launch {
            val hubUrl = AuthPreferences.currentHubUrl
            val resolvedInteractionType = toolCall.interactionType
                ?: if (toolCall.name.startsWith("mcp_") || toolCall.name == "call_mcp_tool" || toolCall.name == "read_resource" || toolCall.name == "list_resources") "mcp" else "permission"
            val res = agyHubClient.handleCascadeUserInteraction(
                cascadeId = conv.id,
                stepIndex = stepIndex,
                trajectoryId = trajectoryId,
                allow = false,
                userDenyInstruction = denyInstruction,
                interactionType = resolvedInteractionType,
                hubUrl = hubUrl
            )
            if (res.isFailure) {
                val err = res.exceptionOrNull()?.message ?: "Failed to reject tool"
                Log.e("ChatViewModel", "handleCascadeUserInteraction (deny) error: $err")
                _conversationError.value = "Rejection failed: $err"

                // Roll back optimistic state on failure so UI accurately reflects reality
                trajectoryEngine.unmarkStepResponded(stepIndex)
                trajectoryEngine.optimisticUpdateStepStatus(stepIndex, com.example.gemini.data.remote.dto.CortexStepStatuses.WAITING)
                _messages.value = trajectoryEngine.toChatMessages(conv.id)
            }
        }
    }

    fun approveAllPendingTools(list: List<PendingToolApproval>) {
        list.forEach { approval ->
            approveAndExecuteTerminalTool(approval.toolCall, approval.messageId)
        }
    }

    fun rejectAllPendingTools(list: List<PendingToolApproval>, reason: String? = null) {
        list.forEach { approval ->
            rejectTerminalTool(approval.toolCall, approval.messageId, reason)
        }
    }

    fun terminateRunningTerminalTool(toolCall: com.example.gemini.domain.model.ToolCall, messageId: String) {
        val conv = _currentConversation.value ?: return
        val stepIndex = toolCall.stepIndex ?: return

        // Optimistically mark this specific step terminated so UI responds instantaneously
        trajectoryEngine.optimisticUpdateStepStatus(
            stepIndex = stepIndex,
            status = com.example.gemini.data.remote.dto.CortexStepStatuses.CANCELED,
            output = "Command terminated by user."
        )
        _messages.value = trajectoryEngine.toChatMessages(conv.id)

        backendScope.launch {
            val hubUrl = AuthPreferences.currentHubUrl
            val res = agyHubClient.cancelCascadeSteps(
                cascadeId = conv.id,
                stepIndices = listOf(stepIndex),
                hubUrl = hubUrl
            )
            if (res.isFailure) {
                val err = res.exceptionOrNull()?.message ?: "Failed to cancel step"
                Log.e("ChatViewModel", "cancelCascadeSteps error for step $stepIndex: $err")
            }
        }
    }

    fun proceedAfterTermination(toolCall: com.example.gemini.domain.model.ToolCall, messageId: String, userInstructions: String?) {
        _terminatedToolDialog.value = null
        val conv = _currentConversation.value ?: return
        val msg = _messages.value.find { it.id == messageId } ?: return

        backendScope.launch {
            val currentHistory = _messages.value.filter { it.id != messageId }
            val promptFeedback = if (!userInstructions.isNullOrBlank()) "\nUser Feedback: $userInstructions" else ""
            val syntheticHistory = currentHistory + listOf(
                ChatMessage(
                    conversationId = conv.id,
                    role = MessageRole.ASSISTANT,
                    content = "<execute_command>${toolCall.command}</execute_command>"
                ),
                ChatMessage(
                    conversationId = conv.id,
                    role = MessageRole.USER,
                    content = "[Terminal Command `${toolCall.command}` was terminated by user]:\n```\n${toolCall.output.ifEmpty { "(No output before termination)" }}\n```$promptFeedback"
                )
            )

            executeStream(
                conv = conv,
                currentHistory = syntheticHistory,
                existingAssistantMsgId = messageId,
                existingToolCalls = msg.toolCalls,
                priorTextPrefix = msg.content
            )
        }
    }

    fun submitUserChoices(
        toolCall: com.example.gemini.domain.model.ToolCall,
        messageId: String,
        responses: List<com.example.gemini.data.remote.dto.AskQuestionResponseItemDto>,
        summaryDisplay: String
    ) {
        val conv = _currentConversation.value ?: return
        val stepIndex = toolCall.stepIndex

        if (stepIndex != null) {
            trajectoryEngine.markStepResponded(stepIndex)
            trajectoryEngine.optimisticUpdateStepStatus(
                stepIndex = stepIndex,
                status = com.example.gemini.data.remote.dto.CortexStepStatuses.DONE,
                output = summaryDisplay
            )
            _messages.value = trajectoryEngine.toChatMessages(conv.id)
        }

        val msg = _messages.value.find { it.id == messageId }
        val completedToolCall = toolCall.copy(
            status = "SUCCESS",
            output = summaryDisplay
        )
        if (msg != null) {
            val updatedToolCalls = msg.toolCalls.map { if (it.stepIndex != null && it.stepIndex == toolCall.stepIndex) completedToolCall else it }
            updateAssistantMessage(
                msgId = messageId,
                content = msg.content,
                thought = msg.thoughtText ?: "",
                thoughtDuration = msg.thoughtDurationMs,
                toolCalls = updatedToolCalls,
                isStreaming = true
            )
        }

        backendScope.launch {
            val hubUrl = AuthPreferences.currentHubUrl
            if (stepIndex != null) {
                val res = agyHubClient.handleAskQuestionInteraction(
                    cascadeId = conv.id,
                    stepIndex = stepIndex,
                    trajectoryId = toolCall.trajectoryId ?: "",
                    responses = responses,
                    hubUrl = hubUrl
                )
                if (res.isFailure) {
                    Log.e("ChatViewModel", "handleAskQuestionInteraction failed: ${res.exceptionOrNull()}")
                }
            }
        }
    }

    fun skipUserChoices(
        toolCall: com.example.gemini.domain.model.ToolCall,
        messageId: String,
        responses: List<com.example.gemini.data.remote.dto.AskQuestionResponseItemDto> = emptyList()
    ) {
        val conv = _currentConversation.value ?: return
        val stepIndex = toolCall.stepIndex
        val skipSummary = "Skipped by user"

        if (stepIndex != null) {
            trajectoryEngine.markStepResponded(stepIndex)
            trajectoryEngine.optimisticUpdateStepStatus(
                stepIndex = stepIndex,
                status = com.example.gemini.data.remote.dto.CortexStepStatuses.DONE,
                output = skipSummary
            )
            _messages.value = trajectoryEngine.toChatMessages(conv.id)
        }

        val msg = _messages.value.find { it.id == messageId }
        val completedToolCall = toolCall.copy(
            status = "SUCCESS",
            output = skipSummary
        )
        if (msg != null) {
            val updatedToolCalls = msg.toolCalls.map { if (it.stepIndex != null && it.stepIndex == toolCall.stepIndex) completedToolCall else it }
            updateAssistantMessage(
                msgId = messageId,
                content = msg.content,
                thought = msg.thoughtText ?: "",
                thoughtDuration = msg.thoughtDurationMs,
                toolCalls = updatedToolCalls,
                isStreaming = true
            )
        }

        backendScope.launch {
            val hubUrl = AuthPreferences.currentHubUrl
            if (stepIndex != null) {
                val actualResponses = if (responses.isNotEmpty()) {
                    responses
                } else {
                    val questionnaire = toolCall.questionnaire
                    questionnaire?.questions?.map { q ->
                        com.example.gemini.data.remote.dto.AskQuestionResponseItemDto(
                            question = q.prompt,
                            options = q.options.map { com.example.gemini.data.remote.dto.AskQuestionOptionDto(id = it.id, text = it.label) },
                            isMultiSelect = if (q.isMultiSelect) true else null,
                            skipped = true
                        )
                    } ?: emptyList()
                }

                val res = agyHubClient.handleAskQuestionInteraction(
                    cascadeId = conv.id,
                    stepIndex = stepIndex,
                    trajectoryId = toolCall.trajectoryId ?: "",
                    responses = actualResponses,
                    hubUrl = hubUrl
                )
                if (res.isFailure) {
                    Log.e("ChatViewModel", "handleAskQuestionInteraction (skip) failed: ${res.exceptionOrNull()}")
                }
            }
        }
    }

    fun cancelUserChoices(toolCall: com.example.gemini.domain.model.ToolCall, messageId: String) {
        val conv = _currentConversation.value ?: return
        val stepIndex = toolCall.stepIndex
        val hubUrl = AuthPreferences.currentHubUrl

        if (stepIndex != null) {
            trajectoryEngine.markStepResponded(stepIndex)
            trajectoryEngine.optimisticUpdateStepStatus(
                stepIndex = stepIndex,
                status = com.example.gemini.data.remote.dto.CortexStepStatuses.CANCELED,
                output = "Questionnaire cancelled by user."
            )
            _messages.value = trajectoryEngine.toChatMessages(conv.id)
        }

        val msg = _messages.value.find { it.id == messageId }
        val canceledToolCall = toolCall.copy(
            status = "CANCELED",
            output = "Cancelled by user."
        )
        if (msg != null) {
            val updatedToolCalls = msg.toolCalls.map { if (it.stepIndex != null && it.stepIndex == toolCall.stepIndex) canceledToolCall else it }
            updateAssistantMessage(
                msgId = messageId,
                content = msg.content,
                thought = msg.thoughtText ?: "",
                thoughtDuration = msg.thoughtDurationMs,
                toolCalls = updatedToolCalls,
                isStreaming = false
            )
        }

        backendScope.launch {
            val res = agyHubClient.cancelCascadeInvocation(conv.id, hubUrl)
            if (res.isFailure) {
                Log.e("ChatViewModel", "cancelCascadeInvocation failed: ${res.exceptionOrNull()}")
            }
        }
    }

    fun resetQuotaState() {
        _quotas.value = emptyList()
        _quotaSummary.value = null
    }

    /** Detaches on-screen presentation of AGY chat without killing background tasks. */
    fun detach() {
        // Do not cancel background stream jobs so ongoing chats (up to 50+) continue uninterrupted in the background
        activeStreamConversationId = null
        currentAssistantMsgId = null
        isPromptInFlight = false
        hasStartedRunning = false
        hasSeenTurnActivity = false
        _isStreaming.value = false
        _isLoadingConversation.value = false
        _bridgeStatusMessage.value = null
    }

    fun dispose() {
        activeStreamJobs.values.forEach { it.cancel() }
        activeStreamJobs.clear()
        streamingJob?.cancel()
    }
}
