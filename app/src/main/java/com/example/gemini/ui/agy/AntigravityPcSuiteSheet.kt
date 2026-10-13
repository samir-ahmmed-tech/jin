package com.example.gemini.ui.agy

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.agent.agy.AntigravityOptimizer
import kotlinx.coroutines.launch

/**
 * Antigravity 2.0 PC-Grade Suite & Turbo Optimizer BottomSheet.
 * Designed with uniform app-native proportions, single-line non-wrapping pills/buttons,
 * and responsive horizontal scrolling so no UI element ever clips or squishes on any screen size.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntigravityPcSuiteSheet(
    initialTab: Int = 0,
    activeWorkspacePath: String?,
    onSendPromptToAntigravity: (String) -> Unit,
    onOpenFullSecuritySettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AntigravityOptimizer.init(context)
    }

    val optState by AntigravityOptimizer.state.collectAsState()
    val subagents by AntigravityOptimizer.subagents.collectAsState()
    val scheduledTasks by AntigravityOptimizer.scheduledTasks.collectAsState()

    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 4)) }
    var isOptimizing by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            // App-Native Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2563EB), Color(0xFF7C3AED))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Antigravity 2.0 PC Suite",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "TURBO +${optState.speedBoostPercent}%",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Accuracy ${optState.accuracyScore}% • Subagents • Rules & Skills • Cron",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5 PC Feature Tabs (Scrollable, Uniform Height, Never Wraps)
            val tabs = listOf(
                "⚡ Optimize",
                "🤖 Subagents",
                "🧩 Rules & Skills",
                "⏰ Schedule",
                "🛡️ Sandbox"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.forEachIndexed { idx, title ->
                    val active = selectedTab == idx
                    Surface(
                        onClick = { selectedTab = idx },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> OptimizeTabContent(
                    optState = optState,
                    isOptimizing = isOptimizing,
                    onRunFullOptimize = {
                        if (!isOptimizing) {
                            isOptimizing = true
                            scope.launch {
                                val res = AntigravityOptimizer.runFullOptimization(context, activeWorkspacePath)
                                isOptimizing = false
                                Toast.makeText(
                                    context,
                                    "⚡ Optimized! Freed ${res.freedMemoryMb}MB RAM • Accuracy ${res.accuracyScore}%",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onToggleChanged = { turbo, smartCtx, http2, wal, fps, deep ->
                        AntigravityOptimizer.updateToggle(
                            context = context,
                            turboMode = turbo,
                            smartContext = smartCtx,
                            prewarmHttp2 = http2,
                            compactWal = wal,
                            highFps = fps,
                            deepReasoning = deep
                        )
                    }
                )
                1 -> SubagentsTabContent(
                    subagents = subagents,
                    onInvokeSubagent = { spec, taskPrompt ->
                        val prompt = buildString {
                            appendLine("Invoke subagent `${spec.name}` (${spec.role}, model=${spec.model}, workspace=${spec.workspaceMode}):")
                            appendLine(taskPrompt.ifBlank { "Analyze the active workspace and report key findings and improvements." })
                        }
                        onSendPromptToAntigravity(prompt)
                        onDismiss()
                    },
                    onDefineSubagent = { newSpec ->
                        AntigravityOptimizer.defineCustomSubagent(newSpec)
                        Toast.makeText(context, "Subagent '${newSpec.name}' saved!", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteSubagent = { name ->
                        AntigravityOptimizer.deleteCustomSubagent(name)
                    }
                )
                2 -> CustomizationsTabContent(
                    activeWorkspacePath = activeWorkspacePath,
                    onSendPromptToAntigravity = { prompt ->
                        onSendPromptToAntigravity(prompt)
                        onDismiss()
                    }
                )
                3 -> ScheduleTabContent(
                    scheduledTasks = scheduledTasks,
                    onAddTask = { prompt, mode, durSec, cronExpr, cond, daemon ->
                        AntigravityOptimizer.addScheduledTask(
                            prompt = prompt,
                            mode = mode,
                            durationSeconds = durSec,
                            cronExpression = cronExpr,
                            timerCondition = cond,
                            isDaemon = daemon
                        )
                        Toast.makeText(context, "⏰ Scheduled task added!", Toast.LENGTH_SHORT).show()
                    },
                    onToggleTask = { id -> AntigravityOptimizer.toggleScheduledTask(id) },
                    onRunNow = { prompt ->
                        onSendPromptToAntigravity(prompt)
                        onDismiss()
                    },
                    onRemoveTask = { id -> AntigravityOptimizer.removeScheduledTask(id) }
                )
                4 -> SandboxPoliciesTabContent(
                    onOpenFullSecuritySettings = {
                        onDismiss()
                        onOpenFullSecuritySettings()
                    },
                    onSendSlashCommand = { cmd ->
                        onSendPromptToAntigravity(cmd)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun OptimizeTabContent(
    optState: AntigravityOptimizer.OptimizationState,
    isOptimizing: Boolean,
    onRunFullOptimize: () -> Unit,
    onToggleChanged: (Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricBox("SPEED", "+${optState.speedBoostPercent}%", Color(0xFF38BDF8))
                        MetricBox("ACCURACY", "${optState.accuracyScore}%", Color(0xFF10B981))
                        MetricBox("RAM FREED", "${optState.freedMemoryMb} MB", Color(0xFFA855F7))
                        MetricBox("STREAM", if (optState.highFrameRateStreaming) "60 FPS" else "30 FPS", Color(0xFFF59E0B))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onRunFullOptimize,
                        enabled = !isOptimizing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isOptimizing) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Optimizing Antigravity Engine...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        } else {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "1-Tap Optimize Speed, Accuracy & RAM",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (optState.lastOptimizedAt > 0L) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Last optimized ${AntigravityOptimizer.formatTimestamp(optState.lastOptimizedAt)} • Cleaned ${optState.cleanedLockFiles} locks",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "ANTIGRAVITY ENGINE TUNING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OptimizeSwitchRow(
                title = "Smart Workspace & Rule Context Injection",
                subtitle = "Automatically injects active GEMINI.md, AGENTS.md & project structure",
                checked = optState.smartContextInjection,
                onCheckedChange = { onToggleChanged(null, it, null, null, null, null) }
            )
        }

        item {
            OptimizeSwitchRow(
                title = "Deep Reasoning Budget Boost (12,288 Tokens)",
                subtitle = "Expands thinking token budget by 1.5x for multi-file architecture & fixes",
                checked = optState.deepReasoningBoost,
                onCheckedChange = { onToggleChanged(null, null, null, null, null, it) }
            )
        }

        item {
            OptimizeSwitchRow(
                title = "60 FPS Ultra-Low-Latency Stream Coalescing",
                subtitle = "Renders token chunks every 16ms with pre-warmed Markdown AST cache",
                checked = optState.highFrameRateStreaming,
                onCheckedChange = { onToggleChanged(null, null, null, null, it, null) }
            )
        }

        item {
            OptimizeSwitchRow(
                title = "HTTP/2 & gRPC Multiplexing Pool Pre-Warm",
                subtitle = "Maintains 32 warm HTTP/2 connections with fast 6s fail-over",
                checked = optState.prewarmHttp2Pool,
                onCheckedChange = { onToggleChanged(null, null, it, null, null, null) }
            )
        }

        item {
            OptimizeSwitchRow(
                title = "Auto-Clean Stale Presence Locks & SQLite WAL",
                subtitle = "Prevents trajectory lock contention and keeps local DB fast",
                checked = optState.autoCompactSqliteWal,
                onCheckedChange = { onToggleChanged(null, null, null, it, null, null) }
            )
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun OptimizeSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SubagentsTabContent(
    subagents: List<AntigravityOptimizer.SubagentSpec>,
    onInvokeSubagent: (AntigravityOptimizer.SubagentSpec, String) -> Unit,
    onDefineSubagent: (AntigravityOptimizer.SubagentSpec) -> Unit,
    onDeleteSubagent: (String) -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newModel by remember { mutableStateOf("inherit") }
    var newWorkspaceMode by remember { mutableStateOf("inherit") }
    var newPrompt by remember { mutableStateOf("") }
    var taskPrompts by remember { mutableStateOf(mapOf<String, String>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SUBAGENTS (${subagents.size})",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = { showCreateForm = !showCreateForm },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showCreateForm) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showCreateForm) "Close" else "New Subagent",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = showCreateForm) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Define Custom Subagent",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Name (e.g. security-auditor)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newRole,
                            onValueChange = { newRole = it },
                            label = { Text("Role Title (e.g. Security Auditor)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("inherit", "flash", "pro").forEach { m ->
                                FilterChip(
                                    selected = newModel == m,
                                    onClick = { newModel = m },
                                    label = { Text("Model: $m", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("inherit", "branch", "share").forEach { wm ->
                                FilterChip(
                                    selected = newWorkspaceMode == wm,
                                    onClick = { newWorkspaceMode = wm },
                                    label = { Text("WS: $wm", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = newPrompt,
                            onValueChange = { newPrompt = it },
                            label = { Text("System Prompt & Instructions", fontSize = 12.sp) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onDefineSubagent(
                                        AntigravityOptimizer.SubagentSpec(
                                            name = newName.trim(),
                                            role = newRole.ifBlank { "Custom Subagent" },
                                            description = newDesc.ifBlank { newPrompt.take(100) },
                                            model = newModel,
                                            workspaceMode = newWorkspaceMode,
                                            systemPrompt = newPrompt.ifBlank { "You are a specialized Antigravity subagent." }
                                        )
                                    )
                                    newName = ""
                                    newRole = ""
                                    newPrompt = ""
                                    showCreateForm = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Subagent", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }

        items(subagents, key = { it.id }) { spec ->
            val currentTask = taskPrompts[spec.id].orEmpty()
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = spec.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${spec.model} • ${spec.workspaceMode}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (!spec.isBuiltIn) {
                            IconButton(
                                onClick = { onDeleteSubagent(spec.name) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = spec.role,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = spec.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = currentTask,
                            onValueChange = { taskPrompts = taskPrompts + (spec.id to it) },
                            placeholder = { Text("Task for ${spec.name}...", fontSize = 11.5.sp, maxLines = 1) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onInvokeSubagent(spec, currentTask) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text("Invoke", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizationsTabContent(
    activeWorkspacePath: String?,
    onSendPromptToAntigravity: (String) -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) } // 0 = Rules (GEMINI.md), 1 = Skills (SKILL.md), 2 = Hooks (hooks.json)
    var useGlobalRule by remember { mutableStateOf(activeWorkspacePath.isNullOrBlank()) }
    var rulesText by remember(activeWorkspacePath, useGlobalRule) {
        mutableStateOf(AntigravityOptimizer.readRulesContent(activeWorkspacePath, useGlobalRule))
    }
    var skillsList by remember(activeWorkspacePath) {
        mutableStateOf(AntigravityOptimizer.listSkills(activeWorkspacePath))
    }
    var showCreateSkill by remember { mutableStateOf(false) }
    var newSkillName by remember { mutableStateOf("") }
    var newSkillDesc by remember { mutableStateOf("") }
    var newSkillBody by remember { mutableStateOf("") }
    var hooksJsonText by remember(activeWorkspacePath) {
        mutableStateOf(AntigravityOptimizer.readHooksJson(activeWorkspacePath))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Equal-width 3-segment bar (Never wraps or squishes)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val subTabs = listOf("📜 Rules", "🧩 Skills", "🪝 Hooks")
                subTabs.forEachIndexed { idx, label ->
                    val selected = subTab == idx
                    Surface(
                        onClick = { subTab = idx },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (subTab) {
            0 -> {
                // Scope Toggle + Save Button Row (Clean, balanced, single-line)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(false to "Workspace", true to "Global (~/.gemini)").forEach { (isGlobal, label) ->
                            val selected = useGlobalRule == isGlobal
                            Surface(
                                onClick = { useGlobalRule = isGlobal },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val ok = AntigravityOptimizer.saveRulesContent(activeWorkspacePath, rulesText, useGlobalRule)
                            Toast.makeText(
                                context,
                                if (ok) "Saved GEMINI.md rules!" else "Failed to save rules",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save Rules",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Rule Presets (Horizontally Scrollable, Single-Line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "+ Zero-Crash Rule" to "\n- Always use defensive null-safety, try/catch around I/O & RPCs, and verify builds before finishing.",
                        "+ Bangla/EN Rule" to "\n- Explain key architectural changes clearly in Bangla & English.",
                        "+ High-Speed Code" to "\n- Prefer O(1) lookups, lazy initialization, and zero main-thread blocking."
                    )
                    presets.forEach { (label, snippet) ->
                        Surface(
                            onClick = { rulesText = (rulesText.trimEnd() + "\n" + snippet.trim()).trim() },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rulesText,
                    onValueChange = { rulesText = it },
                    placeholder = {
                        Text(
                            text = "# GEMINI.md Project Rules\n- Always write clean, tested code...",
                            fontSize = 12.sp
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 12.dp)
                )
            }
            1 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { showCreateSkill = !showCreateSkill },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (showCreateSkill) "Close Creator" else "+ Create SKILL.md",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val count = AntigravityOptimizer.migrateLegacyWorkflows(activeWorkspacePath)
                            skillsList = AntigravityOptimizer.listSkills(activeWorkspacePath)
                            Toast.makeText(context, "Migrated $count legacy workflows to SKILL.md!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Migrate Workflows",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(visible = showCreateSkill) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newSkillName,
                                onValueChange = { newSkillName = it },
                                label = { Text("Skill Name (e.g. api-tester)", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newSkillDesc,
                                onValueChange = { newSkillDesc = it },
                                label = { Text("Description (when to activate)", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newSkillBody,
                                onValueChange = { newSkillBody = it },
                                label = { Text("SKILL.md Markdown Instructions", fontSize = 12.sp) },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (AntigravityOptimizer.createOrUpdateSkill(
                                            activeWorkspacePath,
                                            newSkillName,
                                            newSkillDesc,
                                            newSkillBody,
                                            isGlobal = activeWorkspacePath.isNullOrBlank()
                                        )
                                    ) {
                                        skillsList = AntigravityOptimizer.listSkills(activeWorkspacePath)
                                        newSkillName = ""
                                        newSkillDesc = ""
                                        newSkillBody = ""
                                        showCreateSkill = false
                                        Toast.makeText(context, "SKILL.md created!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save SKILL.md", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(skillsList, key = { it.path }) { skill ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = skill.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = skill.scopeLabel,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = skill.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = {
                                        onSendPromptToAntigravity("Use skill `${skill.name}` (${skill.path}) for my current workspace task.")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Activate", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "hooks.json (SessionStart, BeforeTool, AfterTool)",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Button(
                        onClick = {
                            val ok = AntigravityOptimizer.saveHooksJson(activeWorkspacePath, hooksJsonText)
                            Toast.makeText(context, if (ok) "Saved hooks.json!" else "Failed to save hooks", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Save Hooks", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = hooksJsonText,
                    onValueChange = { hooksJsonText = it },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ScheduleTabContent(
    scheduledTasks: List<AntigravityOptimizer.ScheduledTaskItem>,
    onAddTask: (String, String, Int, String, String, Boolean) -> Unit,
    onToggleTask: (String) -> Unit,
    onRunNow: (String) -> Unit,
    onRemoveTask: (String) -> Unit
) {
    var mode by remember { mutableStateOf("timer") } // "timer" or "cron"
    var prompt by remember { mutableStateOf("") }
    var durationSec by remember { mutableIntStateOf(300) }
    var cronExpr by remember { mutableStateOf("*/15 * * * *") }
    var isDaemon by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "⏰ Schedule Antigravity Task (/schedule)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = mode == "timer",
                            onClick = { mode = "timer" },
                            label = { Text("One-Shot Timer", fontSize = 11.5.sp, maxLines = 1, softWrap = false) }
                        )
                        FilterChip(
                            selected = mode == "cron",
                            onClick = { mode = "cron" },
                            label = { Text("Recurring Cron", fontSize = 11.5.sp, maxLines = 1, softWrap = false) }
                        )
                    }

                    if (mode == "timer") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(60 to "1m", 300 to "5m", 900 to "15m", 1800 to "30m").forEach { (sec, label) ->
                                FilterChip(
                                    selected = durationSec == sec,
                                    onClick = { durationSec = sec },
                                    label = { Text(label, fontSize = 11.5.sp, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("*/5 * * * *" to "Every 5m", "*/15 * * * *" to "Every 15m", "0 * * * *" to "Hourly").forEach { (expr, label) ->
                                FilterChip(
                                    selected = cronExpr == expr,
                                    onClick = { cronExpr = expr },
                                    label = { Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        placeholder = { Text("Prompt to run when timer/cron fires...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (prompt.isNotBlank()) {
                                onAddTask(prompt, mode, durationSec, cronExpr, "never", isDaemon)
                                prompt = ""
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Schedule Task", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        items(scheduledTasks, key = { it.id }) { item ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = item.prompt,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${if (item.mode == "cron") "Cron: ${item.cronExpression}" else "Timer: ${item.durationSeconds}s"} • Runs: ${item.runCount}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onRunNow(item.prompt) }) {
                            Text("Run", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                        Switch(checked = item.isActive, onCheckedChange = { onToggleTask(item.id) })
                        IconButton(onClick = { onRemoveTask(item.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SandboxPoliciesTabContent(
    onOpenFullSecuritySettings: () -> Unit,
    onSendSlashCommand: (String) -> Unit
) {
    val slashCommands = listOf(
        "/boost" to "Deep multi-perspective reasoning, strategic planning & rigorous verification",
        "/plan" to "Create a structured step-by-step implementation plan before execution",
        "/goal" to "Autonomous long-running mode — continues until the goal is 100% achieved",
        "/grill-me" to "Interactive design interview to align on complex requirements",
        "/teamwork-preview" to "Coordinate multiple autonomous subagents in parallel",
        "/learn" to "Persist new workspace rules & learned conventions into GEMINI.md",
        "/schedule" to "Schedule recurring cron jobs or one-shot background timers"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🛡️ Antigravity PC Security & Sandbox",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configure Terminal Auto-Execution Policy (Turbo / Eager / Review), Non-Workspace File Access, Artifact Review Mode & Permission Grants.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenFullSecuritySettings,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open Security & Permission Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "ANTIGRAVITY PC SLASH COMMANDS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(slashCommands, key = { it.first }) { (cmd, desc) ->
            Surface(
                onClick = { onSendSlashCommand("$cmd ") },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = cmd,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = desc,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
