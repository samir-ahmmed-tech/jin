package com.example.gemini.data.harness

import java.io.File

/**
 * Categorized terminal and compiler errors for autonomous recovery.
 */
enum class TerminalErrorCategory {
    KOTLIN_COMPILER,
    AAPT_ANDROID_RESOURCE,
    GRADLE_CONFIGURATION,
    DEPENDENCY_RESOLUTION,
    COMMAND_NOT_FOUND,
    PERMISSION_DENIED,
    EXECUTION_CRASH,
    UNKNOWN
}

data class DiagnosedError(
    val category: TerminalErrorCategory,
    val summary: String,
    val filePath: String? = null,
    val lineNumber: Int? = null,
    val rawSnippet: String,
    val suggestedFixDirection: String
)

/**
 * Intelligent error parser for Terminal commands, Gradle builds, and compiler outputs.
 * Enables JIN autonomous agent to inspect stdout/stderr and attempt self-healing corrections.
 */
object TerminalErrorDiagnoser {

    private val KOTLIN_ERROR_REGEX = Regex("""e:\s*(?:file://)?([^\s:]+):(?:\s*\(([0-9]+),\s*([0-9]+)\))?:\s*(.+)""")
    private val AAPT_ERROR_REGEX = Regex("""AAPT:\s*error:\s*(.+)""")
    private val COMMAND_NOT_FOUND_REGEX = Regex("""(?:sh|bash|zsh):\s*([^\s:]+):\s*command not found""", RegexOption.IGNORE_CASE)
    private val PERMISSION_DENIED_REGEX = Regex("""(?:sh|bash|zsh):\s*([^\s:]+):\s*Permission denied""", RegexOption.IGNORE_CASE)
    private val COULD_NOT_FIND_REGEX = Regex("""Could not find\s+([^\n\r]+)""")

    fun diagnose(exitCode: Int, output: String): DiagnosedError {
        if (exitCode == 0) {
            return DiagnosedError(
                category = TerminalErrorCategory.UNKNOWN,
                summary = "Command succeeded without errors.",
                rawSnippet = output.takeLast(200),
                suggestedFixDirection = "None required."
            )
        }

        // 1. Check for Kotlin compilation errors
        val kotlinMatch = KOTLIN_ERROR_REGEX.find(output)
        if (kotlinMatch != null) {
            val file = kotlinMatch.groupValues[1]
            val line = kotlinMatch.groupValues[2].toIntOrNull()
            val msg = kotlinMatch.groupValues[4]
            return DiagnosedError(
                category = TerminalErrorCategory.KOTLIN_COMPILER,
                summary = "Kotlin compiler error: $msg",
                filePath = file,
                lineNumber = line,
                rawSnippet = kotlinMatch.value,
                suggestedFixDirection = "Inspect file `$file`${if (line != null) " at line $line" else ""} and resolve `$msg`."
            )
        }

        // 2. Check for AAPT resource errors
        val aaptMatch = AAPT_ERROR_REGEX.find(output)
        if (aaptMatch != null) {
            val msg = aaptMatch.groupValues[1]
            return DiagnosedError(
                category = TerminalErrorCategory.AAPT_ANDROID_RESOURCE,
                summary = "AAPT Android Resource packaging error: $msg",
                rawSnippet = aaptMatch.value,
                suggestedFixDirection = "Verify resource identifiers, XML syntax in res/ directory, and manifest drawables."
            )
        }

        // 3. Command not found
        val cmdNotFoundMatch = COMMAND_NOT_FOUND_REGEX.find(output)
        if (cmdNotFoundMatch != null) {
            val missingBin = cmdNotFoundMatch.groupValues[1]
            return DiagnosedError(
                category = TerminalErrorCategory.COMMAND_NOT_FOUND,
                summary = "Required binary not installed: $missingBin",
                rawSnippet = cmdNotFoundMatch.value,
                suggestedFixDirection = "The tool `$missingBin` is missing in the environment. Request user installation via pkg or fall back to an available alternative."
            )
        }

        // 4. Permission denied
        val permMatch = PERMISSION_DENIED_REGEX.find(output)
        if (permMatch != null) {
            val target = permMatch.groupValues[1]
            return DiagnosedError(
                category = TerminalErrorCategory.PERMISSION_DENIED,
                summary = "Permission denied executing: $target",
                rawSnippet = permMatch.value,
                suggestedFixDirection = "Verify executable file permissions (chmod +x) or user storage access permissions."
            )
        }

        // 5. Dependency resolution
        val depMatch = COULD_NOT_FIND_REGEX.find(output)
        if (depMatch != null) {
            val dep = depMatch.groupValues[1]
            return DiagnosedError(
                category = TerminalErrorCategory.DEPENDENCY_RESOLUTION,
                summary = "Gradle could not find dependency: $dep",
                rawSnippet = depMatch.value,
                suggestedFixDirection = "Check repositories (google(), mavenCentral(), maven { ... }) in settings.gradle.kts and verify dependency version coordinate."
            )
        }

        // Fallback: extract last few meaningful lines
        val lines = output.lines().filter { it.isNotBlank() }
        val tailSnippet = lines.takeLast(10).joinToString("\n")
        return DiagnosedError(
            category = TerminalErrorCategory.UNKNOWN,
            summary = "Execution failed with exit code $exitCode",
            rawSnippet = tailSnippet,
            suggestedFixDirection = "Review stderr/stdout diagnostics and adjust implementation accordingly."
        )
    }

    /**
     * Formats diagnosis into an autonomous feedback prompt for iterative self-healing.
     */
    fun formatSelfHealingPrompt(diagnosis: DiagnosedError, attempt: Int, maxAttempts: Int): String {
        return buildString {
            append("### ⚠️ Autonomous Error Diagnosis (Self-Healing Attempt $attempt of $maxAttempts)\n\n")
            append("- **Category**: `${diagnosis.category}`\n")
            append("- **Diagnosis**: ${diagnosis.summary}\n")
            if (diagnosis.filePath != null) {
                append("- **Offending File**: `${diagnosis.filePath}`")
                if (diagnosis.lineNumber != null) append(" (Line: ${diagnosis.lineNumber})")
                append("\n")
            }
            append("- **Suggestion**: ${diagnosis.suggestedFixDirection}\n\n")
            append("```\n${diagnosis.rawSnippet}\n```\n\n")
            append("Please inspect the code and generate the corrected file writes to resolve this issue.")
        }
    }
}
