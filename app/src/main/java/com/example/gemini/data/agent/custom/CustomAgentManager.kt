package com.example.gemini.data.agent.custom

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Manager and Orchestrator for Multi-Agent Studio and Swarm.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
 */
class CustomAgentManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CustomAgentManager"
        private const val FILE_NAME = "jin_custom_agents_v2.json"

        @Volatile
        private var INSTANCE: CustomAgentManager? = null

        fun getInstance(context: Context): CustomAgentManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CustomAgentManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        val BUILTIN_TEMPLATES = listOf(
            CustomAgent(
                id = "builtin_argon",
                name = "Argon Frontier Architect",
                role = AgentSpecialtyRole.ARCHITECT,
                avatarEmoji = "💎",
                accentColorHex = "#6366F1",
                modelId = "gemini-argon",
                systemPrompt = "You are Argon Frontier Architect, powered by Gemini Argon Ultra-tier reasoning. You design robust distributed systems, formulate formal software specs, and orchestrate complex autonomous swarm workflows.",
                temperature = 0.2f,
                enabledTools = AgentCapabilityTool.entries,
                isBuiltInTemplate = true
            ),
            CustomAgent(
                id = "builtin_manus",
                name = "Manus Autonomous Solver",
                role = AgentSpecialtyRole.GENERAL_ORCHESTRATOR,
                avatarEmoji = "⚡",
                accentColorHex = "#EC4899",
                modelId = "gemini-2.5-flash",
                systemPrompt = "You are Manus, an autonomous goal solver. You break down ambitious user requirements into rigorous multi-step execution plans, leverage bash and web search tools proactively, and persist until the objective is 100% verified.",
                temperature = 0.3f,
                enabledTools = AgentCapabilityTool.entries,
                isBuiltInTemplate = true
            ),
            CustomAgent(
                id = "builtin_devin",
                name = "Devin Autonomous Engineer",
                role = AgentSpecialtyRole.FULL_STACK_DEV,
                avatarEmoji = "🛠️",
                accentColorHex = "#10B981",
                modelId = "gemini-2.5-pro",
                systemPrompt = "You are Devin, a world-class autonomous software engineer. You navigate large codebases, write clean production code with idiomatic patterns, manage git commits, and compile/test projects end-to-end.",
                temperature = 0.2f,
                enabledTools = listOf(
                    AgentCapabilityTool.BASH_TERMINAL,
                    AgentCapabilityTool.FILE_SYSTEM,
                    AgentCapabilityTool.AST_CODE_ANALYSIS,
                    AgentCapabilityTool.GIT_VCS
                ),
                isBuiltInTemplate = true
            ),
            CustomAgent(
                id = "builtin_grok",
                name = "Grok DeepSearcher",
                role = AgentSpecialtyRole.DEEP_RESEARCHER,
                avatarEmoji = "🔍",
                accentColorHex = "#8B5CF6",
                modelId = "gemini-2.5-flash",
                systemPrompt = "You are Grok DeepSearcher, an inquisitive researcher inspired by xAI Grok. You provide deep, unbiased analysis, seek primary documentation, and rigorously verify technical facts before formulating recommendations.",
                temperature = 0.5f,
                enabledTools = listOf(
                    AgentCapabilityTool.WEB_RESEARCH,
                    AgentCapabilityTool.BROWSER_INSPECTION,
                    AgentCapabilityTool.FILE_SYSTEM
                ),
                isBuiltInTemplate = true
            ),
            CustomAgent(
                id = "builtin_hermes",
                name = "Hermes Bug Hunter",
                role = AgentSpecialtyRole.BUG_HUNTER,
                avatarEmoji = "🛡️",
                accentColorHex = "#EF4444",
                modelId = "gemini-2.5-flash",
                systemPrompt = "You are Hermes Bug Hunter, a security auditor and edge-case detective. You scrutinize code for crashes, memory leaks, unhandled exceptions, and null pointer risks. You provide regression test suggestions.",
                temperature = 0.1f,
                enabledTools = listOf(
                    AgentCapabilityTool.FILE_SYSTEM,
                    AgentCapabilityTool.AST_CODE_ANALYSIS,
                    AgentCapabilityTool.BASH_TERMINAL
                ),
                isBuiltInTemplate = true
            ),
            CustomAgent(
                id = "builtin_nova",
                name = "Nova UI/UX Designer",
                role = AgentSpecialtyRole.UI_UX_DESIGNER,
                avatarEmoji = "🎨",
                accentColorHex = "#F59E0B",
                modelId = "gemini-2.5-flash",
                systemPrompt = "You are Nova UI/UX Designer. You craft gorgeous modern Jetpack Compose layouts, smooth animations, responsive mobile components, and accessible color palettes.",
                temperature = 0.6f,
                enabledTools = listOf(
                    AgentCapabilityTool.FILE_SYSTEM,
                    AgentCapabilityTool.BROWSER_INSPECTION
                ),
                isBuiltInTemplate = true
            )
        )
    }

    private val _agents = MutableStateFlow<List<CustomAgent>>(BUILTIN_TEMPLATES)
    val agents: StateFlow<List<CustomAgent>> = _agents.asStateFlow()

    private val storageFile = File(context.filesDir, FILE_NAME)

    init {
        loadAgents()
    }

    private fun loadAgents() {
        try {
            if (storageFile.exists()) {
                val text = storageFile.readText()
                val loaded = JSON.decodeFromString<List<CustomAgent>>(text)
                // Combine built-in templates with custom agents
                val customOnly = loaded.filter { !it.isBuiltInTemplate }
                _agents.value = BUILTIN_TEMPLATES + customOnly
            } else {
                _agents.value = BUILTIN_TEMPLATES
                saveAgents()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading custom agents: ${e.message}", e)
            _agents.value = BUILTIN_TEMPLATES
        }
    }

    private fun saveAgents() {
        try {
            val jsonText = JSON.encodeToString(_agents.value)
            storageFile.writeText(jsonText)
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving custom agents: ${e.message}", e)
        }
    }

    suspend fun saveAgent(agent: CustomAgent): Boolean = withContext(Dispatchers.IO) {
        val current = _agents.value.toMutableList()
        val index = current.indexOfFirst { it.id == agent.id }
        if (index >= 0) {
            current[index] = agent
        } else {
            current.add(agent)
        }
        _agents.value = current
        saveAgents()
        true
    }

    suspend fun deleteAgent(agentId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _agents.value.toMutableList()
        val target = current.firstOrNull { it.id == agentId }
        if (target != null && !target.isBuiltInTemplate) {
            current.removeAll { it.id == agentId }
            _agents.value = current
            saveAgents()
            true
        } else {
            false
        }
    }

    suspend fun duplicateAgent(agentId: String): CustomAgent? = withContext(Dispatchers.IO) {
        val target = _agents.value.firstOrNull { it.id == agentId } ?: return@withContext null
        val clone = target.copy(
            id = UUID.randomUUID().toString(),
            name = "${target.name} (Copy)",
            isBuiltInTemplate = false,
            createdAt = System.currentTimeMillis()
        )
        saveAgent(clone)
        clone
    }

    /**
     * Orchestrates a Swarm multi-agent task breakdown.
     */
    suspend fun planSwarmSteps(goal: String, selectedAgents: List<CustomAgent>): List<SwarmStep> = withContext(Dispatchers.Default) {
        val steps = mutableListOf<SwarmStep>()
        if (selectedAgents.isEmpty()) return@withContext emptyList()

        val architect = selectedAgents.firstOrNull { it.role == AgentSpecialtyRole.ARCHITECT }
            ?: selectedAgents.firstOrNull { it.role == AgentSpecialtyRole.GENERAL_ORCHESTRATOR }
            ?: selectedAgents.first()

        steps.add(
            SwarmStep(
                stepIndex = 1,
                agentId = architect.id,
                agentName = architect.name,
                agentAvatar = architect.avatarEmoji,
                title = "1. Architecture Blueprint & Task Decomposition",
                prompt = "Formulate high-level architecture, module contracts, and implementation steps for: $goal",
                status = SwarmTaskStatus.PENDING
            )
        )

        val developers = selectedAgents.filter { it.role == AgentSpecialtyRole.FULL_STACK_DEV || it.role == AgentSpecialtyRole.UI_UX_DESIGNER }
        developers.forEachIndexed { i, dev ->
            steps.add(
                SwarmStep(
                    stepIndex = steps.size + 1,
                    agentId = dev.id,
                    agentName = dev.name,
                    agentAvatar = dev.avatarEmoji,
                    title = "${steps.size + 1}. Implementation (${dev.role.title})",
                    prompt = "Implement component code adhering to architectural blueprints for: $goal",
                    status = SwarmTaskStatus.PENDING
                )
            )
        }

        val auditor = selectedAgents.firstOrNull { it.role == AgentSpecialtyRole.BUG_HUNTER }
            ?: selectedAgents.firstOrNull { it.role == AgentSpecialtyRole.DEEP_RESEARCHER }
        if (auditor != null) {
            steps.add(
                SwarmStep(
                    stepIndex = steps.size + 1,
                    agentId = auditor.id,
                    agentName = auditor.name,
                    agentAvatar = auditor.avatarEmoji,
                    title = "${steps.size + 1}. Quality Verification & Security Audit",
                    prompt = "Audit code changes, verify edge cases, and eliminate potential crash vectors for: $goal",
                    status = SwarmTaskStatus.PENDING
                )
            )
        }

        // Swarm Consensus & Synthesis Step
        if (steps.size > 1) {
            steps.add(
                SwarmStep(
                    stepIndex = steps.size + 1,
                    agentId = architect.id,
                    agentName = architect.name,
                    agentAvatar = architect.avatarEmoji,
                    title = "${steps.size + 1}. Swarm Consensus & Final Synthesis",
                    prompt = "Synthesize all outputs from preceding specialist agents, confirm all acceptance criteria are met, and provide a unified delivery summary for: $goal",
                    status = SwarmTaskStatus.PENDING
                )
            )
        }

        steps
    }

    /**
     * Formats an orchestrated Swarm breakdown into an autonomous dispatch prompt.
     */
    fun formatSwarmDispatchPrompt(goal: String, steps: List<SwarmStep>): String {
        return buildString {
            append("### 🐝 JIN Autonomous Swarm Workflow\n\n")
            append("**Master Goal**: $goal\n\n")
            append("#### 📋 Sequenced Multi-Agent Execution Plan:\n")
            steps.forEach { step ->
                append("${step.stepIndex}. **${step.agentAvatar} ${step.agentName}**: ${step.title}\n")
                append("   - *Directive*: ${step.prompt}\n")
            }
            append("\n---\n*Each agent operates sequentially. Step 1 outputs define architecture; Step 2 builds implementation; subsequent steps audit and certify consensus.*")
        }
    }
}
