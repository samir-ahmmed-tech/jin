package com.example.gemini.agent

import com.example.gemini.data.agent.plan.PlanModeManager
import com.example.gemini.data.agent.plan.PlanStatus
import com.example.gemini.data.agent.plan.PlanStepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanModeManagerTest {

    @Test
    fun testCreatePlanFromNumberedSteps() {
        val manager = PlanModeManager()
        val rawSteps = """
            1. Scrutinize AndroidManifest.xml: Check exported flags and permissions
            2. Remove cleartext traffic: Configure network security config
            3. Verify Release Keystore: Ensure passwords are not hardcoded
        """.trimIndent()

        val plan = manager.createPlan("Security Plan", "Harden Android application security", rawSteps)

        assertEquals("Security Plan", plan.title)
        assertEquals(3, plan.steps.size)
        assertEquals(PlanStatus.DRAFT, plan.status)
        assertEquals("Scrutinize AndroidManifest.xml", plan.steps[0].title)
        assertEquals(PlanStepState.PENDING, plan.steps[0].status)
    }

    @Test
    fun testApproveAndAdvanceStep() {
        val manager = PlanModeManager()
        val plan = manager.createPlan("Refactor Plan", "Refactor codebase", "1. Step A: do A\n2. Step B: do B")

        manager.approvePlan(plan.id)
        assertEquals(PlanStatus.APPROVED, manager.activePlan.value?.status)

        manager.setStepState(1, PlanStepState.COMPLETED, "Done step 1")
        assertEquals(PlanStatus.EXECUTING, manager.activePlan.value?.status)
        assertEquals(PlanStepState.COMPLETED, manager.activePlan.value?.steps?.get(0)?.status)

        manager.setStepState(2, PlanStepState.COMPLETED, "Done step 2")
        assertEquals(PlanStatus.COMPLETED, manager.activePlan.value?.status)
    }

    @Test
    fun testRejectPlan() {
        val manager = PlanModeManager()
        val plan = manager.createPlan("Build Plan", "Build project", "1. Step 1: compile")

        manager.rejectPlan(plan.id, "Need unit tests before compilation")
        assertEquals(PlanStatus.REJECTED, manager.activePlan.value?.status)
        assertEquals("Need unit tests before compilation", manager.activePlan.value?.rejectionReason)
    }
}
