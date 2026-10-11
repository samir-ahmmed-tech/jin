package com.example.gemini.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import com.example.gemini.data.security.SecureStorageManager

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

class AuthPreferences(private val context: Context) {

    companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = androidx.datastore.preferences.core.longPreferencesKey("expires_at")
        val PROJECT_ID = stringPreferencesKey("project_id")
        val SUBSCRIPTION_TIER = stringPreferencesKey("subscription_tier")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val TERMUX_SSH_HOST = stringPreferencesKey("termux_ssh_host")
        val TERMUX_SSH_PORT = stringPreferencesKey("termux_ssh_port")
        val TERMUX_SSH_USER = stringPreferencesKey("termux_ssh_user")
        val TERMUX_SSH_PASS = stringPreferencesKey("termux_ssh_pass")
        val IS_TERMINAL_TOOL_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_terminal_tool_enabled")
        val AUTO_EXECUTE_TERMINAL = androidx.datastore.preferences.core.booleanPreferencesKey("auto_execute_terminal")
        val IS_WEB_SEARCH_TOOL_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_web_search_tool_enabled")
        val IS_WEB_READER_TOOL_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_web_reader_tool_enabled")
        val IS_CHOICES_TOOL_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_choices_tool_enabled")
        val IS_FILE_TOOL_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_file_tool_enabled")
        val IS_AUTOMATION_TOOL_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_automation_tool_enabled")
        val IS_MATH_TOOL_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_math_tool_enabled")
        val IS_INTERACTIVE_UI_TOOL_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_interactive_ui_tool_enabled")
        val CONTEXT_WINDOW_LIMIT = androidx.datastore.preferences.core.intPreferencesKey("context_window_limit")
        val SUMMARY_MODEL_ID = stringPreferencesKey("summary_model_id")
        val ENABLED_MODELS = androidx.datastore.preferences.core.stringSetPreferencesKey("enabled_models")
        val IS_DEV_MODE_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_dev_mode_enabled")
        val CHAT_FONT_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("chat_font_scale")
        val APP_UI_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("app_ui_scale")
        val APP_CARD_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("app_card_scale")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val PERF_CPU_THREADS = androidx.datastore.preferences.core.intPreferencesKey("perf_cpu_threads")
        val PERF_CACHE_MB = androidx.datastore.preferences.core.intPreferencesKey("perf_cache_mb")
        val MULTI_AGENT_CONCURRENCY = androidx.datastore.preferences.core.intPreferencesKey("multi_agent_concurrency")
        val AUTO_FAILOVER_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("auto_failover_enabled")
        val WORKING_ANIMATION_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("working_animation_enabled")
        val CHAT_DIVIDERS_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("chat_dividers_enabled")
        val IS_LOCAL_TOOLS_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_local_tools_enabled")
        val IS_LOCAL_TOOLS_INSTALLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_local_tools_installed")
        val LOCAL_TOOLS_INSTALL_DATE = androidx.datastore.preferences.core.longPreferencesKey("local_tools_install_date")
        val LOCAL_TOOLS_VERSION = stringPreferencesKey("local_tools_version")
        val AGY_BRIDGE_HTTP_URL = stringPreferencesKey("agy_bridge_http_url")
        val AGY_HUB_URL = stringPreferencesKey("agy_hub_url")
        val USE_SSH_TERMINAL = androidx.datastore.preferences.core.booleanPreferencesKey("use_ssh_terminal")
        val PREFERRED_MODEL_ID = stringPreferencesKey("preferred_model_id")
        val PREFERRED_MODEL_KEY = stringPreferencesKey("preferred_model_key")
        val PREFERRED_MODEL_NAME = stringPreferencesKey("preferred_model_name")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode") // "SYSTEM", "DARK", "LIGHT"
        val TERMINAL_FONT_SIZE = androidx.datastore.preferences.core.intPreferencesKey("terminal_font_size")
        val TERMINAL_CURSOR_STYLE = stringPreferencesKey("terminal_cursor_style")
        val TERMINAL_BUFFER_SIZE = androidx.datastore.preferences.core.intPreferencesKey("terminal_buffer_size")
        val TERMINAL_THEME = stringPreferencesKey("terminal_theme")
        val COMMAND_AUTO_EXECUTION_POLICY = stringPreferencesKey("command_auto_execution_policy")
        val COMMAND_SANDBOX_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("command_sandbox_enabled")
        val REQUIRE_APPROVAL_FOR_FILE_EDITS = androidx.datastore.preferences.core.booleanPreferencesKey("require_approval_for_file_edits")
        val DEFAULT_APPROVAL_SCOPE = stringPreferencesKey("default_approval_scope")
        val GROUP_CHATS_BY_WORKSPACE = androidx.datastore.preferences.core.booleanPreferencesKey("group_chats_by_workspace")
        val IS_FLOATING_BUBBLE_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_floating_bubble_enabled")
        val AUTO_SHOW_FLOATING_BUBBLE_ON_MINIMIZE = androidx.datastore.preferences.core.booleanPreferencesKey("auto_show_floating_bubble_on_minimize")
        val IS_FLOATING_SWITCHER_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_floating_switcher_enabled")
        val FLOATING_SWITCHER_ORIENTATION = stringPreferencesKey("floating_switcher_orientation")
        val FLOATING_SWITCHER_ITEMS = stringPreferencesKey("floating_switcher_items")
        val FLOATING_SWITCHER_AUTO_COLLAPSE_SEC = androidx.datastore.preferences.core.intPreferencesKey("floating_switcher_auto_collapse_sec")
        val FLOATING_SWITCHER_POS_X = androidx.datastore.preferences.core.floatPreferencesKey("floating_switcher_pos_x")
        val FLOATING_SWITCHER_POS_Y = androidx.datastore.preferences.core.floatPreferencesKey("floating_switcher_pos_y")
        val IS_BROWSER_AUTOMATION_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_browser_automation_enabled")
        val IS_TERMINAL_AUTOMATION_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_terminal_automation_enabled")
        val IS_FLOW_AUTOMATION_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_flow_automation_enabled")
        val IS_FLOATING_DIAGNOSTICS_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_floating_diagnostics_enabled")
        val IS_NETWORK_INSPECTOR_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_network_inspector_enabled")
        val IS_FLOATING_NETWORK_INSPECTOR_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_floating_network_inspector_enabled")
        val SECURITY_TOKEN = stringPreferencesKey("security_token")
        val AGY_BRIDGE_BINARY_PATH = stringPreferencesKey("agy_bridge_binary_path")
        val AGY_BINARY_PATH = stringPreferencesKey("agy_binary_path")
        val CLAUDE_BINARY_PATH = stringPreferencesKey("claude_binary_path")
        val HAS_SEEN_TERMINAL_LAUNCHER_ONBOARDING = androidx.datastore.preferences.core.booleanPreferencesKey("has_seen_terminal_launcher_onboarding")
        val TERMINAL_LAUNCHER_STYLE = stringPreferencesKey("terminal_launcher_style") // "ANTITERM" or "TERMUX"
        val IS_TERMINAL_LAUNCHER_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("is_terminal_launcher_enabled")

        // Direct API Keys & Custom Proxy/Endpoints
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        val ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val CUSTOM_API_KEY = stringPreferencesKey("custom_api_key")
        val CUSTOM_API_BASE_URL = stringPreferencesKey("custom_api_base_url")
        val CUSTOM_API_MODEL_NAME = stringPreferencesKey("custom_api_model_name")
        val PERSONAL_INTELLIGENCE_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("personal_intelligence_enabled")
        val WEB_SEARCH_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("web_search_enabled")
        val MAX_IDLE_RUNTIME_MIN = androidx.datastore.preferences.core.intPreferencesKey("max_idle_runtime_min")
        val THERMAL_PROTECTION_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("thermal_protection_enabled")

        const val DEFAULT_BRIDGE_BINARY_PATH = "~/../usr/bin/agy_ide_bridge"
        const val DEFAULT_UBUNTU_BRIDGE_BINARY_PATH = "/antigem/bin/agy_ide_bridge"
        const val DEFAULT_AGY_BINARY_PATH = "~/.local/bin/agy"
        const val DEFAULT_UBUNTU_AGY_BINARY_PATH = "/root/.local/bin/agy"
        /** The app's Claude Code launcher (glibc loader); Claude's updater never touches it. */
        const val DEFAULT_CLAUDE_BINARY_PATH = "~/../usr/bin/claude"
        const val DEFAULT_UBUNTU_CLAUDE_BINARY_PATH = "/root/.local/bin/claude"

        const val TOKEN_PREFIX = "x-ag"
        const val TOKEN_SUFFIX = "_9qx"
        const val TOKEN_LENGTH = 12

        fun buildFramedHeader(token: String): String {
            val clean = token.trim().take(TOKEN_LENGTH).padEnd(TOKEN_LENGTH, '0')
            return "$TOKEN_PREFIX$clean$TOKEN_SUFFIX"
        }

        private const val TOKEN_CHARSET = "0123456789abcdefghijklmnopqrstuvwxyz"
        private val secureRandom = java.security.SecureRandom()

        fun generateRandomToken(): String {
            val sb = StringBuilder(TOKEN_LENGTH)
            for (i in 0 until TOKEN_LENGTH) {
                sb.append(TOKEN_CHARSET[secureRandom.nextInt(TOKEN_CHARSET.length)])
            }
            return sb.toString()
        }

        @Volatile
        var currentSecurityToken: String = ""

        val currentFramedHeader: String
            get() = buildFramedHeader(currentSecurityToken)

        // per app (see the build flavors): com.antigem uses other ports than com.termux so both can run together
        val DEFAULT_HUB_URL = "http://127.0.0.1:${com.example.gemini.BuildConfig.HUB_PORT}"
        val DEFAULT_BRIDGE_HTTP_URL = "http://127.0.0.1:${com.example.gemini.BuildConfig.BRIDGE_PORT}"

        /** The defaults every install used before ports became per app; a saved copy of them follows the new default. */
        private const val LEGACY_HUB_URL = "http://127.0.0.1:1235"
        private const val LEGACY_BRIDGE_HTTP_URL = "http://127.0.0.1:1234"

        fun migrateHubUrl(url: String): String = if (url.trimEnd('/') == LEGACY_HUB_URL) DEFAULT_HUB_URL else url
        fun migrateBridgeUrl(url: String): String = if (url.trimEnd('/') == LEGACY_BRIDGE_HTTP_URL) DEFAULT_BRIDGE_HTTP_URL else url

        @Volatile
        var currentHubUrl: String = DEFAULT_HUB_URL

        @Volatile
        var currentBridgeHttpUrl: String = DEFAULT_BRIDGE_HTTP_URL

        val currentBridgeWsUrl: String
            get() = toWsUrl(currentBridgeHttpUrl)

        fun toWsUrl(httpUrl: String): String = when {
            httpUrl.startsWith("https://") -> httpUrl.replaceFirst("https://", "wss://")
            httpUrl.startsWith("http://") -> httpUrl.replaceFirst("http://", "ws://")
            else -> "ws://$httpUrl"
        }
    }

    private val syncPrefs = context.getSharedPreferences("anti_gem_sync_prefs", Context.MODE_PRIVATE)

    init {
        val savedHub = syncPrefs.getString("agy_hub_url", null)
        val savedBridgeHttp = syncPrefs.getString("agy_bridge_http_url", null)
        if (!savedHub.isNullOrBlank()) currentHubUrl = migrateHubUrl(savedHub)
        if (!savedBridgeHttp.isNullOrBlank()) currentBridgeHttpUrl = migrateBridgeUrl(savedBridgeHttp)
        val inspectorOn = syncPrefs.getBoolean("is_network_inspector_enabled", false)
        val inspectorFloating = syncPrefs.getBoolean("is_floating_network_inspector_enabled", false)
        com.example.gemini.data.remote.inspector.NetworkInspectorManager.isEnabled = inspectorOn
        com.example.gemini.data.remote.inspector.NetworkInspectorManager.isFloatingBubbleEnabled = inspectorFloating

        var token = syncPrefs.getString("security_token", null)
        if (token.isNullOrBlank() || token.length != TOKEN_LENGTH) {
            token = generateRandomToken()
            syncPrefs.edit().putString("security_token", token).apply()
        }
        currentSecurityToken = token
    }

    val groupChatsByWorkspace: Flow<Boolean> = context.dataStore.data.map { it[GROUP_CHATS_BY_WORKSPACE] ?: false }

    val preferredModelId: Flow<String?> = context.dataStore.data.map { it[PREFERRED_MODEL_ID] }
    val preferredModelKey: Flow<String?> = context.dataStore.data.map { it[PREFERRED_MODEL_KEY] }
    val preferredModelName: Flow<String?> = context.dataStore.data.map { it[PREFERRED_MODEL_NAME] }
    val useSshTerminal: Flow<Boolean> = context.dataStore.data.map { 
        it[USE_SSH_TERMINAL] ?: false
    }
    val isLocalToolsEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_LOCAL_TOOLS_ENABLED] ?: false }
    val isLocalToolsInstalled: Flow<Boolean> = context.dataStore.data.map { it[IS_LOCAL_TOOLS_INSTALLED] ?: false }
    val localToolsInstallDate: Flow<Long?> = context.dataStore.data.map { it[LOCAL_TOOLS_INSTALL_DATE] }
    val localToolsVersion: Flow<String?> = context.dataStore.data.map { it[LOCAL_TOOLS_VERSION] }
    val isBrowserAutomationEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_BROWSER_AUTOMATION_ENABLED] ?: true }
    val isTerminalAutomationEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_TERMINAL_AUTOMATION_ENABLED] ?: true }
    val isFlowAutomationEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_FLOW_AUTOMATION_ENABLED] ?: false }

    fun isBrowserAutomationEnabledSync(): Boolean = syncPrefs.getBoolean("is_browser_automation_enabled", true)
    fun isTerminalAutomationEnabledSync(): Boolean = syncPrefs.getBoolean("is_terminal_automation_enabled", true)
    fun isFlowAutomationEnabledSync(): Boolean = syncPrefs.getBoolean("is_flow_automation_enabled", false)

    val agyBridgeHttpUrl: Flow<String> = context.dataStore.data.map { 
        val url = it[AGY_BRIDGE_HTTP_URL]?.let(::migrateBridgeUrl) ?: DEFAULT_BRIDGE_HTTP_URL
        currentBridgeHttpUrl = url
        url
    }
    val agyBridgeWsUrl: Flow<String> = agyBridgeHttpUrl.map { toWsUrl(it) }
    val agyHubUrl: Flow<String> = context.dataStore.data.map { 
        val url = it[AGY_HUB_URL]?.let(::migrateHubUrl) ?: DEFAULT_HUB_URL
        currentHubUrl = url
        url
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[ACCESS_TOKEN]?.let { t -> SecureStorageManager.decrypt(t) } }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[REFRESH_TOKEN]?.let { t -> SecureStorageManager.decrypt(t) } }
    val expiresAt: Flow<Long?> = context.dataStore.data.map { it[EXPIRES_AT] }
    val projectId: Flow<String?> = context.dataStore.data.map { it[PROJECT_ID] ?: "rising-fact-p41fc" }
    val subscriptionTier: Flow<String?> = context.dataStore.data.map { it[SUBSCRIPTION_TIER] ?: "pro" }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL]?.let { e -> SecureStorageManager.decrypt(e) } }

    fun getThemeModeSync(): String {
        val cached = syncPrefs.getString("app_theme_mode", null)
        if (cached != null) return cached

        // Fast fallback: parse datastore pb file directly if syncPrefs not yet populated
        try {
            val pbFile = java.io.File(context.filesDir, "datastore/auth_prefs.preferences_pb")
            if (pbFile.exists()) {
                val content = pbFile.readBytes().toString(Charsets.ISO_8859_1)
                val key = "app_theme_mode"
                val idx = content.indexOf(key)
                if (idx != -1) {
                    val sub = content.substring(idx + key.length, minOf(content.length, idx + key.length + 30))
                    val mode = when {
                        sub.contains("LIGHT") -> "LIGHT"
                        sub.contains("DARK") -> "DARK"
                        else -> "SYSTEM"
                    }
                    syncPrefs.edit().putString("app_theme_mode", mode).apply()
                    return mode
                }
            }
        } catch (_: Exception) {}

        return "SYSTEM"
    }

    val enabledModelIds: Flow<Set<String>?> = context.dataStore.data.map { it[ENABLED_MODELS] }
    val isDevModeEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_DEV_MODE_ENABLED] ?: false }
    val chatFontScale: Flow<Float> = context.dataStore.data.map { it[CHAT_FONT_SCALE] ?: 1.0f }
    fun getChatFontScaleSync(): Float = syncPrefs.getFloat("chat_font_scale", 1.0f)

    val appUiScale: Flow<Float> = context.dataStore.data.map { it[APP_UI_SCALE] ?: 0.85f }
    fun getAppUiScaleSync(): Float = syncPrefs.getFloat("app_ui_scale", 0.85f)

    val appCardScale: Flow<Float> = context.dataStore.data.map { it[APP_CARD_SCALE] ?: 1.0f }
    fun getAppCardScaleSync(): Float = syncPrefs.getFloat("app_card_scale", 1.0f)

    val performanceMode: Flow<String> = context.dataStore.data.map { it[PERFORMANCE_MODE] ?: "TURBO" }
    fun getPerformanceModeSync(): String = syncPrefs.getString("performance_mode", "TURBO") ?: "TURBO"

    val perfCpuThreads: Flow<Int> = context.dataStore.data.map { it[PERF_CPU_THREADS] ?: Runtime.getRuntime().availableProcessors().coerceAtLeast(1) }
    fun getPerfCpuThreadsSync(): Int = syncPrefs.getInt("perf_cpu_threads", Runtime.getRuntime().availableProcessors().coerceAtLeast(1))

    val perfCacheMb: Flow<Int> = context.dataStore.data.map { it[PERF_CACHE_MB] ?: 512 }
    fun getPerfCacheMbSync(): Int = syncPrefs.getInt("perf_cache_mb", 512)

    val multiAgentConcurrency: Flow<Int> = context.dataStore.data.map { it[MULTI_AGENT_CONCURRENCY] ?: 3 }
    fun getMultiAgentConcurrencySync(): Int = syncPrefs.getInt("multi_agent_concurrency", 3)

    val autoFailoverEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_FAILOVER_ENABLED] ?: true }
    fun getAutoFailoverEnabledSync(): Boolean = syncPrefs.getBoolean("auto_failover_enabled", true)

    /** The Claude Code-style "working" row under a running reply; off = only the "…" dots before the first output. */
    val workingAnimationEnabled: Flow<Boolean> = context.dataStore.data.map { it[WORKING_ANIMATION_ENABLED] ?: true }

    /** Wavy-line dividers in chats: a new day ("Sat, Oct 10") and model switches ("Switched to …"). */
    val chatDividersEnabled: Flow<Boolean> = context.dataStore.data.map { it[CHAT_DIVIDERS_ENABLED] ?: true }
    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        val mode = prefs[APP_THEME_MODE] ?: "SYSTEM"
        if (syncPrefs.getString("app_theme_mode", null) != mode) {
            syncPrefs.edit().putString("app_theme_mode", mode).apply()
        }
        mode
    }
    fun getTerminalFontSizeSync(): Int = syncPrefs.getInt("terminal_font_size", 14)
    fun getTerminalCursorStyleSync(): String = syncPrefs.getString("terminal_cursor_style", "BAR") ?: "BAR"
    fun getTerminalBufferSizeSync(): Int = syncPrefs.getInt("terminal_buffer_size", 20000)
    fun getTerminalThemeSync(): String = syncPrefs.getString("terminal_theme", "DEFAULT") ?: "DEFAULT"

    val terminalFontSize: Flow<Int> = context.dataStore.data.map { prefs ->
        val size = prefs[TERMINAL_FONT_SIZE] ?: 14
        if (syncPrefs.getInt("terminal_font_size", 14) != size) {
            syncPrefs.edit().putInt("terminal_font_size", size).apply()
        }
        size
    }
    val terminalCursorStyle: Flow<String> = context.dataStore.data.map { prefs ->
        val style = prefs[TERMINAL_CURSOR_STYLE] ?: "BAR"
        if (syncPrefs.getString("terminal_cursor_style", null) != style) {
            syncPrefs.edit().putString("terminal_cursor_style", style).apply()
        }
        style
    }
    val terminalBufferSize: Flow<Int> = context.dataStore.data.map { prefs ->
        val size = prefs[TERMINAL_BUFFER_SIZE] ?: 20000
        if (syncPrefs.getInt("terminal_buffer_size", 20000) != size) {
            syncPrefs.edit().putInt("terminal_buffer_size", size).apply()
        }
        size
    }
    val terminalTheme: Flow<String> = context.dataStore.data.map { prefs ->
        val th = prefs[TERMINAL_THEME] ?: "DEFAULT"
        if (syncPrefs.getString("terminal_theme", null) != th) {
            syncPrefs.edit().putString("terminal_theme", th).apply()
        }
        th
    }

    val termuxSshHost: Flow<String> = context.dataStore.data.map { it[TERMUX_SSH_HOST] ?: "127.0.0.1" }
    val termuxSshPort: Flow<Int> = context.dataStore.data.map { it[TERMUX_SSH_PORT]?.toIntOrNull() ?: 8022 }
    val termuxSshUser: Flow<String> = context.dataStore.data.map { it[TERMUX_SSH_USER] ?: "root" }
    val termuxSshPass: Flow<String> = context.dataStore.data.map { SecureStorageManager.decrypt(it[TERMUX_SSH_PASS] ?: "root") }
    val isTerminalToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_TERMINAL_TOOL_ENABLED] ?: false }
    val isAutoExecuteTerminal: Flow<Boolean> = context.dataStore.data.map { it[AUTO_EXECUTE_TERMINAL] ?: true }

    val isWebSearchToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_WEB_SEARCH_TOOL_ENABLED] ?: true }
    val isWebReaderToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_WEB_READER_TOOL_ENABLED] ?: true }
    val isChoicesToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_CHOICES_TOOL_ENABLED] ?: true }
    val isFileToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_FILE_TOOL_ENABLED] ?: false }
    val isAutomationToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_AUTOMATION_TOOL_ENABLED] ?: false }
    val isMathToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_MATH_TOOL_ENABLED] ?: true }
    val isInteractiveUiToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_INTERACTIVE_UI_TOOL_ENABLED] ?: false }
    val contextWindowLimit: Flow<Int> = context.dataStore.data.map { it[CONTEXT_WINDOW_LIMIT] ?: 10 }
    val summaryModelId: Flow<String> = context.dataStore.data.map { it[SUMMARY_MODEL_ID] ?: "always_ask" }

    val commandAutoExecutionPolicy: Flow<String> = context.dataStore.data.map {
        it[COMMAND_AUTO_EXECUTION_POLICY] ?: "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER"
    }
    val commandSandboxEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[COMMAND_SANDBOX_ENABLED] ?: false
    }
    val requireApprovalForFileEdits: Flow<Boolean> = context.dataStore.data.map {
        it[REQUIRE_APPROVAL_FOR_FILE_EDITS] ?: false
    }
    val defaultApprovalScope: Flow<String> = context.dataStore.data.map {
        it[DEFAULT_APPROVAL_SCOPE] ?: "PERMISSION_SCOPE_ONCE"
    }

    suspend fun setWebSearchToolEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_WEB_SEARCH_TOOL_ENABLED] = enabled
        }
    }

    suspend fun setWebReaderToolEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_WEB_READER_TOOL_ENABLED] = enabled
        }
    }

    suspend fun setChoicesToolEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_CHOICES_TOOL_ENABLED] = enabled
        }
    }

    suspend fun setFileToolEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_FILE_TOOL_ENABLED] = enabled
        }
    }

    suspend fun setAutomationToolEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_AUTOMATION_TOOL_ENABLED] = enabled
        }
    }

    suspend fun setMathToolEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_MATH_TOOL_ENABLED] = enabled
        }
    }

    suspend fun setInteractiveUiToolEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_INTERACTIVE_UI_TOOL_ENABLED] = enabled
        }
    }

    suspend fun setContextWindowLimit(limit: Int) {
        context.dataStore.edit { prefs ->
            prefs[CONTEXT_WINDOW_LIMIT] = limit
        }
    }

    suspend fun setSummaryModelId(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[SUMMARY_MODEL_ID] = modelId
        }
    }

    suspend fun setDevModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_DEV_MODE_ENABLED] = enabled
        }
    }

    suspend fun setBrowserAutomationEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("is_browser_automation_enabled", enabled).apply()
        context.dataStore.edit { prefs ->
            prefs[IS_BROWSER_AUTOMATION_ENABLED] = enabled
        }
    }

    suspend fun setTerminalAutomationEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("is_terminal_automation_enabled", enabled).apply()
        context.dataStore.edit { prefs ->
            prefs[IS_TERMINAL_AUTOMATION_ENABLED] = enabled
        }
    }

    suspend fun setFlowAutomationEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("is_flow_automation_enabled", enabled).apply()
        context.dataStore.edit { prefs ->
            prefs[IS_FLOW_AUTOMATION_ENABLED] = enabled
        }
    }

    suspend fun saveTermuxSshConfig(
        host: String,
        port: Int,
        user: String,
        pass: String,
        enabled: Boolean,
        autoExecute: Boolean
    ) {
        val encPass = SecureStorageManager.encrypt(pass)
        context.dataStore.edit { prefs ->
            prefs[TERMUX_SSH_HOST] = host
            prefs[TERMUX_SSH_PORT] = port.toString()
            prefs[TERMUX_SSH_USER] = user
            prefs[TERMUX_SSH_PASS] = encPass
            prefs[IS_TERMINAL_TOOL_ENABLED] = enabled
            prefs[AUTO_EXECUTE_TERMINAL] = autoExecute
        }
    }

    suspend fun setTerminalToolEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_TERMINAL_TOOL_ENABLED] = enabled
        }
    }

    suspend fun setUseSshTerminal(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[USE_SSH_TERMINAL] = enabled
        }
    }

    suspend fun saveSshSettings(host: String, port: Int, user: String, pass: String) {
        val encPass = SecureStorageManager.encrypt(pass)
        context.dataStore.edit { prefs ->
            prefs[TERMUX_SSH_HOST] = host
            prefs[TERMUX_SSH_PORT] = port.toString()
            prefs[TERMUX_SSH_USER] = user
            prefs[TERMUX_SSH_PASS] = encPass
        }
    }

    suspend fun setLocalToolsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_LOCAL_TOOLS_ENABLED] = enabled
        }
    }

    suspend fun setLocalToolsInstalled(installed: Boolean, version: String = "1.0.0") {
        context.dataStore.edit { prefs ->
            prefs[IS_LOCAL_TOOLS_INSTALLED] = installed
            if (installed) {
                prefs[LOCAL_TOOLS_INSTALL_DATE] = System.currentTimeMillis()
                prefs[LOCAL_TOOLS_VERSION] = version
            }
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String?, email: String? = null, expiresInSeconds: Long? = 3600) {
        val encAccess = SecureStorageManager.encrypt(accessToken)
        val encRefresh = refreshToken?.let { SecureStorageManager.encrypt(it) }
        val encEmail = email?.let { SecureStorageManager.encrypt(it) }

        val editor = syncPrefs.edit().putString("access_token", encAccess)
        if (encRefresh != null) editor.putString("refresh_token", encRefresh)
        if (encEmail != null) editor.putString("user_email", encEmail)
        editor.apply()
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = encAccess
            if (encRefresh != null) {
                prefs[REFRESH_TOKEN] = encRefresh
            }
            if (encEmail != null) {
                prefs[USER_EMAIL] = encEmail
            }
            if (expiresInSeconds != null) {
                prefs[EXPIRES_AT] = System.currentTimeMillis() + (expiresInSeconds * 1000)
            }
        }
    }

    fun getAccessTokenSync(): String = SecureStorageManager.decrypt(syncPrefs.getString("access_token", "") ?: "")
    fun getRefreshTokenSync(): String = SecureStorageManager.decrypt(syncPrefs.getString("refresh_token", "") ?: "")
    fun getUserEmailSync(): String = SecureStorageManager.decrypt(syncPrefs.getString("user_email", "") ?: "")

    val maxIdleRuntimeMin: Flow<Int> = context.dataStore.data.map { it[MAX_IDLE_RUNTIME_MIN] ?: 30 }
    fun getMaxIdleRuntimeMinSync(): Int = syncPrefs.getInt("max_idle_runtime_min", 30)
    suspend fun saveMaxIdleRuntimeMin(minutes: Int) {
        syncPrefs.edit().putInt("max_idle_runtime_min", minutes).apply()
        context.dataStore.edit { it[MAX_IDLE_RUNTIME_MIN] = minutes }
    }

    val thermalProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { it[THERMAL_PROTECTION_ENABLED] ?: true }
    fun getThermalProtectionEnabledSync(): Boolean = syncPrefs.getBoolean("thermal_protection_enabled", true)
    suspend fun saveThermalProtectionEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("thermal_protection_enabled", enabled).apply()
        context.dataStore.edit { it[THERMAL_PROTECTION_ENABLED] = enabled }
    }

    suspend fun saveProjectInfo(projectId: String, tier: String) {
        context.dataStore.edit { prefs ->
            prefs[PROJECT_ID] = projectId
            prefs[SUBSCRIPTION_TIER] = tier
        }
    }

    suspend fun saveEnabledModelIds(ids: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[ENABLED_MODELS] = ids
        }
    }


    suspend fun saveChatFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.5f, 2.0f)
        syncPrefs.edit().putFloat("chat_font_scale", clamped).apply()
        context.dataStore.edit { prefs ->
            prefs[CHAT_FONT_SCALE] = clamped
        }
    }

    suspend fun saveAppCardScale(scale: Float) {
        val clamped = scale.coerceIn(0.4f, 1.8f)
        syncPrefs.edit().putFloat("app_card_scale", clamped).apply()
        context.dataStore.edit { prefs ->
            prefs[APP_CARD_SCALE] = clamped
        }
    }

    suspend fun saveAppUiScale(scale: Float) {
        val clamped = scale.coerceIn(0.25f, 2.0f)
        syncPrefs.edit().putFloat("app_ui_scale", clamped).apply()
        context.dataStore.edit { prefs ->
            prefs[APP_UI_SCALE] = clamped
        }
    }

    suspend fun savePerformanceMode(mode: String) {
        syncPrefs.edit().putString("performance_mode", mode).apply()
        context.dataStore.edit { prefs ->
            prefs[PERFORMANCE_MODE] = mode
        }
    }

    suspend fun savePerfCpuThreads(threads: Int) {
        val maxThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val clamped = threads.coerceIn(1, maxThreads)
        syncPrefs.edit().putInt("perf_cpu_threads", clamped).apply()
        context.dataStore.edit { prefs ->
            prefs[PERF_CPU_THREADS] = clamped
        }
    }

    suspend fun savePerfCacheMb(mb: Int) {
        val clamped = mb.coerceIn(64, 4096)
        syncPrefs.edit().putInt("perf_cache_mb", clamped).apply()
        context.dataStore.edit { prefs ->
            prefs[PERF_CACHE_MB] = clamped
        }
    }

    suspend fun saveMultiAgentConcurrency(count: Int) {
        val clamped = count.coerceIn(1, 6)
        syncPrefs.edit().putInt("multi_agent_concurrency", clamped).apply()
        context.dataStore.edit { prefs ->
            prefs[MULTI_AGENT_CONCURRENCY] = clamped
        }
    }

    suspend fun saveAutoFailoverEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("auto_failover_enabled", enabled).apply()
        context.dataStore.edit { prefs ->
            prefs[AUTO_FAILOVER_ENABLED] = enabled
        }
    }

    suspend fun saveWorkingAnimationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[WORKING_ANIMATION_ENABLED] = enabled
        }
    }

    suspend fun saveChatDividersEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CHAT_DIVIDERS_ENABLED] = enabled
        }
    }

    suspend fun saveAgyHubUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotBlank()) {
            currentHubUrl = trimmed
            syncPrefs.edit().putString("agy_hub_url", trimmed).apply()
            context.dataStore.edit { prefs ->
                prefs[AGY_HUB_URL] = trimmed
            }
        }
    }

    suspend fun saveAgyBridgeHttpUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotBlank()) {
            currentBridgeHttpUrl = trimmed
            syncPrefs.edit().putString("agy_bridge_http_url", trimmed).apply()
            context.dataStore.edit { prefs ->
                prefs[AGY_BRIDGE_HTTP_URL] = trimmed
            }
        }
    }

    suspend fun saveThemeMode(mode: String) {
        syncPrefs.edit().putString("app_theme_mode", mode).commit()
        context.dataStore.edit { prefs ->
            prefs[APP_THEME_MODE] = mode
        }
    }

    suspend fun saveGroupChatsByWorkspace(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[GROUP_CHATS_BY_WORKSPACE] = enabled
        }
    }

    fun saveTerminalPreferencesSync(fontSize: Int, cursorStyle: String = "BAR", bufferSize: Int = 20000, theme: String = "DEFAULT") {
        syncPrefs.edit()
            .putInt("terminal_font_size", fontSize)
            .putString("terminal_cursor_style", cursorStyle)
            .putInt("terminal_buffer_size", bufferSize)
            .putString("terminal_theme", theme)
            .apply()
    }

    suspend fun saveTerminalPreferences(fontSize: Int, cursorStyle: String, bufferSize: Int, theme: String) {
        saveTerminalPreferencesSync(fontSize, cursorStyle, bufferSize, theme)
        context.dataStore.edit { prefs ->
            prefs[TERMINAL_FONT_SIZE] = fontSize
            prefs[TERMINAL_CURSOR_STYLE] = cursorStyle
            prefs[TERMINAL_BUFFER_SIZE] = bufferSize
            prefs[TERMINAL_THEME] = theme
        }
    }

    suspend fun savePreferredModel(modelId: String, modelKey: String = "", displayName: String = "") {
        context.dataStore.edit { prefs ->
            prefs[PREFERRED_MODEL_ID] = modelId
            if (modelKey.isNotBlank()) {
                prefs[PREFERRED_MODEL_KEY] = modelKey
            }
            if (displayName.isNotBlank()) {
                prefs[PREFERRED_MODEL_NAME] = displayName
            }
        }
    }

    suspend fun setCommandAutoExecutionPolicy(policy: String) {
        context.dataStore.edit { prefs ->
            prefs[COMMAND_AUTO_EXECUTION_POLICY] = policy
        }
    }

    suspend fun setCommandSandboxEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[COMMAND_SANDBOX_ENABLED] = enabled
        }
    }

    suspend fun setRequireApprovalForFileEdits(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[REQUIRE_APPROVAL_FOR_FILE_EDITS] = enabled
        }
    }

    suspend fun setDefaultApprovalScope(scope: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_APPROVAL_SCOPE] = scope
        }
    }

    val isFloatingBubbleEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[IS_FLOATING_BUBBLE_ENABLED] ?: true }

    fun getFloatingBubbleEnabledSync(): Boolean = syncPrefs.getBoolean("is_floating_bubble_enabled", true)

    suspend fun saveFloatingBubbleEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("is_floating_bubble_enabled", enabled).apply()
        context.dataStore.edit { it[IS_FLOATING_BUBBLE_ENABLED] = enabled }
    }

    val autoShowFloatingBubbleOnMinimize: Flow<Boolean> = context.dataStore.data
        .map { it[AUTO_SHOW_FLOATING_BUBBLE_ON_MINIMIZE] ?: true }

    fun getAutoShowFloatingBubbleOnMinimizeSync(): Boolean = syncPrefs.getBoolean("auto_show_floating_bubble_on_minimize", true)

    suspend fun saveAutoShowFloatingBubbleOnMinimize(enabled: Boolean) {
        syncPrefs.edit().putBoolean("auto_show_floating_bubble_on_minimize", enabled).apply()
        context.dataStore.edit { it[AUTO_SHOW_FLOATING_BUBBLE_ON_MINIMIZE] = enabled }
    }

    val isFloatingSwitcherEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[IS_FLOATING_SWITCHER_ENABLED] ?: true }

    fun getFloatingSwitcherEnabledSync(): Boolean = syncPrefs.getBoolean("is_floating_switcher_enabled", true)

    suspend fun saveFloatingSwitcherEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("is_floating_switcher_enabled", enabled).apply()
        context.dataStore.edit { it[IS_FLOATING_SWITCHER_ENABLED] = enabled }
    }

    val floatingSwitcherOrientation: Flow<String> = context.dataStore.data
        .map { it[FLOATING_SWITCHER_ORIENTATION] ?: "HORIZONTAL" }

    fun getFloatingSwitcherOrientationSync(): String = syncPrefs.getString("floating_switcher_orientation", "HORIZONTAL") ?: "HORIZONTAL"

    suspend fun saveFloatingSwitcherOrientation(orientation: String) {
        syncPrefs.edit().putString("floating_switcher_orientation", orientation).apply()
        context.dataStore.edit { it[FLOATING_SWITCHER_ORIENTATION] = orientation }
    }

    val floatingSwitcherItems: Flow<List<String>> = context.dataStore.data
        .map {
            val raw = it[FLOATING_SWITCHER_ITEMS] ?: "chat,ide,terminal,browser,translator,pc_tools"
            raw.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
        }

    fun getFloatingSwitcherItemsSync(): List<String> {
        val raw = syncPrefs.getString("floating_switcher_items", "chat,ide,terminal,browser,translator,pc_tools") ?: "chat,ide,terminal,browser,translator,pc_tools"
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    suspend fun saveFloatingSwitcherItems(items: List<String>) {
        val raw = items.joinToString(",")
        syncPrefs.edit().putString("floating_switcher_items", raw).apply()
        context.dataStore.edit { it[FLOATING_SWITCHER_ITEMS] = raw }
    }

    val floatingSwitcherAutoCollapseSec: Flow<Int> = context.dataStore.data
        .map { it[FLOATING_SWITCHER_AUTO_COLLAPSE_SEC] ?: 0 }

    fun getFloatingSwitcherAutoCollapseSecSync(): Int = syncPrefs.getInt("floating_switcher_auto_collapse_sec", 0)

    suspend fun saveFloatingSwitcherAutoCollapseSec(sec: Int) {
        syncPrefs.edit().putInt("floating_switcher_auto_collapse_sec", sec).apply()
        context.dataStore.edit { it[FLOATING_SWITCHER_AUTO_COLLAPSE_SEC] = sec }
    }

    val floatingSwitcherPosX: Flow<Float> = context.dataStore.data
        .map { it[FLOATING_SWITCHER_POS_X] ?: 0.96f }

    val floatingSwitcherPosY: Flow<Float> = context.dataStore.data
        .map { it[FLOATING_SWITCHER_POS_Y] ?: 0.22f }

    fun getFloatingSwitcherPositionSync(): Pair<Float, Float> {
        val x = syncPrefs.getFloat("floating_switcher_pos_x", 0.96f)
        val y = syncPrefs.getFloat("floating_switcher_pos_y", 0.22f)
        return Pair(x, y)
    }

    fun saveFloatingSwitcherPositionSync(x: Float, y: Float) {
        syncPrefs.edit()
            .putFloat("floating_switcher_pos_x", x)
            .putFloat("floating_switcher_pos_y", y)
            .apply()
    }

    suspend fun saveFloatingSwitcherPosition(x: Float, y: Float) {
        saveFloatingSwitcherPositionSync(x, y)
        context.dataStore.edit {
            it[FLOATING_SWITCHER_POS_X] = x
            it[FLOATING_SWITCHER_POS_Y] = y
        }
    }

    val isFloatingDiagnosticsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[IS_FLOATING_DIAGNOSTICS_ENABLED] ?: false }

    fun getFloatingDiagnosticsEnabledSync(): Boolean = syncPrefs.getBoolean("is_floating_diagnostics_enabled", false)

    suspend fun saveFloatingDiagnosticsEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("is_floating_diagnostics_enabled", enabled).apply()
        context.dataStore.edit { it[IS_FLOATING_DIAGNOSTICS_ENABLED] = enabled }
    }

    val isNetworkInspectorEnabled: Flow<Boolean> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "is_network_inspector_enabled") {
                trySend(getNetworkInspectorEnabledSync())
            }
        }
        syncPrefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getNetworkInspectorEnabledSync())
        awaitClose { syncPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getNetworkInspectorEnabledSync(): Boolean = syncPrefs.getBoolean("is_network_inspector_enabled", false)

    fun saveNetworkInspectorEnabledSync(enabled: Boolean) {
        com.example.gemini.data.remote.inspector.NetworkInspectorManager.isEnabled = enabled
        syncPrefs.edit().putBoolean("is_network_inspector_enabled", enabled).commit()
    }

    suspend fun saveNetworkInspectorEnabled(enabled: Boolean) {
        saveNetworkInspectorEnabledSync(enabled)
        context.dataStore.edit { it[IS_NETWORK_INSPECTOR_ENABLED] = enabled }
    }

    val isFloatingNetworkInspectorEnabled: Flow<Boolean> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "is_floating_network_inspector_enabled") {
                trySend(getFloatingNetworkInspectorEnabledSync())
            }
        }
        syncPrefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getFloatingNetworkInspectorEnabledSync())
        awaitClose { syncPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getFloatingNetworkInspectorEnabledSync(): Boolean = syncPrefs.getBoolean("is_floating_network_inspector_enabled", false)

    fun saveFloatingNetworkInspectorEnabledSync(enabled: Boolean) {
        com.example.gemini.data.remote.inspector.NetworkInspectorManager.isFloatingBubbleEnabled = enabled
        syncPrefs.edit().putBoolean("is_floating_network_inspector_enabled", enabled).commit()
    }

    suspend fun saveFloatingNetworkInspectorEnabled(enabled: Boolean) {
        saveFloatingNetworkInspectorEnabledSync(enabled)
        context.dataStore.edit { it[IS_FLOATING_NETWORK_INSPECTOR_ENABLED] = enabled }
    }

    val securityToken: Flow<String> = context.dataStore.data.map {
        it[SECURITY_TOKEN] ?: getSecurityTokenSync()
    }

    fun getSecurityTokenSync(): String {
        var token = syncPrefs.getString("security_token", null)
        if (token.isNullOrBlank() || token.length != TOKEN_LENGTH) {
            token = generateRandomToken()
            syncPrefs.edit().putString("security_token", token).apply()
        }
        currentSecurityToken = token
        return token
    }

    suspend fun saveSecurityToken(token: String) {
        val clean = token.trim()
        val validToken = if (clean.length == TOKEN_LENGTH) clean else generateRandomToken()
        syncPrefs.edit().putString("security_token", validToken).apply()
        currentSecurityToken = validToken
        context.dataStore.edit { it[SECURITY_TOKEN] = validToken }
    }

    fun getDefaultBridgeBinaryPath(): String =
        if (context.packageName == "com.termux") DEFAULT_BRIDGE_BINARY_PATH else DEFAULT_UBUNTU_BRIDGE_BINARY_PATH

    fun getDefaultAgyBinaryPath(): String =
        if (context.packageName == "com.termux") DEFAULT_AGY_BINARY_PATH else DEFAULT_UBUNTU_AGY_BINARY_PATH

    val agyBridgeBinaryPath: Flow<String> = context.dataStore.data.map {
        it[AGY_BRIDGE_BINARY_PATH] ?: getAgyBridgeBinaryPathSync()
    }

    fun getAgyBridgeBinaryPathSync(): String =
        syncPrefs.getString("agy_bridge_binary_path", getDefaultBridgeBinaryPath()) ?: getDefaultBridgeBinaryPath()

    suspend fun saveAgyBridgeBinaryPath(path: String) {
        val trimmed = path.trim().ifBlank { getDefaultBridgeBinaryPath() }
        syncPrefs.edit().putString("agy_bridge_binary_path", trimmed).apply()
        context.dataStore.edit { it[AGY_BRIDGE_BINARY_PATH] = trimmed }
    }

    val agyBinaryPath: Flow<String> = context.dataStore.data.map {
        it[AGY_BINARY_PATH] ?: getAgyBinaryPathSync()
    }

    fun getAgyBinaryPathSync(): String =
        syncPrefs.getString("agy_binary_path", getDefaultAgyBinaryPath()) ?: getDefaultAgyBinaryPath()

    suspend fun saveAgyBinaryPath(path: String) {
        val trimmed = path.trim().ifBlank { getDefaultAgyBinaryPath() }
        syncPrefs.edit().putString("agy_binary_path", trimmed).apply()
        context.dataStore.edit { it[AGY_BINARY_PATH] = trimmed }
    }

    fun getDefaultClaudeBinaryPath(): String =
        if (context.packageName == "com.termux") DEFAULT_CLAUDE_BINARY_PATH else DEFAULT_UBUNTU_CLAUDE_BINARY_PATH

    fun getClaudeBinaryPathSync(): String =
        syncPrefs.getString("claude_binary_path", getDefaultClaudeBinaryPath()) ?: getDefaultClaudeBinaryPath()

    suspend fun saveClaudeBinaryPath(path: String) {
        val trimmed = path.trim().ifBlank { getDefaultClaudeBinaryPath() }
        syncPrefs.edit().putString("claude_binary_path", trimmed).apply()
        context.dataStore.edit { it[CLAUDE_BINARY_PATH] = trimmed }
    }

    val hasSeenTerminalLauncherOnboarding: Flow<Boolean> = context.dataStore.data.map {
        it[HAS_SEEN_TERMINAL_LAUNCHER_ONBOARDING] ?: false
    }

    suspend fun setHasSeenTerminalLauncherOnboarding(seen: Boolean) {
        context.dataStore.edit { it[HAS_SEEN_TERMINAL_LAUNCHER_ONBOARDING] = seen }
    }

    val terminalLauncherStyle: Flow<String> = context.dataStore.data.map {
        it[TERMINAL_LAUNCHER_STYLE] ?: "ANTITERM"
    }

    suspend fun setTerminalLauncherStyle(style: String) {
        context.dataStore.edit { it[TERMINAL_LAUNCHER_STYLE] = style }
    }

    val isTerminalLauncherEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[IS_TERMINAL_LAUNCHER_ENABLED] ?: true
    }

    suspend fun setTerminalLauncherEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IS_TERMINAL_LAUNCHER_ENABLED] = enabled }
    }

    // Direct API Keys & Custom Proxy Accessors (100% AES-256-GCM On-Device Encrypted)
    val openAiApiKey: Flow<String> = context.dataStore.data.map { SecureStorageManager.decrypt(it[OPENAI_API_KEY] ?: getOpenAiApiKeySync()) }
    fun getOpenAiApiKeySync(): String = SecureStorageManager.decrypt(syncPrefs.getString("openai_api_key", "") ?: "")
    suspend fun saveOpenAiApiKey(key: String) {
        val trimmed = key.trim()
        val encrypted = SecureStorageManager.encrypt(trimmed)
        syncPrefs.edit().putString("openai_api_key", encrypted).apply()
        context.dataStore.edit { it[OPENAI_API_KEY] = encrypted }
    }

    val deepSeekApiKey: Flow<String> = context.dataStore.data.map { SecureStorageManager.decrypt(it[DEEPSEEK_API_KEY] ?: getDeepSeekApiKeySync()) }
    fun getDeepSeekApiKeySync(): String = SecureStorageManager.decrypt(syncPrefs.getString("deepseek_api_key", "") ?: "")
    suspend fun saveDeepSeekApiKey(key: String) {
        val trimmed = key.trim()
        val encrypted = SecureStorageManager.encrypt(trimmed)
        syncPrefs.edit().putString("deepseek_api_key", encrypted).apply()
        context.dataStore.edit { it[DEEPSEEK_API_KEY] = encrypted }
    }

    val anthropicApiKey: Flow<String> = context.dataStore.data.map { SecureStorageManager.decrypt(it[ANTHROPIC_API_KEY] ?: getAnthropicApiKeySync()) }
    fun getAnthropicApiKeySync(): String = SecureStorageManager.decrypt(syncPrefs.getString("anthropic_api_key", "") ?: "")
    suspend fun saveAnthropicApiKey(key: String) {
        val trimmed = key.trim()
        val encrypted = SecureStorageManager.encrypt(trimmed)
        syncPrefs.edit().putString("anthropic_api_key", encrypted).apply()
        context.dataStore.edit { it[ANTHROPIC_API_KEY] = encrypted }
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { SecureStorageManager.decrypt(it[GEMINI_API_KEY] ?: getGeminiApiKeySync()) }
    fun getGeminiApiKeySync(): String = SecureStorageManager.decrypt(syncPrefs.getString("gemini_api_key", "") ?: "")
    suspend fun saveGeminiApiKey(key: String) {
        val trimmed = key.trim()
        val encrypted = SecureStorageManager.encrypt(trimmed)
        syncPrefs.edit().putString("gemini_api_key", encrypted).apply()
        context.dataStore.edit { it[GEMINI_API_KEY] = encrypted }
    }

    val openRouterApiKey: Flow<String> = context.dataStore.data.map { SecureStorageManager.decrypt(it[OPENROUTER_API_KEY] ?: getOpenRouterApiKeySync()) }
    fun getOpenRouterApiKeySync(): String = SecureStorageManager.decrypt(syncPrefs.getString("openrouter_api_key", "") ?: "")
    suspend fun saveOpenRouterApiKey(key: String) {
        val trimmed = key.trim()
        val encrypted = SecureStorageManager.encrypt(trimmed)
        syncPrefs.edit().putString("openrouter_api_key", encrypted).apply()
        context.dataStore.edit { it[OPENROUTER_API_KEY] = encrypted }
    }

    val customApiKey: Flow<String> = context.dataStore.data.map { SecureStorageManager.decrypt(it[CUSTOM_API_KEY] ?: getCustomApiKeySync()) }
    fun getCustomApiKeySync(): String = SecureStorageManager.decrypt(syncPrefs.getString("custom_api_key", "") ?: "")
    suspend fun saveCustomApiKey(key: String) {
        val trimmed = key.trim()
        val encrypted = SecureStorageManager.encrypt(trimmed)
        syncPrefs.edit().putString("custom_api_key", encrypted).apply()
        context.dataStore.edit { it[CUSTOM_API_KEY] = encrypted }
    }

    val customApiBaseUrl: Flow<String> = context.dataStore.data.map { it[CUSTOM_API_BASE_URL] ?: getCustomApiBaseUrlSync() }
    fun getCustomApiBaseUrlSync(): String = syncPrefs.getString("custom_api_base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
    suspend fun saveCustomApiBaseUrl(url: String) {
        val trimmed = url.trim().ifBlank { "https://api.openai.com/v1" }
        syncPrefs.edit().putString("custom_api_base_url", trimmed).apply()
        context.dataStore.edit { it[CUSTOM_API_BASE_URL] = trimmed }
    }

    val customApiModelName: Flow<String> = context.dataStore.data.map { it[CUSTOM_API_MODEL_NAME] ?: getCustomApiModelNameSync() }
    fun getCustomApiModelNameSync(): String = syncPrefs.getString("custom_api_model_name", "gpt-4o") ?: "gpt-4o"
    suspend fun saveCustomApiModelName(name: String) {
        val trimmed = name.trim().ifBlank { "gpt-4o" }
        syncPrefs.edit().putString("custom_api_model_name", trimmed).apply()
        context.dataStore.edit { it[CUSTOM_API_MODEL_NAME] = trimmed }
    }

    val personalIntelligenceEnabled: Flow<Boolean> = context.dataStore.data.map { it[PERSONAL_INTELLIGENCE_ENABLED] ?: getPersonalIntelligenceEnabledSync() }
    fun getPersonalIntelligenceEnabledSync(): Boolean = syncPrefs.getBoolean("personal_intelligence_enabled", true)
    suspend fun setPersonalIntelligenceEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("personal_intelligence_enabled", enabled).apply()
        context.dataStore.edit { it[PERSONAL_INTELLIGENCE_ENABLED] = enabled }
    }

    val webSearchEnabled: Flow<Boolean> = context.dataStore.data.map { it[WEB_SEARCH_ENABLED] ?: getWebSearchEnabledSync() }
    fun getWebSearchEnabledSync(): Boolean = syncPrefs.getBoolean("web_search_enabled", false)
    suspend fun setWebSearchEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("web_search_enabled", enabled).apply()
        context.dataStore.edit { it[WEB_SEARCH_ENABLED] = enabled }
    }

    suspend fun clearAuth() {
        syncPrefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("user_email")
            .apply()
        context.dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
            prefs.remove(EXPIRES_AT)
            prefs.remove(USER_EMAIL)
        }
    }
}
