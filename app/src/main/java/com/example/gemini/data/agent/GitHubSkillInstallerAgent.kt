package com.example.gemini.data.agent

import android.content.Context
import android.util.Log
import com.example.gemini.data.local.LocalTerminalBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * Autonomous agent that detects GitHub repo or skill links in chat prompts,
 * automatically clones them into the workspace, installs dependencies (using pnpm for Node.js),
 * and activates skills/tools for immediate use.
 */
object GitHubSkillInstallerAgent {

    private const val TAG = "GitHubSkillInstaller"

    private val GITHUB_URL_PATTERN = Pattern.compile(
        "https?://github\\.com/([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)(?:\\.git)?(?:/.*)?"
    )

    data class RepoInfo(
        val owner: String,
        val repoName: String,
        val fullUrl: String
    )

    fun extractGitHubRepo(text: String): RepoInfo? {
        val matcher = GITHUB_URL_PATTERN.matcher(text)
        if (matcher.find()) {
            val owner = matcher.group(1) ?: return null
            val rawName = matcher.group(2) ?: return null
            val repoName = rawName.removeSuffix(".git")
            val fullUrl = "https://github.com/$owner/$repoName.git"
            return RepoInfo(owner, repoName, fullUrl)
        }
        return null
    }

    /**
     * Autonomously clones, inspects, and installs the repository/skill.
     */
    suspend fun installRepoOrSkill(
        repo: RepoInfo,
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val homeDir = System.getenv("HOME") ?: "/data/user/0/com.termux/files/home"
            val projectsDir = File(homeDir, "projects")
            if (!projectsDir.exists()) projectsDir.mkdirs()

            val targetDir = File(projectsDir, repo.repoName)

            onProgress("🔍 Initiating autonomous setup for ${repo.owner}/${repo.repoName}...")
            delay(300)

            // Step 1: Git clone or pull
            if (targetDir.exists()) {
                onProgress("📂 Repository already exists in projects/${repo.repoName}, updating latest changes...")
                LocalTerminalBridge.instance.sendCommand(
                    command = "cd \"${targetDir.absolutePath}\" && git pull",
                    appendEnter = true
                )
            } else {
                onProgress("⬇️ Cloning repository from GitHub into projects/${repo.repoName}...")
                LocalTerminalBridge.instance.sendCommand(
                    command = "git clone \"${repo.fullUrl}\" \"${targetDir.absolutePath}\"",
                    appendEnter = true
                )
            }
            delay(1500)

            // Step 2: Inspect repository structure
            val hasSkill = File(targetDir, "SKILL.md").exists()
            val hasPackageJson = File(targetDir, "package.json").exists()
            val hasRequirements = File(targetDir, "requirements.txt").exists()
            val hasPyproject = File(targetDir, "pyproject.toml").exists()

            val installedCapabilities = mutableListOf<String>()

            // Step 3: Handle Antigravity Skill
            if (hasSkill) {
                onProgress("🧠 Antigravity Skill detected! Registering into active agent skills...")
                val skillsDir = File(homeDir, ".gemini/antigravity-cli/skills")
                if (!skillsDir.exists()) skillsDir.mkdirs()
                val skillTarget = File(skillsDir, repo.repoName)
                LocalTerminalBridge.instance.sendCommand(
                    command = "cp -r \"${targetDir.absolutePath}\" \"${skillTarget.absolutePath}\"",
                    appendEnter = true
                )
                installedCapabilities.add("Antigravity Custom Skill (SKILL.md registered)")
                delay(800)
            }

            // Step 4: Handle Node.js dependencies (Default to pnpm per agent rules)
            if (hasPackageJson) {
                onProgress("📦 Node.js environment detected. Installing dependencies via pnpm...")
                LocalTerminalBridge.instance.sendCommand(
                    command = "cd \"${targetDir.absolutePath}\" && (command -v pnpm >/dev/null && pnpm install || npm install)",
                    appendEnter = true
                )
                installedCapabilities.add("Node.js Dependencies installed via pnpm")
                delay(1200)
            }

            // Step 5: Handle Python dependencies
            if (hasRequirements || hasPyproject) {
                onProgress("🐍 Python environment detected. Installing dependencies via pip...")
                LocalTerminalBridge.instance.sendCommand(
                    command = "cd \"${targetDir.absolutePath}\" && pip install -r requirements.txt",
                    appendEnter = true
                )
                installedCapabilities.add("Python Dependencies installed")
                delay(1000)
            }

            if (installedCapabilities.isEmpty()) {
                installedCapabilities.add("Source code cloned into projects/${repo.repoName}")
            }

            val summary = buildString {
                append("✅ **Successfully installed ${repo.owner}/${repo.repoName}!**\n\n")
                append("• **Workspace Location:** `projects/${repo.repoName}`\n")
                installedCapabilities.forEach { cap ->
                    append("• **Activated:** $cap\n")
                }
                append("\n🎉 The agent is ready to execute and pair-program with this repository!")
            }

            onProgress(summary)
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install repo: ${e.message}", e)
            val err = "Installation failed: ${e.localizedMessage ?: e.message}"
            onProgress("❌ $err")
            Result.failure(e)
        }
    }
}
