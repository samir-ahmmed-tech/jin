package com.example.gemini.agent

import com.example.gemini.data.agent.memory.ProjectMemoryManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectMemoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testAddDecisionAndRulePersisted() = runBlocking {
        val workspace = tempFolder.newFolder("test_project")
        val manager = ProjectMemoryManager()

        // 1. Initial memory should be empty
        val initialMem = manager.loadMemory(workspace)
        assertEquals("test_project", initialMem.projectName)
        assertTrue(initialMem.architecturalDecisions.isEmpty())
        assertTrue(initialMem.codingRules.isEmpty())

        // 2. Add decision and rule
        manager.addDecision(workspace, "Use Jetpack Compose with Material3")
        manager.addRule(workspace, "Use pnpm for node, never npm")

        // 3. Load again and verify
        val updatedMem = manager.loadMemory(workspace)
        assertEquals(1, updatedMem.architecturalDecisions.size)
        assertEquals("Use Jetpack Compose with Material3", updatedMem.architecturalDecisions[0])
        assertEquals(1, updatedMem.codingRules.size)
        assertEquals("Use pnpm for node, never npm", updatedMem.codingRules[0])

        // 4. Verify prompt formatting
        val prompt = manager.formatMemoryPrompt(workspace)
        assertTrue(prompt.contains("Project Memory & Conventions (test_project)"))
        assertTrue(prompt.contains("Use Jetpack Compose with Material3"))
        assertTrue(prompt.contains("Use pnpm for node, never npm"))

        // 5. Clear memory
        manager.clearMemory(workspace)
        val clearedMem = manager.loadMemory(workspace)
        assertTrue(clearedMem.architecturalDecisions.isEmpty())
        assertTrue(clearedMem.codingRules.isEmpty())
    }
}
