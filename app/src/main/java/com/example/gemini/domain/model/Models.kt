package com.example.gemini.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL,
    SYSTEM,
    SUMMARY
}

enum class ToolType {
    BASH,
    VIEW_FILE,
    EDIT_FILE,
    LIST_DIR,
    GREP_SEARCH,
    FIND,
    SEARCH_WEB,
    READ_URL,
    GENERATE_IMAGE,
    MCP,
    ASK_CHOICE,
    MATH,
    SYSTEM_NOTIFICATION,
    UNKNOWN
}

@Serializable
data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "bash",
    val toolType: ToolType = ToolType.BASH,
    val command: String,
    val status: String = "RUNNING", // RUNNING, SUCCESS, FAILED, TERMINATED
    val output: String = "",
    val exitCode: Int? = null,
    val durationMs: Long? = null,
    val stepIndex: Int? = null,
    val trajectoryId: String? = null,
    val interactionType: String? = null,
    val questionnaire: ChoiceQuestionnaire? = null
)

@Serializable
data class TokenUsage(
    val promptTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val cacheCreationTokens: Int = 0,
    val totalTokens: Int = 0,
    val durationMs: Long = 0L,
    val isEstimated: Boolean = false,
    val costUsd: Double? = null,
    /** The model that produced the reply, when the agent reports it (Claude). */
    val model: String? = null
)

@Serializable
data class ArtifactSnapshot(
    val name: String = "",
    val absoluteUri: String = "",
    val lastEdited: String = "",
    val summary: String = "",
    val requestFeedback: Boolean = false,
    val userFacing: Boolean = false
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val thoughtText: String? = null,
    val thoughtDurationMs: Long? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val isStreaming: Boolean = false,
    val tokenUsage: TokenUsage? = null,
    val rawPayload: String? = null,
    val rawContent: String? = null,
    val contextSummary: String? = null,
    val stepIndex: Int? = null,
    val attachments: List<ChatAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    /** The model that wrote this reply, as the agent reports it (null when unknown). */
    val modelId: String? = null
)

@Serializable
data class ChatAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val isImage: Boolean = false,
    val isAudio: Boolean = false,
    val isVideo: Boolean = false,
    val localUri: String? = null,
    val size: Long = 0L,
    val url: String? = null,
    val durationSeconds: Int = 0,
    val mimeType: String? = null,
    val base64: String? = null
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val modelId: String = "",
    val sessionId: String = UUID.randomUUID().toString(),
    val summary: String? = null,
    val customSystemPrompt: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isRunning: Boolean = false,
    val notFullyIdle: Boolean = false,
    val hasActivity: Boolean = false,
    val runStatus: String = "CASCADE_RUN_STATUS_IDLE",
    val stepCount: Int = 0,
    val workspaceUri: String = "",
    val parentConversationId: String? = null,
    val subagentRole: String? = null,
    val subagentTypeName: String? = null,
    val nestingDepth: Int = 0,
    val agent: AgentKind = AgentKind.AGY
)

enum class ModelFamily {
    ANTIGRAVITY,
    OPENAI,
    DEEPSEEK,
    CLAUDE,
    GEMINI,
    OTHER
}

@Serializable
data class AiModel(
    val id: String,
    val displayName: String,
    val family: ModelFamily = when {
        id.contains("antigravity", ignoreCase = true) || displayName.contains("antigravity", ignoreCase = true) -> ModelFamily.ANTIGRAVITY
        id.contains("codex", ignoreCase = true) || id.contains("gpt", ignoreCase = true) || id.contains("openai", ignoreCase = true) || displayName.contains("codex", ignoreCase = true) || displayName.contains("gpt", ignoreCase = true) -> ModelFamily.OPENAI
        id.contains("deepseek", ignoreCase = true) || id.contains("hermes", ignoreCase = true) || displayName.contains("deepseek", ignoreCase = true) || displayName.contains("hermes", ignoreCase = true) -> ModelFamily.DEEPSEEK
        id.contains("claude", ignoreCase = true) || displayName.contains("claude", ignoreCase = true) -> ModelFamily.CLAUDE
        id.contains("gemini", ignoreCase = true) || displayName.contains("gemini", ignoreCase = true) -> ModelFamily.GEMINI
        else -> ModelFamily.OTHER
    },
    val supportsThinking: Boolean = id.contains("thinking", ignoreCase = true) || id.contains("flash", ignoreCase = true) || id.contains("pro", ignoreCase = true) || id.contains("high", ignoreCase = true) || id.contains("medium", ignoreCase = true) || id.contains("low", ignoreCase = true) || id.contains("r1", ignoreCase = true),
    val description: String = "",
    val isDefault: Boolean = false,
    val key: String = "",
    val baseName: String = "",
    val tier: String? = null
) {
    companion object {
        val DEFAULT_MODELS = listOf(
            AiModel("antigravity-2.0-flash", "Antigravity 2.0 Flash Thinking", ModelFamily.ANTIGRAVITY, true, "Next-gen Antigravity reasoning engine with live step-by-step thinking", isDefault = true),
            AiModel("antigravity-2.0-pro", "Antigravity 2.0 Pro", ModelFamily.ANTIGRAVITY, true, "Flagship Antigravity 2.0 model with advanced agentic workspace tool execution"),
            AiModel("openai-codex", "OpenAI Codex Coder", ModelFamily.OPENAI, false, "OpenAI high-speed code synthesis, terminal operations and automated debugging"),
            AiModel("gpt-4o", "ChatGPT 4o", ModelFamily.OPENAI, false, "Omni-modal reasoning and software architecture model"),
            AiModel("gpt-4o-mini", "ChatGPT 4o Mini", ModelFamily.OPENAI, false, "Lightweight ChatGPT model optimized for fast responses"),
            AiModel("deepseek-hermes", "DeepSeek Hermes", ModelFamily.DEEPSEEK, true, "Fine-tuned DeepSeek model for complex algorithmic reasoning and terminal tasks"),
            AiModel("deepseek-chat", "DeepSeek V3", ModelFamily.DEEPSEEK, false, "DeepSeek 671B MoE architecture with ultra-high coding capability"),
            AiModel("deepseek-coder", "DeepSeek Coder V2", ModelFamily.DEEPSEEK, false, "Dedicated coding LLM supporting 300+ programming languages"),
            AiModel("claude-3-7-sonnet", "Claude 3.7 Sonnet (Thinking)", ModelFamily.CLAUDE, true, "Anthropic hybrid reasoning model for complex agentic workflows"),
            AiModel("claude-3-5-sonnet", "Claude 3.5 Sonnet", ModelFamily.CLAUDE, false, "Anthropic top-tier code intelligence and refactoring engine"),
            AiModel("gemini-argon", "Gemini Argon (Advanced / Ultra Tier)", ModelFamily.GEMINI, true, "Google frontier reasoning model for Pro/Ultra subscription tier with deep analysis & massive multimodal context", tier = "Pro/Ultra Subscription"),
            AiModel("gemini-2.5-pro", "Gemini 2.5 Pro", ModelFamily.GEMINI, true, "Google multimodal frontier model with 2M token context"),
            AiModel("gemini-2.5-flash", "Gemini 2.5 Flash", ModelFamily.GEMINI, true, "Google high-speed low-latency multimodal reasoning model"),
            AiModel("qwen/qwen-2.5-coder-32b-instruct", "Qwen 2.5 Coder 32B (OpenRouter)", ModelFamily.OTHER, false, "Alibaba Cloud flagship code model for polyglot software engineering"),
            AiModel("mistralai/codestral-2501", "Mistral Codestral 25B", ModelFamily.OTHER, false, "Mistral AI cutting-edge open weights coding model"),
            AiModel("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (Free OpenRouter)", ModelFamily.OTHER, false, "Meta flagship open weights model on free tier"),
            AiModel("deepseek/deepseek-r1:free", "DeepSeek R1 (Free OpenRouter)", ModelFamily.OTHER, true, "DeepSeek R1 full chain-of-thought reasoning on free tier"),
            AiModel("google/gemini-2.0-flash-exp:free", "Gemini 2.0 Flash (Free OpenRouter)", ModelFamily.OTHER, true, "Google high-speed low-latency multimodal on free tier"),
            AiModel("opencode-zen-deepseek", "OpenCode DeepSeek V3", ModelFamily.OTHER, false, "Direct high-speed coding gateway via OpenCode Zen"),
            AiModel("opencode-zen-coder", "OpenCode Zen Coder", ModelFamily.OTHER, false, "Optimized programming model for polyglot software engineering"),
            AiModel("opencode-zen-r1", "OpenCode DeepSeek R1 Hermes", ModelFamily.OTHER, true, "Deep reasoning and chain-of-thought MoE architecture"),
            AiModel("custom-api", "Custom 3rd-Party / GitHub API", ModelFamily.OTHER, false, "Connect to any custom base URL (GitHub Free LLM APIs, Groq, Ollama, Together, DeepSeek)")
        )

        fun fromApi(id: String, displayName: String?, description: String? = null): AiModel {
            val name = displayName?.takeIf { it.isNotBlank() } ?: formatModelName(id)
            val family = when {
                id.contains("antigravity", ignoreCase = true) || name.contains("antigravity", ignoreCase = true) -> ModelFamily.ANTIGRAVITY
                id.contains("codex", ignoreCase = true) || id.contains("gpt", ignoreCase = true) || id.contains("openai", ignoreCase = true) || name.contains("codex", ignoreCase = true) || name.contains("gpt", ignoreCase = true) -> ModelFamily.OPENAI
                id.contains("deepseek", ignoreCase = true) || id.contains("hermes", ignoreCase = true) || name.contains("deepseek", ignoreCase = true) || name.contains("hermes", ignoreCase = true) -> ModelFamily.DEEPSEEK
                id.contains("claude", ignoreCase = true) || name.contains("claude", ignoreCase = true) -> ModelFamily.CLAUDE
                id.contains("gemini", ignoreCase = true) || id.contains("argon", ignoreCase = true) || name.contains("gemini", ignoreCase = true) || name.contains("argon", ignoreCase = true) -> ModelFamily.GEMINI
                else -> ModelFamily.OTHER
            }
            val thinking = id.contains("thinking", ignoreCase = true) || id.contains("flash", ignoreCase = true) || id.contains("pro", ignoreCase = true) || id.contains("argon", ignoreCase = true) || id.contains("high", ignoreCase = true) || id.contains("medium", ignoreCase = true) || id.contains("low", ignoreCase = true) || id.contains("r1", ignoreCase = true)
            val desc = description ?: when (family) {
                ModelFamily.ANTIGRAVITY -> "Google Antigravity 2.0 Engine"
                ModelFamily.OPENAI -> "OpenAI Codex & ChatGPT Model"
                ModelFamily.DEEPSEEK -> "DeepSeek AI Open Intelligence"
                ModelFamily.CLAUDE -> "Anthropic Claude via JIN"
                ModelFamily.GEMINI -> if (id.contains("argon", ignoreCase = true)) "Google Gemini Argon (Pro/Ultra Tier)" else "Google Gemini via Antigravity"
                ModelFamily.OTHER -> "Custom / 3rd-Party API Provider"
            }
            var baseName = name
            var tier: String? = if (id.contains("argon", ignoreCase = true)) "Pro/Ultra Subscription" else null
            val tierMatch = Regex("^(.*?)\\s*\\((High|Medium|Low|Med|Thinking)\\)$", RegexOption.IGNORE_CASE).find(name)
            if (tierMatch != null) {
                baseName = tierMatch.groupValues[1].trim()
                tier = tierMatch.groupValues[2].replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            return AiModel(
                id = id,
                displayName = name,
                family = family,
                supportsThinking = thinking,
                description = desc,
                key = id,
                baseName = baseName,
                tier = tier
            )
        }

        private fun formatModelName(id: String): String {
            if (id.startsWith("MODEL_", ignoreCase = true)) {
                return "Gemini"
            }
            return id.split("-", "_").joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        fun findInList(models: List<AiModel>, id: String, fallbackDisplayName: String? = null): AiModel {
            val found = models.find { it.id == id || (it.key.isNotBlank() && it.key.equals(id, ignoreCase = true)) }
            if (found != null) return found
            if (!fallbackDisplayName.isNullOrBlank()) {
                return fromApi(id.ifBlank { "gemini" }, fallbackDisplayName)
            }
            if (id.isNotBlank() && !id.startsWith("MODEL_", ignoreCase = true)) {
                return fromApi(id, null)
            }
            return if (models.isNotEmpty()) models.first() else AiModel("gemini", "Gemini")
        }
    }
}

enum class ThinkingLevel(val label: String, val tokens: Int, val description: String) {
    OFF("Off", 0, "0 tokens • Direct response (no reasoning)"),
    LOW("Low", 1024, "1,024 tokens (~4,000 chars) • Quick logic"),
    MEDIUM("Med", 4096, "4,096 tokens (~16,000 chars) • Standard"),
    HIGH("High", 8192, "8,192 tokens (~32,000 chars) • Deep thinking"),
    CUSTOM("Custom", 0, "Custom token budget")
}

data class ThinkingPreference(
    val level: ThinkingLevel = ThinkingLevel.MEDIUM,
    val customTokens: Int = 4096
) {
    val activeTokens: Int
        get() = if (level == ThinkingLevel.CUSTOM) customTokens else level.tokens

    val isEnabled: Boolean
        get() = level != ThinkingLevel.OFF && activeTokens > 0

    val displaySummary: String
        get() = when (level) {
            ThinkingLevel.OFF -> "Thinking: Off"
            ThinkingLevel.LOW -> "Thinking: Low (1K)"
            ThinkingLevel.MEDIUM -> "Thinking: Med (4K)"
            ThinkingLevel.HIGH -> "Thinking: High (8K)"
            ThinkingLevel.CUSTOM -> "Thinking: Custom (${activeTokens})"
        }
}

@Serializable
data class QuotaWindowInfo(
    val window: String = "5h",
    val displayName: String = "",
    val remainingFraction: Float = 1.0f,
    val remainingPct: String = "100.0%",
    val usedPct: String = "0.0%",
    val resetTime: String? = null,
    val countdown: String = "",
    val description: String = ""
)

@Serializable
data class ModelQuotaGroup(
    val groupId: String = "",
    val groupName: String = "",
    val description: String = "",
    val fiveHour: QuotaWindowInfo? = null,
    val weekly: QuotaWindowInfo? = null
)

@Serializable
data class QuotaSummaryResponse(
    val groups: List<ModelQuotaGroup> = emptyList(),
    val lastUpdated: String? = null
)

@Serializable
data class ModelQuota(
    val modelId: String,
    val remainingFraction: Float? = null,
    val resetTime: String? = null,
    val usedPercentage: String? = null,
    val resetCountdown: String? = null,
    val weeklyRemainingFraction: Float? = null,
    val weeklyUsedPercentage: String? = null,
    val weeklyResetCountdown: String? = null
) {
    val percentage: Int
        get() = remainingFraction?.let { (it * 100).toInt() } ?: 0
}

@Serializable
data class McpToolInfo(
    val name: String,
    val description: String = "",
    val inputSchema: String = ""
)

@Serializable
data class McpServerSpec(
    val serverName: String,
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val serverUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val disabled: Boolean = false,
    val disabledTools: List<String> = emptyList(),
    val cwd: String = ""
)

@Serializable
data class McpServerState(
    val name: String,
    val spec: McpServerSpec? = null,
    val status: String = "MCP_SERVER_STATUS_UNKNOWN",
    val error: String? = null,
    val tools: List<McpToolInfo> = emptyList(),
    val instructions: String? = null,
    val isEnabled: Boolean = true
)
