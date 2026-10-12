package com.example.gemini.ui.harness

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.harness.*
import com.example.gemini.data.local.LocalEnvironmentManager
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.theme.ChatGptEmerald
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.QuotaGreen
import com.example.gemini.theme.QuotaRed
import com.example.gemini.ui.components.AppToastHelper
import com.example.gemini.ui.components.ChatToastType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HarnessTab(val label: String, val icon: ImageVector) {
    RUNNER("Agent", Icons.Outlined.SmartToy),
    PROJECTS("Projects", Icons.Outlined.FolderCopy),
    DIFF("Changes", Icons.Outlined.Difference),
    BUILD("Android Build", Icons.Outlined.Android),
    SETTINGS("Engines", Icons.Outlined.Settings)
}

enum class StudioMode {
    CLASSIC_JIN,
    FUSION_HYBRID,
    HARNESS_STUDIO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarnessStudioScreen(
    onClose: () -> Unit,
    onNavigateToChat: () -> Unit = {},
    onNavigateToIde: () -> Unit = {},
    onNavigateToTerminal: () -> Unit = {},
    onSwitchToFusion: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(HarnessTab.RUNNER) }
    var currentMode by remember { mutableStateOf(StudioMode.HARNESS_STUDIO) }

    // Projects & Checkpoint State
    val projectsDir = remember {
        val f = File("/data/user/0/com.termux/files/home/projects")
        if (f.exists()) f else context.filesDir
    }
    var activeProjectId by remember { mutableStateOf("default-app") }
    var activeWorkspacePath by remember { mutableStateOf(projectsDir.absolutePath) }

    val checkpoints = remember { WorkspaceCheckpoints(context.filesDir, context) }
    var changeItems by remember { mutableStateOf<List<ChangeItem>>(emptyList()) }
    var isDiffLoading by remember { mutableStateOf(false) }

    // Runner State
    var selectedAgent by remember { mutableStateOf(AgentKind.DEEPSEEK_HARNESS) }
    var selectedProvider by remember { mutableStateOf(ProviderKind.OPENCODE_ZEN) }
    var customModelName by remember { mutableStateOf("deepseek-v4-flash") }
    var runnerPrompt by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var reasoningText by remember { mutableStateOf("") }
    var isReasoningExpanded by remember { mutableStateOf(true) }
    var agentLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var finalResult by remember { mutableStateOf("") }

    // Android Build State
    var buildLogs by remember { mutableStateOf("") }
    var isBuilding by remember { mutableStateOf(false) }
    var detectedApk by remember { mutableStateOf<File?>(null) }

    // Refresh changes diff helper
    fun refreshChanges() {
        coroutineScope.launch(Dispatchers.IO) {
            isDiffLoading = true
            try {
                val workspace = File(activeWorkspacePath)
                val changedPaths = checkpoints.readChangedPaths(activeProjectId)
                val items = checkpoints.buildChangeDetails(activeProjectId, workspace, changedPaths)
                withContext(Dispatchers.Main) {
                    changeItems = items
                    isDiffLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDiffLoading = false
                }
            }
        }
    }

    LaunchedEffect(activeProjectId, activeWorkspacePath) {
        refreshChanges()
        withContext(Dispatchers.IO) {
            val workspace = File(activeWorkspacePath)
            val apk = AndroidBuildSupport.findDebugApk(workspace)
            withContext(Dispatchers.Main) {
                detectedApk = apk
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Harness Studio",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ClaudeTerracotta.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "AUTONOMOUS",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ClaudeTerracotta,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "by Samir Ahmmed · DeepSeek & OpenCode Zen Engine",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    // Quick Jump Icons
                    IconButton(onClick = onNavigateToTerminal, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.Terminal, contentDescription = "Terminal", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onNavigateToIde, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.Code, contentDescription = "IDE", modifier = Modifier.size(18.dp))
                    }
                }

                // Mode Switcher Banner (Classic JIN vs Fusion vs Harness Studio)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onNavigateToChat()
                            }
                    ) {
                        Text(
                            text = "💬 JIN Classic",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 5.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onSwitchToFusion()
                            }
                    ) {
                        Text(
                            text = "⚡ JIN Fusion",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 5.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ClaudeTerracotta,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "🚀 Harness Studio",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 5.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Sub Navigation Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = ClaudeTerracotta,
                    divider = {}
                ) {
                    HarnessTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = tab.label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                HarnessTab.RUNNER -> {
                    AgentRunnerTab(
                        selectedAgent = selectedAgent,
                        onAgentChange = { selectedAgent = it },
                        selectedProvider = selectedProvider,
                        onProviderChange = {
                            selectedProvider = it
                            customModelName = it.defaultModel
                        },
                        modelName = customModelName,
                        onModelNameChange = { customModelName = it },
                        workspacePath = activeWorkspacePath,
                        prompt = runnerPrompt,
                        onPromptChange = { runnerPrompt = it },
                        isRunning = isRunning,
                        reasoningText = reasoningText,
                        isReasoningExpanded = isReasoningExpanded,
                        onToggleReasoning = { isReasoningExpanded = !isReasoningExpanded },
                        agentLogs = agentLogs,
                        finalResult = finalResult,
                        onRunTask = {
                            if (runnerPrompt.isBlank()) {
                                AppToastHelper.showToast("Please enter a task prompt for the agent", ChatToastType.INFO)
                                return@AgentRunnerTab
                            }
                            isRunning = true
                            reasoningText = ""
                            agentLogs = listOf("🚀 Autonomous session initialized with ${selectedAgent.title}...")
                            finalResult = ""

                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val profile = ProviderProfile(
                                        kind = selectedProvider,
                                        model = customModelName
                                    )
                                    val engine = JinAutonomousEngine(context)
                                    engine.executeTask(
                                        projectId = activeProjectId,
                                        prompt = runnerPrompt,
                                        agent = selectedAgent,
                                        provider = profile,
                                        customModel = customModelName
                                    ) { event ->
                                        when (event) {
                                            is RuntimeEvent.ReasoningDelta -> {
                                                withContext(Dispatchers.Main) {
                                                    reasoningText += event.text
                                                }
                                            }
                                            is RuntimeEvent.AssistantDelta -> {
                                                withContext(Dispatchers.Main) {
                                                    finalResult += event.text
                                                }
                                            }
                                            is RuntimeEvent.ToolStarted -> {
                                                withContext(Dispatchers.Main) {
                                                    agentLogs = agentLogs + "⚡ [Tool] ${event.toolName}: ${event.detail}"
                                                }
                                            }
                                            is RuntimeEvent.ToolCompleted -> {
                                                withContext(Dispatchers.Main) {
                                                    agentLogs = agentLogs + "✅ [Tool] ${event.toolName}: ${event.summary}"
                                                }
                                            }
                                            is RuntimeEvent.RuntimeLog -> {
                                                withContext(Dispatchers.Main) {
                                                    agentLogs = agentLogs + "ℹ️ ${event.title}: ${event.detail}"
                                                }
                                            }
                                            is RuntimeEvent.FilesChanged -> {
                                                withContext(Dispatchers.Main) {
                                                    changeItems = event.changes
                                                }
                                            }
                                            is RuntimeEvent.SessionCompleted -> {
                                                withContext(Dispatchers.Main) {
                                                    isRunning = false
                                                    finalResult = event.finalAnswer.ifBlank { finalResult }
                                                    agentLogs = agentLogs + "✅ Task execution complete."
                                                    refreshChanges()
                                                    AppToastHelper.showToast("Agent completed the coding task!", ChatToastType.SUCCESS)
                                                }
                                            }
                                            is RuntimeEvent.SessionFailed -> {
                                                withContext(Dispatchers.Main) {
                                                    isRunning = false
                                                    agentLogs = agentLogs + "❌ Failed: ${event.reason}"
                                                    AppToastHelper.showToast(event.reason, ChatToastType.ERROR)
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isRunning = false
                                        agentLogs = agentLogs + "❌ Error: ${e.message}"
                                    }
                                }
                            }

                        }
                    )
                }

                HarnessTab.PROJECTS -> {
                    ProjectsManagerTab(
                        projectsDir = projectsDir,
                        activeWorkspace = activeWorkspacePath,
                        onSelectWorkspace = { newPath ->
                            activeWorkspacePath = newPath
                            activeProjectId = File(newPath).name
                            refreshChanges()
                        },
                        onNavigateToIde = onNavigateToIde,
                        onNavigateToTerminal = onNavigateToTerminal
                    )
                }

                HarnessTab.DIFF -> {
                    ChangesDiffTab(
                        projectId = activeProjectId,
                        workspacePath = activeWorkspacePath,
                        changeItems = changeItems,
                        isLoading = isDiffLoading,
                        onUndoChange = { item ->
                            coroutineScope.launch(Dispatchers.IO) {
                                checkpoints.undoChange(activeProjectId, File(activeWorkspacePath), item.path)
                                refreshChanges()
                                withContext(Dispatchers.Main) {
                                    AppToastHelper.showToast("Reverted ${item.path}", ChatToastType.SUCCESS)
                                }
                            }
                        },
                        onKeepChange = { item ->
                            coroutineScope.launch(Dispatchers.IO) {
                                checkpoints.keepChange(activeProjectId, item.path)
                                refreshChanges()
                                withContext(Dispatchers.Main) {
                                    AppToastHelper.showToast("Accepted ${item.path}", ChatToastType.SUCCESS)
                                }
                            }
                        },
                        onUndoAll = {
                            coroutineScope.launch(Dispatchers.IO) {
                                changeItems.forEach { item ->
                                    checkpoints.undoChange(activeProjectId, File(activeWorkspacePath), item.path)
                                }
                                refreshChanges()
                                withContext(Dispatchers.Main) {
                                    AppToastHelper.showToast("All changes undone!", ChatToastType.SUCCESS)
                                }
                            }
                        },
                        onKeepAll = {
                            coroutineScope.launch(Dispatchers.IO) {
                                checkpoints.checkpointDir(activeProjectId).deleteRecursively()
                                checkpoints.createCheckpoint(activeProjectId, File(activeWorkspacePath))
                                refreshChanges()
                                withContext(Dispatchers.Main) {
                                    AppToastHelper.showToast("Baseline updated! All changes saved.", ChatToastType.SUCCESS)
                                }
                            }
                        },
                        onRefresh = { refreshChanges() }
                    )
                }

                HarnessTab.BUILD -> {
                    AndroidBuildTab(
                        workspacePath = activeWorkspacePath,
                        detectedApk = detectedApk,
                        buildLogs = buildLogs,
                        isBuilding = isBuilding,
                        onStartBuild = {
                            isBuilding = true
                            buildLogs = "🔨 Resolving Android project and starting Gradle assembleDebug...\n"
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val workspace = File(activeWorkspacePath)
                                    val cmd = AndroidBuildSupport.androidGradleCommand(workspace, "assembleDebug")
                                    withContext(Dispatchers.Main) {
                                        buildLogs += "$ $cmd\n"
                                    }
                                    val res = LocalEnvironmentManager.executeCommand(cmd, context, workspace.absolutePath)
                                    withContext(Dispatchers.Main) {
                                        buildLogs += res.output
                                    }
                                    val apk = AndroidBuildSupport.findDebugApk(workspace)
                                    withContext(Dispatchers.Main) {
                                        detectedApk = apk
                                        isBuilding = false
                                        if (apk != null) {
                                            buildLogs += "\n✅ Build succeeded! APK generated at: ${apk.name} (${apk.length() / 1024 / 1024} MB)\n"
                                            AppToastHelper.showToast("Build Succeeded! APK Ready to install.", ChatToastType.SUCCESS)
                                        } else {
                                            buildLogs += "\n⚠️ Build finished with exit code ${res.exitCode}\n"
                                        }
                                    }

                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isBuilding = false
                                        buildLogs += "\n❌ Build failed: ${e.message}\n"
                                        AppToastHelper.showToast("Build Failed: ${e.message}", ChatToastType.ERROR)
                                    }
                                }
                            }
                        },
                        onInstallApk = { apk ->
                            try {
                                AndroidAppInstaller.install(context, apk, launchAfterInstall = true)
                                AppToastHelper.showToast("Installing ${apk.name}...", ChatToastType.SUCCESS)
                            } catch (e: Exception) {
                                AppToastHelper.showToast("Install error: ${e.message}", ChatToastType.ERROR)
                            }
                        },
                        onLaunchApp = { apk ->
                            val pkg = AndroidAppInstaller.archivePackageName(context, apk)
                            if (pkg != null) {
                                AndroidAppInstaller.launch(context, pkg)
                            } else {
                                AppToastHelper.showToast("Package name could not be resolved", ChatToastType.INFO)
                            }
                        }
                    )
                }

                HarnessTab.SETTINGS -> {
                    RuntimesAndEnginesTab(context = context)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 0: AGENT RUNNER COMPONENT
// -----------------------------------------------------------------------------
@Composable
private fun AgentRunnerTab(
    selectedAgent: AgentKind,
    onAgentChange: (AgentKind) -> Unit,
    selectedProvider: ProviderKind,
    onProviderChange: (ProviderKind) -> Unit,
    modelName: String,
    onModelNameChange: (String) -> Unit,
    workspacePath: String,
    prompt: String,
    onPromptChange: (String) -> Unit,
    isRunning: Boolean,
    reasoningText: String,
    isReasoningExpanded: Boolean,
    onToggleReasoning: () -> Unit,
    agentLogs: List<String>,
    finalResult: String,
    onRunTask: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Agent Engine & Provider Selector Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🤖 Agent Configuration",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Agent Kind Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AgentKind.entries.forEach { agent ->
                        val isSel = selectedAgent == agent
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) ClaudeTerracotta else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isSel) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAgentChange(agent) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = agent.title,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = agent.downloadNote,
                                    fontSize = 8.5.sp,
                                    color = if (isSel) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

                // Provider Kind & Model
                var showProviderDialog by remember { mutableStateOf(false) }
                var showModelDialog by remember { mutableStateOf(false) }

                if (showProviderDialog) {
                    ProviderSelectionDialog(
                        currentProvider = selectedProvider,
                        onSelectProvider = { prov ->
                            onProviderChange(prov)
                        },
                        onDismiss = { showProviderDialog = false }
                    )
                }

                if (showModelDialog) {
                    ModelSelectionDialog(
                        provider = selectedProvider,
                        currentModel = modelName,
                        onSelectModel = { model ->
                            onModelNameChange(model)
                        },
                        onDismiss = { showModelDialog = false }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Provider Kind Selector (Interactive Clickable Card)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ClaudeTerracotta.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clickable { showProviderDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PROVIDER",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = selectedProvider.title,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ClaudeTerracotta,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.ArrowDropDown,
                                contentDescription = "Select Provider",
                                tint = ClaudeTerracotta,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Model Name Selector (Interactive Clickable Card)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                        modifier = Modifier
                            .weight(1.15f)
                            .heightIn(min = 52.dp)
                            .clickable { showModelDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MODEL",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = modelName.ifBlank { "Select Model" },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Edit Model",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "📂 Target Workspace: ${File(workspacePath).name}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Prompt Input Box
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            placeholder = { Text("Describe the task, bug to fix, or Android feature to code...", fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ClaudeTerracotta,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
        )

        // Run Button
        Button(
            onClick = onRunTask,
            enabled = !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta)
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Agent Executing Coding Turns...", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("🚀 Launch Autonomous Coding Agent", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Live Reasoning Accordion
        if (reasoningText.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, ClaudeTerracotta.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleReasoning() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🧠 Agent Reasoning Stream",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ClaudeTerracotta
                            )
                            if (isRunning) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = ClaudeTerracotta)
                            }
                        }
                        Icon(
                            imageVector = if (isReasoningExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = ClaudeTerracotta,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isReasoningExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = reasoningText,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // Live Logs & Execution Steps
        if (agentLogs.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "📋 Execution Log",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    agentLogs.takeLast(10).forEach { log ->
                        Text(
                            text = log,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        // Final Result Box
        if (finalResult.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, QuotaGreen.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "✨ Agent Solution / Summary",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = QuotaGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = finalResult,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// PROVIDER & MODEL SELECTION DIALOGS
// -----------------------------------------------------------------------------

fun getPresetModelsForProvider(provider: ProviderKind): List<String> = when (provider) {
    ProviderKind.OPENCODE_ZEN -> listOf(
        "deepseek-v4-flash",
        "deepseek-chat",
        "deepseek-reasoner",
        "qwen-2.5-coder-32b",
        "minimax-01"
    )
    ProviderKind.DEEPSEEK -> listOf(
        "deepseek-chat",
        "deepseek-reasoner",
        "deepseek-coder"
    )
    ProviderKind.LLM_ROUTER -> listOf(
        "anthropic/claude-3.7-sonnet",
        "deepseek/deepseek-r1",
        "deepseek/deepseek-chat",
        "google/gemini-2.0-flash-001",
        "openai/gpt-4o",
        "meta-llama/llama-3.3-70b-instruct"
    )
    ProviderKind.CLAUDE, ProviderKind.ANTHROPIC -> listOf(
        "claude-3-7-sonnet-20250219",
        "claude-3-5-sonnet-20241022",
        "claude-3-5-haiku-20241022",
        "claude-3-opus-20240229"
    )
    ProviderKind.KIMI -> listOf(
        "kimi-k2.6",
        "moonshot-v1-8k",
        "moonshot-v1-32k",
        "moonshot-v1-128k"
    )
    ProviderKind.NVIDIA_NIM -> listOf(
        "qwen/qwen2.5-coder-32b-instruct",
        "meta/llama-3.3-70b-instruct",
        "deepseek-ai/deepseek-r1"
    )
    ProviderKind.CUSTOM -> listOf(
        "custom-model"
    )
}

@Composable
fun ProviderSelectionDialog(
    currentProvider: ProviderKind,
    onSelectProvider: (ProviderKind) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Dns,
                    contentDescription = null,
                    tint = ClaudeTerracotta,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Select AI Provider",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ProviderKind.entries) { provider ->
                    val isSelected = provider == currentProvider
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) ClaudeTerracotta.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, if (isSelected) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectProvider(provider)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = provider.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = ClaudeTerracotta.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = provider.protocol.name,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ClaudeTerracotta,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = provider.subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                if (provider.defaultBaseUrl.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = provider.defaultBaseUrl,
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = ClaudeTerracotta,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ClaudeTerracotta, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ModelSelectionDialog(
    provider: ProviderKind,
    currentModel: String,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchOrCustom by remember { mutableStateOf(currentModel) }
    val presets = remember(provider) { getPresetModelsForProvider(provider) }
    val filteredPresets = remember(presets, searchOrCustom) {
        val q = searchOrCustom.trim()
        if (q.isBlank()) presets else presets.filter { it.contains(q, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = ClaudeTerracotta,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Select / Custom Model",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "For ${provider.title}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchOrCustom,
                    onValueChange = { searchOrCustom = it },
                    label = { Text("Model Name / ID", fontSize = 11.sp) },
                    placeholder = { Text("e.g. deepseek-v4-flash", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        if (searchOrCustom.isNotBlank()) {
                            IconButton(onClick = {
                                if (searchOrCustom.isNotBlank()) {
                                    onSelectModel(searchOrCustom.trim())
                                    onDismiss()
                                }
                            }) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "Apply",
                                    tint = ClaudeTerracotta,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Recommended for ${provider.title}:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredPresets) { preset ->
                        val isCurrent = preset == currentModel
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) ClaudeTerracotta.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, if (isCurrent) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectModel(preset)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Selected",
                                        tint = ClaudeTerracotta,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (searchOrCustom.isNotBlank()) {
                        onSelectModel(searchOrCustom.trim())
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta)
            ) {
                Text("Apply Model", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 12.sp)
            }
        }
    )
}

// -----------------------------------------------------------------------------
// TAB 1: PROJECTS & TEMPLATES COMPONENT
// -----------------------------------------------------------------------------
@Composable
private fun ProjectsManagerTab(
    projectsDir: File,
    activeWorkspace: String,
    onSelectWorkspace: (String) -> Unit,
    onNavigateToIde: () -> Unit,
    onNavigateToTerminal: () -> Unit
) {
    var newAppName by remember { mutableStateOf("MyFirstApp") }
    var selectedTemplate by remember { mutableStateOf(AndroidTemplate.COMPOSE) }
    var isCreating by remember { mutableStateOf(false) }

    val projectList = remember(projectsDir, isCreating) {
        projectsDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Scaffolding New Project Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "📦 Create New Android Project",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = newAppName,
                    onValueChange = { newAppName = it },
                    label = { Text("App Name", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AndroidTemplate.entries.forEach { tmpl ->
                        val isSel = selectedTemplate == tmpl
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) ClaudeTerracotta else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isSel) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTemplate = tmpl }
                        ) {
                            Text(
                                text = tmpl.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 7.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Text(
                    text = "Package: ${AndroidProjectTemplateGenerator.packageName(newAppName)} · Author: Samir Ahmmed",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Button(
                    onClick = {
                        val destination = File(projectsDir, projectSlug(newAppName))
                        try {
                            AndroidProjectTemplateGenerator.generate(destination, newAppName, selectedTemplate)
                            onSelectWorkspace(destination.absolutePath)
                            AppToastHelper.showToast("Generated Android project: ${destination.name}", ChatToastType.SUCCESS)
                        } catch (e: Exception) {
                            AppToastHelper.showToast("Failed to generate: ${e.message}", ChatToastType.ERROR)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta)
                ) {
                    Text("🚀 Scaffolding & Generate Project", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Existing Workspaces List
        Text(
            text = "📁 Existing Workspaces (${projectList.size})",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        projectList.forEach { proj ->
            val isCurrent = proj.absolutePath == activeWorkspace
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isCurrent) ClaudeTerracotta.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, if (isCurrent) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectWorkspace(proj.absolutePath) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = proj.name,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isCurrent) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = ClaudeTerracotta
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = proj.absolutePath,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onNavigateToIde, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.Code, contentDescription = "IDE", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onNavigateToTerminal, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.Terminal, contentDescription = "Terminal", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 2: CHANGES DIFF & CHECKPOINTS COMPONENT
// -----------------------------------------------------------------------------
@Composable
private fun ChangesDiffTab(
    projectId: String,
    workspacePath: String,
    changeItems: List<ChangeItem>,
    isLoading: Boolean,
    onUndoChange: (ChangeItem) -> Unit,
    onKeepChange: (ChangeItem) -> Unit,
    onUndoAll: () -> Unit,
    onKeepAll: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Summary & Global Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Changes Diff (${changeItems.size} files)",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Line-by-line snapshot tracking & undo engine",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                }
                if (changeItems.isNotEmpty()) {
                    Button(
                        onClick = onUndoAll,
                        colors = ButtonDefaults.buttonColors(containerColor = QuotaRed.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Undo All", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onKeepAll,
                        colors = ButtonDefaults.buttonColors(containerColor = QuotaGreen),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Keep All", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ClaudeTerracotta)
            }
        } else if (changeItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(30.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = QuotaGreen,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Workspace is Clean",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "No uncommitted modifications detected against baseline checkpoint.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(changeItems, key = { it.path }) { item ->
                    var isExpanded by remember { mutableStateOf(false) }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isExpanded = !isExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.path,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = "+${item.additions}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = QuotaGreen)
                                        Text(text = "-${item.deletions}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = QuotaRed)
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onUndoChange(item) },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(26.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text("Undo", fontSize = 9.5.sp, color = QuotaRed)
                                    }
                                    Button(
                                        onClick = { onKeepChange(item) },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = QuotaGreen),
                                        modifier = Modifier.height(26.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text("Keep", fontSize = 9.5.sp)
                                    }
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        item.diffLines.take(50).forEach { diff ->
                                            val bg = when (diff.type) {
                                                DiffLineType.ADDITION -> Color(0x3310B981)
                                                DiffLineType.DELETION -> Color(0x33EF4444)
                                                else -> Color.Transparent
                                            }
                                            val color = when (diff.type) {
                                                DiffLineType.ADDITION -> Color(0xFF34D399)
                                                DiffLineType.DELETION -> Color(0xFFF87171)
                                                else -> Color(0xFF94A3B8)
                                            }
                                            Text(
                                                text = diff.text,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = color,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(bg)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 3: ANDROID BUILD & INSTALL COMPONENT
// -----------------------------------------------------------------------------
@Composable
private fun AndroidBuildTab(
    workspacePath: String,
    detectedApk: File?,
    buildLogs: String,
    isBuilding: Boolean,
    onStartBuild: () -> Unit,
    onInstallApk: (File) -> Unit,
    onLaunchApp: (File) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Build Action Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🔨 On-Device Android Build",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Compiles APK using on-device Gradle, AAPT2 & D8",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Button(
                        onClick = onStartBuild,
                        enabled = !isBuilding,
                        colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        if (isBuilding) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Building...", fontSize = 11.sp)
                        } else {
                            Text("Run Gradle Build", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Detected APK status
                if (detectedApk != null && detectedApk.exists()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = QuotaGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, QuotaGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ready APK: ${detectedApk.name}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = QuotaGreen
                                )
                                Text(
                                    text = "Size: ${detectedApk.length() / 1024 / 1024} MB · ${detectedApk.parentFile?.name}",
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { onInstallApk(detectedApk) },
                                    colors = ButtonDefaults.buttonColors(containerColor = QuotaGreen),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("📲 Install", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onLaunchApp(detectedApk) },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("🚀 Launch", fontSize = 10.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Build Logs Terminal
        Text(
            text = "Console Output",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0F172A),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val scrollState = rememberScrollState()
            LaunchedEffect(buildLogs) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            Text(
                text = buildLogs.ifBlank { "Ready to build. Tap 'Run Gradle Build' above to assemble debug APK on-device." },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFCBD5E1),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .verticalScroll(scrollState)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 4: RUNTIMES & ENGINES COMPONENT
// -----------------------------------------------------------------------------
@Composable
private fun RuntimesAndEnginesTab(context: Context) {
    val installer = remember { HarnessRuntimeInstaller(context) }
    var isInstalling by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "⚙️ Runtime Bundles & AI Engines",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Manages the on-device PRoot/Termux subsystem, Python, Android SDK, and DeepSeek Harness.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        HarnessRuntimeInstaller.BUNDLES.values.forEach { bundle ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = bundle.description,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ClaudeTerracotta.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "v${bundle.version}",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ClaudeTerracotta,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "${bundle.fileName} · ${bundle.compressedBytes / 1024 / 1024} MB",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Installed",
                        tint = QuotaGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                isInstalling = true
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        installer.installCore()
                        withContext(Dispatchers.Main) {
                            isInstalling = false
                            AppToastHelper.showToast("All runtime bundles verified & installed!", ChatToastType.SUCCESS)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isInstalling = false
                            AppToastHelper.showToast("Installation: ${e.message}", ChatToastType.INFO)
                        }
                    }
                }
            },
            enabled = !isInstalling,
            modifier = Modifier.fillMaxWidth().height(42.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta)
        ) {
            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verifying Runtimes...", fontSize = 12.sp)
            } else {
                Text("🔄 Verify & Update Runtime Bundles", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // AI Provider Keys Section
        Text(
            text = "🔑 AI Provider API Keys",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Configure API keys for DeepSeek, OpenRouter, Anthropic, or OpenCode Zen.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        val authPrefs = remember { AuthPreferences(context) }
        var deepseekKey by remember { mutableStateOf(authPrefs.getDeepSeekApiKeySync()) }
        var openRouterKey by remember { mutableStateOf(authPrefs.getOpenRouterApiKeySync()) }
        var anthropicKey by remember { mutableStateOf(authPrefs.getAnthropicApiKeySync()) }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = deepseekKey,
                    onValueChange = { deepseekKey = it },
                    label = { Text("DeepSeek API Key (sk-...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = openRouterKey,
                    onValueChange = { openRouterKey = it },
                    label = { Text("OpenRouter API Key (sk-or-...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = anthropicKey,
                    onValueChange = { anthropicKey = it },
                    label = { Text("Anthropic API Key (sk-ant-...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = {
                        coroutineScope.launch {
                            authPrefs.saveDeepSeekApiKey(deepseekKey)
                            authPrefs.saveOpenRouterApiKey(openRouterKey)
                            authPrefs.saveAnthropicApiKey(anthropicKey)
                            AppToastHelper.showToast("API keys saved successfully!", ChatToastType.SUCCESS)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta)
                ) {
                    Text("💾 Save API Keys", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
