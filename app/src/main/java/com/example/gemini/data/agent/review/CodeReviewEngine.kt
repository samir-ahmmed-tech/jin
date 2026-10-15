package com.example.gemini.data.agent.review

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class ReviewSeverity(val label: String, val badgeColorHex: String) {
    SECURITY_CRITICAL("Security Critical", "#EF4444"),
    BUG_RISK("Bug Risk", "#F59E0B"),
    PERFORMANCE_ISSUE("Performance Issue", "#3B82F6"),
    STYLE_WARNING("Style & Readability", "#10B981")
}

data class ReviewFinding(
    val filePath: String,
    val lineNumber: Int? = null,
    val severity: ReviewSeverity,
    val title: String,
    val description: String,
    val suggestedFixSnippet: String? = null
)

data class CodeReviewReport(
    val projectName: String,
    val totalFilesScanned: Int,
    val findings: List<ReviewFinding>,
    val timestamp: Long = System.currentTimeMillis()
) {
    val securityCount get() = findings.count { it.severity == ReviewSeverity.SECURITY_CRITICAL }
    val bugRiskCount get() = findings.count { it.severity == ReviewSeverity.BUG_RISK }
    val performanceCount get() = findings.count { it.severity == ReviewSeverity.PERFORMANCE_ISSUE }
}

/**
 * Autonomous Code Review Agent for JIN.
 * Analyzes workspace files or git diffs for security flaws, memory leaks, performance issues and bugs.
 */
class CodeReviewEngine(private val context: Context) {

    private val HARDCODED_KEY_REGEX = Regex("""(?i)(?:apiKey|secret|token|password)\s*=\s*["']([A-Za-z0-9_\-]{16,})["']""")
    private val GLOBAL_SCOPE_REGEX = Regex("""GlobalScope\s*\.\s*(?:launch|async)""")
    private val HARD_FORCE_UNWRAP_REGEX = Regex("""[a-zA-Z0-9_]+\s*!!""")
    private val CLEARTEXT_TRAFFIC_REGEX = Regex("""usesCleartextTraffic\s*=\s*["']true["']""")

    suspend fun reviewWorkspace(workspaceDir: File): CodeReviewReport = withContext(Dispatchers.IO) {
        val findings = mutableListOf<ReviewFinding>()
        val files = workspaceDir.walkTopDown()
            .maxDepth(6)
            .filter { it.isFile && !it.path.contains("/build/") && !it.path.contains("/.git/") && !it.name.startsWith(".") }
            .toList()

        for (file in files) {
            val relPath = file.relativeTo(workspaceDir).invariantSeparatorsPath
            scanFile(file, relPath, findings)
        }

        CodeReviewReport(
            projectName = workspaceDir.name,
            totalFilesScanned = files.size,
            findings = findings
        )
    }

    private fun scanFile(file: File, relPath: String, findings: MutableList<ReviewFinding>) {
        val ext = file.extension.lowercase()
        if (ext !in listOf("kt", "java", "xml", "gradle", "kts", "json", "properties")) return

        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())

        lines.forEachIndexed { idx, line ->
            val lineNum = idx + 1

            // 1. Hardcoded secrets check
            val keyMatch = HARDCODED_KEY_REGEX.find(line)
            if (keyMatch != null && !relPath.contains("test", ignoreCase = true)) {
                val secretSnippet = keyMatch.groupValues[1]
                if (secretSnippet.length > 20 && !secretSnippet.startsWith("$")) {
                    findings.add(
                        ReviewFinding(
                            filePath = relPath,
                            lineNumber = lineNum,
                            severity = ReviewSeverity.SECURITY_CRITICAL,
                            title = "Hardcoded Secret / Token Found",
                            description = "Found potential hardcoded secret in code: `${secretSnippet.take(4)}...${secretSnippet.takeLast(4)}`. Move to environment variable or secure preferences.",
                            suggestedFixSnippet = "val apiKey = System.getenv(\"API_KEY\") ?: authPrefs.getApiKey()"
                        )
                    )
                }
            }

            // 2. Coroutine Scope Leak
            if (GLOBAL_SCOPE_REGEX.containsMatchIn(line)) {
                findings.add(
                    ReviewFinding(
                        filePath = relPath,
                        lineNumber = lineNum,
                        severity = ReviewSeverity.BUG_RISK,
                        title = "Unbounded GlobalScope Usage",
                        description = "GlobalScope can cause memory leaks and ignore Android lifecycle cancellation. Use viewModelScope, lifecycleScope or structured CoroutineScope.",
                        suggestedFixSnippet = "viewModelScope.launch(Dispatchers.IO) { ... }"
                    )
                )
            }

            // 3. Dangerous Force Unwrap (!!)
            if (HARD_FORCE_UNWRAP_REGEX.containsMatchIn(line) && (ext == "kt" || ext == "kts") && !relPath.contains("test", ignoreCase = true)) {
                findings.add(
                    ReviewFinding(
                        filePath = relPath,
                        lineNumber = lineNum,
                        severity = ReviewSeverity.BUG_RISK,
                        title = "Unsafe Null Force Unwrap (!!)",
                        description = "Using !! can trigger unhandled NullPointerExceptions at runtime. Prefer safe calls (?.) or elvis operator (?:).",
                        suggestedFixSnippet = "val value = target ?: return"
                    )
                )
            }

            // 4. Cleartext traffic in Manifest
            if (CLEARTEXT_TRAFFIC_REGEX.containsMatchIn(line) && file.name == "AndroidManifest.xml") {
                findings.add(
                    ReviewFinding(
                        filePath = relPath,
                        lineNumber = lineNum,
                        severity = ReviewSeverity.SECURITY_CRITICAL,
                        title = "Insecure Cleartext Traffic Permitted",
                        description = "android:usesCleartextTraffic=\"true\" allows unencrypted HTTP traffic. Use network-security-config instead.",
                        suggestedFixSnippet = "android:networkSecurityConfig=\"@xml/network_security_config\""
                    )
                )
            }
        }
    }
}
