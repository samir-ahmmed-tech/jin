package com.example.gemini.data.agent.plan

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

enum class PlanStatus {
    DRAFT,
    APPROVED,
    EXECUTING,
    COMPLETED,
    REJECTED
}

enum class PlanStepState {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class PlanStep(
    val id: String = UUID.randomUUID().toString(),
    val stepIndex: Int,
    val title: String,
    val description: String,
    val targetFiles: List<String> = emptyList(),
    val status: PlanStepState = PlanStepState.PENDING,
    val executionLog: String? = null
) {
    constructor(stepIndex: Int, title: String, description: String) : this(
        id = UUID.randomUUID().toString(),
        stepIndex = stepIndex,
        title = title,
        description = description
    )
}

data class ExecutionPlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val goal: String,
    val steps: List<PlanStep>,
    val status: PlanStatus = PlanStatus.DRAFT,
    val createdAt: Long = System.currentTimeMillis(),
    val rejectionReason: String? = null
)

/**
 * Universal Plan Mode & Step-by-Step Execution Manager for JIN.
 * Enforces structured planning before code modifications, allowing user review & approval.
 */
class PlanModeManager(private val context: Context? = null) {

    companion object {
        private const val TAG = "PlanModeManager"

        @Volatile
        private var INSTANCE: PlanModeManager? = null

        fun getInstance(context: Context? = null): PlanModeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlanModeManager(context?.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _activePlan = MutableStateFlow<ExecutionPlan?>(null)
    val activePlan: StateFlow<ExecutionPlan?> = _activePlan.asStateFlow()
    val currentPlan: StateFlow<ExecutionPlan?> = _activePlan.asStateFlow()

    private val _planHistory = MutableStateFlow<List<ExecutionPlan>>(emptyList())
    val planHistory: StateFlow<List<ExecutionPlan>> = _planHistory.asStateFlow()

    fun createPlan(title: String, goal: String, rawStepsText: String): ExecutionPlan {
        val parsedSteps = parseStepsFromText(rawStepsText)
        val plan = ExecutionPlan(
            title = title.ifBlank { "Implementation Plan" },
            goal = goal,
            steps = parsedSteps,
            status = PlanStatus.DRAFT
        )
        _activePlan.value = plan
        _planHistory.value = _planHistory.value + plan
        return plan
    }

    fun createPlan(title: String, goal: String, steps: List<PlanStep>): ExecutionPlan {
        val plan = ExecutionPlan(
            title = title.ifBlank { "Implementation Plan" },
            goal = goal,
            steps = steps,
            status = PlanStatus.DRAFT
        )
        _activePlan.value = plan
        _planHistory.value = _planHistory.value + plan
        return plan
    }

    fun approvePlan(planId: String) {
        val plan = _activePlan.value ?: return
        if (plan.id == planId) {
            _activePlan.value = plan.copy(status = PlanStatus.APPROVED)
        }
    }

    fun rejectPlan(planId: String, reason: String) {
        val plan = _activePlan.value ?: return
        if (plan.id == planId) {
            _activePlan.value = plan.copy(status = PlanStatus.REJECTED, rejectionReason = reason)
        }
    }

    fun setStepState(stepIndex: Int, newState: PlanStepState, logMessage: String? = null) {
        val plan = _activePlan.value ?: return
        val updatedSteps = plan.steps.map { s ->
            if (s.stepIndex == stepIndex) {
                s.copy(status = newState, executionLog = logMessage ?: s.executionLog)
            } else s
        }
        val isAllDone = updatedSteps.all { it.status == PlanStepState.COMPLETED || it.status == PlanStepState.SKIPPED }
        val newPlanStatus = if (isAllDone) PlanStatus.COMPLETED else PlanStatus.EXECUTING
        _activePlan.value = plan.copy(steps = updatedSteps, status = newPlanStatus)
    }

    fun editStep(stepIndex: Int, newTitle: String, newDescription: String) {
        val plan = _activePlan.value ?: return
        val updatedSteps = plan.steps.map { s ->
            if (s.stepIndex == stepIndex) {
                s.copy(title = newTitle, description = newDescription)
            } else s
        }
        _activePlan.value = plan.copy(steps = updatedSteps)
    }

    fun clearActivePlan() {
        _activePlan.value = null
    }

    private fun parseStepsFromText(text: String): List<PlanStep> {
        val lines = text.lines()
        val steps = mutableListOf<PlanStep>()
        var currentIndex = 1

        val numberedRegex = Regex("""^(?:[0-9]+\.|\-|\*|###\s*[0-9]+\.)\s+(.+)""")
        lines.forEach { line ->
            val trimmed = line.trim()
            val match = numberedRegex.find(trimmed)
            if (match != null && trimmed.length > 5) {
                val fullText = match.groupValues[1]
                val parts = fullText.split(":", limit = 2)
                val title = parts.getOrNull(0)?.trim() ?: "Step $currentIndex"
                val desc = parts.getOrNull(1)?.trim() ?: fullText

                steps.add(
                    PlanStep(
                        stepIndex = currentIndex++,
                        title = title,
                        description = desc,
                        status = PlanStepState.PENDING
                    )
                )
            }
        }

        if (steps.isEmpty()) {
            steps.add(
                PlanStep(
                    stepIndex = 1,
                    title = "Analyze Requirements",
                    description = "Inspect workspace state and identify dependencies.",
                    status = PlanStepState.PENDING
                )
            )
            steps.add(
                PlanStep(
                    stepIndex = 2,
                    title = "Execute Code Edits",
                    description = "Apply required modifications and verify file integrity.",
                    status = PlanStepState.PENDING
                )
            )
            steps.add(
                PlanStep(
                    stepIndex = 3,
                    title = "Verify & Test",
                    description = "Run compiler or unit test suite to ensure zero regressions.",
                    status = PlanStepState.PENDING
                )
            )
        }

        return steps
    }
}
