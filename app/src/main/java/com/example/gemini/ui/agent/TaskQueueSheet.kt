package com.example.gemini.ui.agent

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.agent.queue.AgentTaskQueueManager
import com.example.gemini.data.agent.queue.QueuedTask
import com.example.gemini.data.agent.queue.QueuedTaskState
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.GeminiBlue
import com.example.gemini.theme.QuotaGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskQueueSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val queueManager = remember { AgentTaskQueueManager.getInstance(context) }
    val tasks by queueManager.tasks.collectAsState()
    val runningTaskId by queueManager.currentRunningTaskId.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.QueuePlayNext,
                        contentDescription = null,
                        tint = GeminiBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Agent Task Queue",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (tasks.isNotEmpty()) GeminiBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${tasks.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (tasks.isNotEmpty()) GeminiBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (tasks.any { it.state == QueuedTaskState.DONE || it.state == QueuedTaskState.FAILED }) {
                        TextButton(
                            onClick = { queueManager.clearCompleted() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Clear Done", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (tasks.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Checklist,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No pending agent tasks",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Queue tasks to run autonomous coding workflows in the background.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskQueueCard(
                            task = task,
                            isCurrentlyRunning = task.id == runningTaskId,
                            onPause = { queueManager.pauseTask(task.id) },
                            onResume = { queueManager.resumeTask(task.id) },
                            onDelete = { queueManager.deleteTask(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskQueueCard(
    task: QueuedTask,
    isCurrentlyRunning: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val stateColor = when (task.state) {
        QueuedTaskState.QUEUED -> Color(0xFFF59E0B)
        QueuedTaskState.RUNNING -> GeminiBlue
        QueuedTaskState.PAUSED -> Color(0xFF8B5CF6)
        QueuedTaskState.DONE -> QuotaGreen
        QueuedTaskState.FAILED -> MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, if (isCurrentlyRunning) GeminiBlue else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = stateColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = task.state.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = stateColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (task.state) {
                        QueuedTaskState.RUNNING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Outlined.Pause, contentDescription = "Pause", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            }
                        }
                        QueuedTaskState.PAUSED -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume", tint = QuotaGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                        else -> {}
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = task.prompt,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (isCurrentlyRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = GeminiBlue
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${task.agentKind.name} · ${task.modelId}",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = task.statusMessage,
                    fontSize = 10.5.sp,
                    color = stateColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
