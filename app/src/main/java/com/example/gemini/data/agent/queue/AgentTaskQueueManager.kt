package com.example.gemini.data.agent.queue

import android.content.Context
import android.util.Log
import com.example.gemini.data.harness.AgentKind
import com.example.gemini.data.harness.JinAutonomousEngine
import com.example.gemini.data.harness.ProviderKind
import com.example.gemini.data.harness.ProviderProfile
import com.example.gemini.data.harness.RuntimeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class QueuedTaskState {
    QUEUED,
    RUNNING,
    PAUSED,
    DONE,
    FAILED
}

data class QueuedTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val prompt: String,
    val agentKind: AgentKind = AgentKind.ANTIGRAVITY,
    val modelId: String = "gemini-2.5-flash",
    val workspacePath: String,
    val state: QueuedTaskState = QueuedTaskState.QUEUED,
    val progressPercent: Int = 0,
    val statusMessage: String = "Waiting in queue...",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val logs: List<String> = emptyList()
)

/**
 * Manages autonomous background agent tasks, priority queuing, and persistence.
 * Integrates with Android Foreground Service to prevent OS execution drops during phone lock.
 */
class AgentTaskQueueManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AgentTaskQueueManager"
        private const val QUEUE_FILE_NAME = "agent_task_queue.json"

        @Volatile
        private var INSTANCE: AgentTaskQueueManager? = null

        fun getInstance(context: Context): AgentTaskQueueManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentTaskQueueManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueFile = File(context.filesDir, QUEUE_FILE_NAME)
    private var currentExecutionJob: Job? = null

    private val _tasks = MutableStateFlow<List<QueuedTask>>(emptyList())
    val tasks: StateFlow<List<QueuedTask>> = _tasks.asStateFlow()

    private val _currentRunningTaskId = MutableStateFlow<String?>(null)
    val currentRunningTaskId: StateFlow<String?> = _currentRunningTaskId.asStateFlow()

    init {
        loadPersistedQueue()
    }

    private fun loadPersistedQueue() {
        if (!queueFile.exists()) return
        try {
            val text = queueFile.readText()
            val array = JSONArray(text)
            val list = mutableListOf<QueuedTask>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawLogs = obj.optJSONArray("logs")
                val logList = mutableListOf<String>()
                if (rawLogs != null) {
                    for (j in 0 until rawLogs.length()) {
                        logList.add(rawLogs.getString(j))
                    }
                }
                list.add(
                    QueuedTask(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        prompt = obj.getString("prompt"),
                        agentKind = runCatching { AgentKind.valueOf(obj.optString("agentKind", "ANTIGRAVITY")) }.getOrDefault(AgentKind.ANTIGRAVITY),
                        modelId = obj.optString("modelId", "gemini-2.5-flash"),
                        workspacePath = obj.getString("workspacePath"),
                        state = runCatching { QueuedTaskState.valueOf(obj.optString("state", "QUEUED")) }.getOrDefault(QueuedTaskState.QUEUED),
                        progressPercent = obj.optInt("progressPercent", 0),
                        statusMessage = obj.optString("statusMessage", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        logs = logList
                    )
                )
            }
            _tasks.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Error loading persisted task queue: ${e.message}")
        }
    }

    private fun persistQueue() {
        try {
            val array = JSONArray()
            _tasks.value.forEach { t ->
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("prompt", t.prompt)
                    put("agentKind", t.agentKind.name)
                    put("modelId", t.modelId)
                    put("workspacePath", t.workspacePath)
                    put("state", t.state.name)
                    put("progressPercent", t.progressPercent)
                    put("statusMessage", t.statusMessage)
                    put("createdAt", t.createdAt)
                    put("updatedAt", t.updatedAt)
                    put("logs", JSONArray(t.logs.takeLast(100)))
                }
                array.put(obj)
            }
            queueFile.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting task queue: ${e.message}")
        }
    }

    fun enqueueTask(
        title: String,
        prompt: String,
        agentKind: AgentKind = AgentKind.ANTIGRAVITY,
        modelId: String = "gemini-2.5-flash",
        workspacePath: String
    ): QueuedTask {
        val task = QueuedTask(
            title = title.ifBlank { "Autonomous Task" },
            prompt = prompt,
            agentKind = agentKind,
            modelId = modelId,
            workspacePath = workspacePath
        )
        _tasks.value = _tasks.value + task
        persistQueue()
        triggerQueueProcessor()
        return task
    }

    fun pauseTask(taskId: String) {
        val current = _tasks.value.toMutableList()
        val index = current.indexOfFirst { it.id == taskId }
        if (index >= 0) {
            current[index] = current[index].copy(
                state = QueuedTaskState.PAUSED,
                statusMessage = "Paused by user",
                updatedAt = System.currentTimeMillis()
            )
            _tasks.value = current
            persistQueue()
            if (_currentRunningTaskId.value == taskId) {
                currentExecutionJob?.cancel()
                _currentRunningTaskId.value = null
                triggerQueueProcessor()
            }
        }
    }

    fun resumeTask(taskId: String) {
        val current = _tasks.value.toMutableList()
        val index = current.indexOfFirst { it.id == taskId }
        if (index >= 0) {
            current[index] = current[index].copy(
                state = QueuedTaskState.QUEUED,
                statusMessage = "Resumed and waiting in queue",
                updatedAt = System.currentTimeMillis()
            )
            _tasks.value = current
            persistQueue()
            triggerQueueProcessor()
        }
    }

    fun deleteTask(taskId: String) {
        if (_currentRunningTaskId.value == taskId) {
            currentExecutionJob?.cancel()
            _currentRunningTaskId.value = null
        }
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
        persistQueue()
        triggerQueueProcessor()
    }

    fun clearCompleted() {
        _tasks.value = _tasks.value.filterNot { it.state == QueuedTaskState.DONE || it.state == QueuedTaskState.FAILED }
        persistQueue()
    }

    private fun triggerQueueProcessor() {
        if (_currentRunningTaskId.value != null) return
        val next = _tasks.value.firstOrNull { it.state == QueuedTaskState.QUEUED } ?: return
        executeTaskAsync(next)
    }

    private fun executeTaskAsync(task: QueuedTask) {
        _currentRunningTaskId.value = task.id
        updateTaskState(task.id, QueuedTaskState.RUNNING, 10, "Starting autonomous agent...")

        currentExecutionJob = queueScope.launch {
            try {
                val engine = JinAutonomousEngine(context)
                val provider = ProviderProfile(
                    kind = ProviderKind.OPENCODE_ZEN,
                    model = task.modelId
                )

                updateTaskState(task.id, QueuedTaskState.RUNNING, 30, "Agent executing directives...")

                engine.runTask(
                    projectId = task.id,
                    prompt = task.prompt,
                    agent = task.agentKind,
                    provider = provider,
                    customModel = task.modelId
                ) { event ->
                    when (event) {
                        is RuntimeEvent.ToolStarted -> {
                            appendTaskLog(task.id, "Tool: ${event.toolName} - ${event.detail}")
                            updateTaskState(task.id, QueuedTaskState.RUNNING, 60, "Running ${event.toolName}")
                        }
                        is RuntimeEvent.ToolCompleted -> {
                            appendTaskLog(task.id, "Completed: ${event.toolName}")
                        }
                        is RuntimeEvent.RuntimeLog -> {
                            appendTaskLog(task.id, "${event.title}: ${event.detail}")
                        }
                        is RuntimeEvent.SessionCompleted -> {
                            appendTaskLog(task.id, "Finished: ${event.finalAnswer}")
                        }
                        is RuntimeEvent.SessionFailed -> {
                            appendTaskLog(task.id, "Failed: ${event.reason}")
                        }
                        else -> {}
                    }
                }

                updateTaskState(task.id, QueuedTaskState.DONE, 100, "Completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Task execution failed: ${e.message}", e)
                updateTaskState(task.id, QueuedTaskState.FAILED, 100, "Error: ${e.message}")
            } finally {
                _currentRunningTaskId.value = null
                persistQueue()
                triggerQueueProcessor()
            }
        }
    }

    private fun updateTaskState(taskId: String, state: QueuedTaskState, progress: Int, message: String) {
        val current = _tasks.value.toMutableList()
        val index = current.indexOfFirst { it.id == taskId }
        if (index >= 0) {
            current[index] = current[index].copy(
                state = state,
                progressPercent = progress,
                statusMessage = message,
                updatedAt = System.currentTimeMillis()
            )
            _tasks.value = current
            persistQueue()
        }
    }

    private fun appendTaskLog(taskId: String, logLine: String) {
        val current = _tasks.value.toMutableList()
        val index = current.indexOfFirst { it.id == taskId }
        if (index >= 0) {
            val updatedLogs = current[index].logs + logLine
            current[index] = current[index].copy(
                logs = updatedLogs.takeLast(100),
                updatedAt = System.currentTimeMillis()
            )
            _tasks.value = current
        }
    }
}
