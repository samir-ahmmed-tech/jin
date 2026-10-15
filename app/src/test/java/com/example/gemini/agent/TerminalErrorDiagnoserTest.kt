package com.example.gemini.agent

import com.example.gemini.data.harness.TerminalErrorCategory
import com.example.gemini.data.harness.TerminalErrorDiagnoser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalErrorDiagnoserTest {

    @Test
    fun testKotlinCompilerErrorDetected() {
        val output = """
            > Task :app:compileDebugKotlin FAILED
            e: file:///home/project/MainActivity.kt: (42, 15): Unresolved reference: NonExistentSymbol
        """.trimIndent()

        val diagnosis = TerminalErrorDiagnoser.diagnose(1, output)
        assertEquals(TerminalErrorCategory.KOTLIN_COMPILER, diagnosis.category)
        assertTrue(diagnosis.filePath?.contains("MainActivity.kt") == true)
        assertEquals(42, diagnosis.lineNumber)
        assertTrue(diagnosis.summary.contains("Unresolved reference"))
    }

    @Test
    fun testCommandNotFoundDetected() {
        val output = "bash: gradlew: command not found"
        val diagnosis = TerminalErrorDiagnoser.diagnose(127, output)
        assertEquals(TerminalErrorCategory.COMMAND_NOT_FOUND, diagnosis.category)
        assertTrue(diagnosis.summary.contains("gradlew"))
    }

    @Test
    fun testPermissionDeniedDetected() {
        val output = "sh: ./gradlew: Permission denied"
        val diagnosis = TerminalErrorDiagnoser.diagnose(126, output)
        assertEquals(TerminalErrorCategory.PERMISSION_DENIED, diagnosis.category)
        assertTrue(diagnosis.suggestedFixDirection.contains("chmod +x"))
    }

    @Test
    fun testSuccessOutputDiagnosis() {
        val output = "BUILD SUCCESSFUL in 1s"
        val diagnosis = TerminalErrorDiagnoser.diagnose(0, output)
        assertEquals(TerminalErrorCategory.UNKNOWN, diagnosis.category)
        assertTrue(diagnosis.summary.contains("succeeded"))
    }

    @Test
    fun testSelfHealingPromptFormatting() {
        val output = "e: /app/Foo.kt: (10, 5): Type mismatch: inferred Int but String expected"
        val diagnosis = TerminalErrorDiagnoser.diagnose(1, output)
        val prompt = TerminalErrorDiagnoser.formatSelfHealingPrompt(diagnosis, 1, 3)

        assertTrue(prompt.contains("Self-Healing Attempt 1 of 3"))
        assertTrue(prompt.contains("Foo.kt"))
        assertTrue(prompt.contains("Type mismatch"))
    }
}
