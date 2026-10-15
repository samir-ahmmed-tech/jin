package com.example.gemini.ui.agents

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemini.data.agent.custom.*
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.QuotaGreen
import com.example.gemini.ui.components.AppToastHelper
import com.example.gemini.ui.components.ChatToastType
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiAgentStudioDialog(
    onDismiss: () -> Unit,
    onDispatchToChat: (prompt: String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agentManager = remember { CustomAgentManager.getInstance(context) }
    val agents by agentManager.agents.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Agents, 1: Swarm Dispatcher
    var editingAgent by remember { mutableStateOf<CustomAgent?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🤖 Multi-Agent Studio",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Devin, Manus & Grok inspired autonomous agent ecosystem",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Button(
                        onClick = {
                            editingAgent = CustomAgent(
                                name = "New Specialist Agent",
                                role = AgentSpecialtyRole.FULL_STACK_DEV,
                                avatarEmoji = "⚡",
                                systemPrompt = "You are an autonomous coding assistant specializing in precision execution."
                            )
                            isCreatingNew = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ New Agent", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Segmented Tabs: 0: Agents Suite, 1: Swarm Dispatcher
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    contentColor = ClaudeTerracotta
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Agents (${agents.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("🚀 Swarm Orchestrator", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }

                if (selectedTab == 0) {
                    // List of Agents
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(agents, key = { it.id }) { agent ->
                            AgentCard(
                                agent = agent,
                                onEdit = {
                                    editingAgent = agent
                                    isCreatingNew = false
                                },
                                onDuplicate = {
                                    scope.launch {
                                        agentManager.duplicateAgent(agent.id)
                                        AppToastHelper.showToast("Duplicated ${agent.name}", ChatToastType.SUCCESS)
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        val ok = agentManager.deleteAgent(agent.id)
                                        if (ok) AppToastHelper.showToast("Deleted ${agent.name}", ChatToastType.INFO)
                                        else AppToastHelper.showToast("Built-in templates cannot be deleted", ChatToastType.ERROR)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // Swarm Dispatcher View
                    SwarmDispatcherTab(
                        agents = agents,
                        onPlanSwarm = { goal, selected ->
                            agentManager.planSwarmSteps(goal, selected)
                        },
                        onDispatch = { prompt ->
                            onDismiss()
                            onDispatchToChat(prompt)
                        }
                    )
                }
            }
        }
    }

    // Agent Edit / Creation Modal
    editingAgent?.let { agent ->
        AgentEditorDialog(
            initialAgent = agent,
            isNew = isCreatingNew,
            onDismiss = { editingAgent = null },
            onSave = { updated ->
                scope.launch {
                    agentManager.saveAgent(updated)
                    AppToastHelper.showToast("Saved ${updated.name} successfully!", ChatToastType.SUCCESS)
                    editingAgent = null
                }
            }
        )
    }
}

@Composable
private fun AgentCard(
    agent: CustomAgent,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val roleColor = try {
        Color(android.graphics.Color.parseColor(agent.role.badgeColorHex))
    } catch (_: Exception) {
        ClaudeTerracotta
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = roleColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(agent.avatarEmoji, fontSize = 20.sp)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = agent.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (agent.isBuiltInTemplate) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = QuotaGreen.copy(alpha = 0.15f)
                            ) {
                                Text("PRESET", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = QuotaGreen, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Text(
                        text = agent.role.title,
                        fontSize = 11.sp,
                        color = roleColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "Duplicate", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
                if (!agent.isBuiltInTemplate) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFEF4444).copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = agent.systemPrompt,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "🧠 ${agent.modelId}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "🛠️ ${agent.enabledTools.size} Tools",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentEditorDialog(
    initialAgent: CustomAgent,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (CustomAgent) -> Unit
) {
    var name by remember { mutableStateOf(initialAgent.name) }
    var role by remember { mutableStateOf(initialAgent.role) }
    var avatar by remember { mutableStateOf(initialAgent.avatarEmoji) }
    var modelId by remember { mutableStateOf(initialAgent.modelId) }
    var systemPrompt by remember { mutableStateOf(initialAgent.systemPrompt) }
    var temperature by remember { mutableFloatStateOf(initialAgent.temperature) }
    var selectedTools by remember { mutableStateOf(initialAgent.enabledTools.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isNew) "Create Custom Agent" else "Edit Agent", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Agent Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = avatar,
                        onValueChange = { avatar = it.take(2) },
                        label = { Text("Avatar Emoji") },
                        modifier = Modifier.width(110.dp)
                    )
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("Model ID") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("gemini-argon", "gemini-2.5-pro", "gemini-2.5-flash", "claude-3-7-sonnet", "deepseek-chat").forEach { quickModel ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (modelId == quickModel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (modelId == quickModel) MaterialTheme.colorScheme.primary else Color.Transparent),
                            modifier = Modifier.clickable { modelId = quickModel }
                        ) {
                            Text(
                                text = quickModel,
                                fontSize = 10.sp,
                                fontWeight = if (modelId == quickModel) FontWeight.Bold else FontWeight.Normal,
                                color = if (modelId == quickModel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text("Specialty Role:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AgentSpecialtyRole.entries.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { role = r }
                        ) {
                            RadioButton(selected = role == r, onClick = { role = r })
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(r.title, fontSize = 12.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("Custom System Instructions") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Enabled Tools & Capabilities:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                AgentCapabilityTool.entries.forEach { tool ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTools = if (selectedTools.contains(tool)) selectedTools - tool else selectedTools + tool
                            }
                    ) {
                        Checkbox(
                            checked = selectedTools.contains(tool),
                            onCheckedChange = { chk ->
                                selectedTools = if (chk) selectedTools + tool else selectedTools - tool
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(tool.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(tool.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialAgent.copy(
                            name = name.ifBlank { "Specialist Agent" },
                            role = role,
                            avatarEmoji = avatar.ifBlank { "🤖" },
                            modelId = modelId.ifBlank { "gemini-2.5-flash" },
                            systemPrompt = systemPrompt,
                            temperature = temperature,
                            enabledTools = selectedTools.toList()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta)
            ) {
                Text("Save Agent", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SwarmDispatcherTab(
    agents: List<CustomAgent>,
    onPlanSwarm: suspend (goal: String, selected: List<CustomAgent>) -> List<SwarmStep>,
    onDispatch: (prompt: String) -> Unit
) {
    val context = LocalContext.current
    val agentManager = remember { CustomAgentManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var goalInput by remember { mutableStateOf("") }
    var selectedAgentIds by remember { mutableStateOf(agents.map { it.id }.toSet()) }
    var plannedSteps by remember { mutableStateOf<List<SwarmStep>>(emptyList()) }
    var isPlanning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎯 Swarm Objective", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    placeholder = { Text("e.g. Build an authentication module with SQLite and Compose UI...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Select Participating Agents:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    agents.forEach { agent ->
                        val isSelected = selectedAgentIds.contains(agent.id)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedAgentIds = if (isSelected) selectedAgentIds - agent.id else selectedAgentIds + agent.id
                            },
                            label = {
                                Text(
                                    text = "${agent.avatarEmoji} ${agent.name.take(12)}",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                    }
                }

                Button(
                    onClick = {
                        if (goalInput.isNotBlank()) {
                            isPlanning = true
                            scope.launch {
                                val active = agents.filter { selectedAgentIds.contains(it.id) }
                                plannedSteps = onPlanSwarm(goalInput, active)
                                isPlanning = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text(if (isPlanning) "Decomposing Goal..." else "⚡ Plan Multi-Agent Workflow", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (plannedSteps.isNotEmpty()) {
            Text("📋 Orchestrated Execution Pipeline (${plannedSteps.size} Steps):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            plannedSteps.forEach { step ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(step.agentAvatar, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(step.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(step.prompt, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }

            Button(
                onClick = {
                    val swarmPrompt = agentManager.formatSwarmDispatchPrompt(goalInput, plannedSteps)
                    onDispatch(swarmPrompt)
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta)
            ) {
                Icon(Icons.Outlined.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("🚀 Launch Swarm in Chat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
