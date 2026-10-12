package com.example.gemini.data.agent.custom

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Multi-Agent System inspired by Manus, Devin, and Grok.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
 */

@Serializable
enum class AgentSpecialtyRole(val title: String, val badgeColorHex: String) {
    ARCHITECT("System Architect & Planner", "#3B82F6"),
    FULL_STACK_DEV("Full-Stack Software Engineer", "#10B981"),
    DEEP_RESEARCHER("Deep Web & Code Researcher", "#8B5CF6"),
    BUG_HUNTER("Edge-Case & Security Auditor", "#EF4444"),
    UI_UX_DESIGNER("UI/UX & Frontend Specialist", "#F59E0B"),
    GENERAL_ORCHESTRATOR("Autonomous Goal Solver", "#EC4899")
}

@Serializable
enum class AgentCapabilityTool(val title: String, val description: String) {
    BASH_TERMINAL("Bash / Linux Shell", "Execute build tools, git, npm, python and tests in Termux/PRoot"),
    FILE_SYSTEM("File System Workspace", "Read, write, edit, and patch code across workspace projects"),
    WEB_RESEARCH("Live Web & Docs Search", "Query online APIs, documentation, search engines, and repositories"),
    AST_CODE_ANALYSIS("Code Analysis & AST", "Parse symbols, linting, code structure, and references"),
    GIT_VCS("Git Version Control", "Commit, diff, branch, revert, and push commits to GitHub"),
    BROWSER_INSPECTION("Browser & UI Automation", "Render, inspect DOM, take screenshots, and automate web apps")
}

@Serializable
data class CustomAgent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: AgentSpecialtyRole,
    val avatarEmoji: String = "🤖",
    val accentColorHex: String = "#3B82F6",
    val modelId: String = "gemini-2.5-flash",
    val systemPrompt: String,
    val temperature: Float = 0.4f,
    val enabledTools: List<AgentCapabilityTool> = AgentCapabilityTool.entries,
    val isBuiltInTemplate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class SwarmTaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

@Serializable
data class SwarmStep(
    val stepIndex: Int,
    val agentId: String,
    val agentName: String,
    val agentAvatar: String,
    val title: String,
    val prompt: String,
    val output: String = "",
    val status: SwarmTaskStatus = SwarmTaskStatus.PENDING,
    val durationMs: Long = 0L
)

@Serializable
data class SwarmTask(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,
    val participatingAgentIds: List<String>,
    val steps: List<SwarmStep> = emptyList(),
    val finalSynthesis: String = "",
    val status: SwarmTaskStatus = SwarmTaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)
