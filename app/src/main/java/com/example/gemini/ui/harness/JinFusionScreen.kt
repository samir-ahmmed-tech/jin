package com.example.gemini.ui.harness

import android.content.Context
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
import com.example.gemini.ui.chat.ChatViewModel
import com.example.gemini.ui.components.AppToastHelper
import com.example.gemini.ui.components.ChatToastType
import com.example.gemini.ui.components.MarkdownContent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FusionDarkBg = Color(0xFF070B12)
private val FusionCardBg = Color(0xFF0F172A)
private val FusionAmber = Color(0xFFF59E0B)
private val FusionCyan = Color(0xFF06B6D4)
private val FusionPurple = Color(0xFF8B5CF6)
private val FusionBorder = Color(0xFF1E293B)

enum class FusionConsoleTab(val label: String, val icon: ImageVector) {
    AGENT("Agent Runner", Icons.Outlined.SmartToy),
    DIFFS("Changes & Diffs", Icons.Outlined.Difference),
    BUILD("Android Build", Icons.Outlined.Android)
}

/**
 * JIN Fusion Hybrid Interface.
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin).
 *
 * Dedicated hybrid interface uniting JIN Conversational AI with
 * Mobile-Harness Autonomous Software Engineering, live reasoning,
 * interactive diffs, and APK compilation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JinFusionScreen(
    chatViewModel: ChatViewModel,
    onClose: () -> Unit,
    onNavigateToClassic: () -> Unit,
    onNavigateToHarness: () -> Unit,
    onNavigateToIde: () -> Unit,
    onNavigateToTerminal: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Active project & workspace
    val projectsDir = remember {
        val f = File("/data/user/0/com.termux/files/home/projects")
        if (f.exists()) f else context.filesDir
    }
    var activeProjectId by remember { mutableStateOf("default-app") }
    var activeWorkspacePath by remember { mutableStateOf(projectsDir.absolutePath) }

    val checkpoints = remember { WorkspaceCheckpoints(context.filesDir, context) }
    val engine = remember { JinAutonomousEngine(context) }

    var consoleTab by remember { mutableStateOf(FusionConsoleTab.AGENT) }

    // Runner State
    var promptInput by remember { mutableStateOf("") }
    var selectedAgent by remember { mutableStateOf(AgentKind.DEEPSEEK_HARNESS) }
    var selectedProvider by remember { mutableStateOf(ProviderKind.OPENCODE_ZEN) }
    var isRunning by remember { mutableStateOf(false) }
    var reasoningText by remember { mutableStateOf("") }
    var isReasoningExpanded by remember { mutableStateOf(true) }
    var agentLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var finalResult by remember { mutableStateOf("") }

    // Diffs State
    var changeItems by remember { mutableStateOf<List<ChangeItem>>(emptyList()) }
    var isDiffLoading by remember { mutableStateOf(false) }

    // Build State
    var buildLogs by remember { mutableStateOf("") }
    var isBuilding by remember { mutableStateOf(false) }
    var detectedApk by remember { mutableStateOf<File?>(null) }

    fun refreshDiffs() {
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
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { isDiffLoading = false }
            }
        }
    }

    LaunchedEffect(activeProjectId, activeWorkspacePath) {
        refreshDiffs()
        withContext(Dispatchers.IO) {
            val workspace = File(activeWorkspacePath)
            val apk = AndroidBuildSupport.findDebugApk(workspace)
            withContext(Dispatchers.Main) { detectedApk = apk }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FusionDarkBg)
                    .statusBarsPadding()
            ) {
                // Main Header Row
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
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Title & Badge
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "JIN",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = FusionAmber,
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                Text(
                                    text = "FUSION",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Hybrid AI & Autonomous Harness · Samir Ahmmed",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Quick Nav Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onNavigateToIde, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Code, contentDescription = "IDE", tint = FusionCyan, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onNavigateToTerminal, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Terminal, contentDescription = "Terminal", tint = FusionAmber, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // 3-Mode Selector Bar (Classic | Fusion | Harness)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Mode 1: Classic
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = FusionCardBg,
                        border = BorderStroke(1.dp, FusionBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToClassic() }
                    ) {
                        Text(
                            text = "💬 Classic",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp)
                        )
                    }

                    // Mode 2: Fusion (Active)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = FusionAmber,
                        border = BorderStroke(1.dp, FusionAmber),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "⚡ Fusion",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp)
                        )
                    }

                    // Mode 3: Harness Studio
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = FusionCardBg,
                        border = BorderStroke(1.dp, FusionBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToHarness() }
                    ) {
                        Text(
                            text = "🚀 Harness",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp)
                        )
                    }
                }

                // Project Selector Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FusionCardBg,
                    border = BorderStroke(1.dp, FusionBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = FusionAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Workspace: ${File(activeWorkspacePath).name}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        if (changeItems.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = FusionCyan.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${changeItems.size} changed",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FusionCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = FusionBorder)
            }
        },
        containerColor = FusionDarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Deck Tabs: Agent | Diffs | Build
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FusionCardBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FusionConsoleTab.entries.forEach { tab ->
                    val isSel = consoleTab == tab
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) FusionAmber else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSel) FusionAmber else FusionBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { consoleTab = tab }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSel) Color.Black else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.Black else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Tab Content
            when (consoleTab) {
                FusionConsoleTab.AGENT -> {
                    FusionAgentTab(
                        prompt = promptInput,
                        onPromptChange = { promptInput = it },
                        selectedAgent = selectedAgent,
                        onSelectAgent = { selectedAgent = it },
                        selectedProvider = selectedProvider,
                        onSelectProvider = { selectedProvider = it },
                        isRunning = isRunning,
                        reasoningText = reasoningText,
                        isReasoningExpanded = isReasoningExpanded,
                        onToggleReasoning = { isReasoningExpanded = !isReasoningExpanded },
                        agentLogs = agentLogs,
                        finalResult = finalResult,
                        onRunTask = {
                            if (promptInput.isBlank()) {
                                AppToastHelper.showToast("Please enter a coding prompt", ChatToastType.INFO)
                                return@FusionAgentTab
                            }
                            isRunning = true
                            reasoningText = ""
                            agentLogs = listOf("🚀 Autonomous task started with ${selectedAgent.title}...")
                            finalResult = ""

                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val profile = ProviderProfile(kind = selectedProvider)
                                    engine.executeTask(
                                        projectId = activeProjectId,
                                        prompt = promptInput,
                                        agent = selectedAgent,
                                        provider = profile
                                    ) { event ->
                                        when (event) {
                                            is RuntimeEvent.ReasoningDelta -> {
                                                withContext(Dispatchers.Main) { reasoningText += event.text }
                                            }
                                            is RuntimeEvent.AssistantDelta -> {
                                                withContext(Dispatchers.Main) { finalResult += event.text }
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
                                                    agentLogs = agentLogs + "🏁 Session completed successfully."
                                                    refreshDiffs()
                                                    AppToastHelper.showToast("Task completed!", ChatToastType.SUCCESS)
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
                                        agentLogs = agentLogs + "❌ Exception: ${e.message}"
                                    }
                                }
                            }
                        },
                        onStopTask = {
                            coroutineScope.launch(Dispatchers.IO) {
                                JinAutonomousEngine.stopSession(activeProjectId)
                                withContext(Dispatchers.Main) {
                                    isRunning = false
                                    agentLogs = agentLogs + "🛑 Task stopped by user."
                                }
                            }
                        }
                    )
                }

                FusionConsoleTab.DIFFS -> {
                    FusionDiffsTab(
                        changeItems = changeItems,
                        isLoading = isDiffLoading,
                        onAcceptAll = {
                            coroutineScope.launch(Dispatchers.IO) {
                                checkpoints.checkpointDir(activeProjectId).deleteRecursively()
                                checkpoints.createCheckpoint(activeProjectId, File(activeWorkspacePath))
                                refreshDiffs()
                                withContext(Dispatchers.Main) {
                                    AppToastHelper.showToast("All changes accepted and saved to baseline", ChatToastType.SUCCESS)
                                }
                            }
                        },
                        onRevertAll = {
                            coroutineScope.launch(Dispatchers.IO) {
                                changeItems.forEach { item ->
                                    checkpoints.undoChange(activeProjectId, File(activeWorkspacePath), item.path)
                                }
                                refreshDiffs()
                                withContext(Dispatchers.Main) {
                                    AppToastHelper.showToast("All changes reverted to previous checkpoint", ChatToastType.SUCCESS)
                                }
                            }
                        },
                        onUndoItem = { item ->
                            coroutineScope.launch(Dispatchers.IO) {
                                checkpoints.undoChange(activeProjectId, File(activeWorkspacePath), item.path)
                                refreshDiffs()
                                withContext(Dispatchers.Main) {
                                    AppToastHelper.showToast("Reverted ${item.path}", ChatToastType.SUCCESS)
                                }
                            }
                        },
                        onKeepItem = { item ->
                            coroutineScope.launch(Dispatchers.IO) {
                                checkpoints.keepChange(activeProjectId, item.path)
                                refreshDiffs()
                                withContext(Dispatchers.Main) {
                                    AppToastHelper.showToast("Kept ${item.path}", ChatToastType.SUCCESS)
                                }
                            }
                        }
                    )
                }

                FusionConsoleTab.BUILD -> {
                    FusionBuildTab(
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
                                    withContext(Dispatchers.Main) { buildLogs += "$ $cmd\n" }

                                    val res = LocalEnvironmentManager.executeCommand(cmd, context, workspace.absolutePath)
                                    withContext(Dispatchers.Main) {
                                        buildLogs += res.output
                                    }

                                    val apk = AndroidBuildSupport.findDebugApk(workspace)
                                    withContext(Dispatchers.Main) {
                                        detectedApk = apk
                                        isBuilding = false
                                        if (apk != null) {
                                            buildLogs += "\n✅ Build succeeded! APK: ${apk.name} (${apk.length() / 1024 / 1024} MB)\n"
                                            AppToastHelper.showToast("Build Succeeded! APK Ready to install.", ChatToastType.SUCCESS)
                                        } else {
                                            buildLogs += "\n⚠️ Build finished with code ${res.exitCode}\n"
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isBuilding = false
                                        buildLogs += "\n❌ Build error: ${e.message}\n"
                                        AppToastHelper.showToast("Build error: ${e.message}", ChatToastType.ERROR)
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
                                AppToastHelper.showToast("Package name unresolvable", ChatToastType.INFO)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FusionAgentTab(
    prompt: String,
    onPromptChange: (String) -> Unit,
    selectedAgent: AgentKind,
    onSelectAgent: (AgentKind) -> Unit,
    selectedProvider: ProviderKind,
    onSelectProvider: (ProviderKind) -> Unit,
    isRunning: Boolean,
    reasoningText: String,
    isReasoningExpanded: Boolean,
    onToggleReasoning: () -> Unit,
    agentLogs: List<String>,
    finalResult: String,
    onRunTask: () -> Unit,
    onStopTask: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quick Presets Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val chips = listOf(
                "🚀 Scaffold Jetpack Compose App" to "Scaffold a modern Jetpack Compose Android application with Kotlin DSL Gradle build scripts and a counter screen.",
                "🔨 Build Debug APK" to "Run Gradle assembleDebug to compile the Android project into a debug APK.",
                "🔍 Inspect Codebase Diffs" to "Inspect all recent changes made in the workspace, verify syntax, and run git status.",
                "⚡ Fix Runtime Bugs" to "Audit the current workspace for compiler warnings, syntax errors, and missing dependencies."
            )
            chips.forEach { (label, task) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = FusionCardBg,
                    border = BorderStroke(1.dp, FusionBorder),
                    modifier = Modifier.clickable { onPromptChange(task) }
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = FusionCyan,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Engine & Provider Selector Row
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = FusionCardBg,
            border = BorderStroke(1.dp, FusionBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Agent Selector
                AgentKind.entries.forEach { ag ->
                    val isSel = selectedAgent == ag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) FusionAmber else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSel) FusionAmber else FusionBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectAgent(ag) }
                    ) {
                        Text(
                            text = ag.title,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color.Black else Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 5.dp),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Prompt Input Box
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            placeholder = { Text("Describe the task (e.g., 'Add a dark mode toggle button in MainActivity.kt')...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 160.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FusionAmber,
                unfocusedBorderColor = FusionBorder,
                focusedContainerColor = FusionCardBg,
                unfocusedContainerColor = FusionCardBg,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        )

        // Action Buttons: Run / Stop
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isRunning) {
                Button(
                    onClick = onStopTask,
                    colors = ButtonDefaults.buttonColors(containerColor = QuotaRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Agent", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = onRunTask,
                    colors = ButtonDefaults.buttonColors(containerColor = FusionAmber),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Autonomous Turn", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Streaming Reasoning Accordion
        if (reasoningText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = FusionCardBg,
                border = BorderStroke(1.dp, FusionPurple.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleReasoning() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧠 Model Reasoning & Thoughts",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FusionPurple,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isReasoningExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = FusionPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isReasoningExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = reasoningText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Live Activity Logs
        if (agentLogs.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = FusionCardBg,
                border = BorderStroke(1.dp, FusionBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "⚡ Live Activity Logs",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = FusionCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    agentLogs.takeLast(15).forEach { log ->
                        Text(
                            text = log,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }

        // Final Result Card
        if (finalResult.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = FusionCardBg,
                border = BorderStroke(1.dp, FusionBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📋 Result & Code Solution",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = FusionAmber
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownContent(
                        content = finalResult,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun FusionDiffsTab(
    changeItems: List<ChangeItem>,
    isLoading: Boolean,
    onAcceptAll: () -> Unit,
    onRevertAll: () -> Unit,
    onUndoItem: (ChangeItem) -> Unit,
    onKeepItem: (ChangeItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary & Batch Actions
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = FusionCardBg,
            border = BorderStroke(1.dp, FusionBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Workspace Checkpoint Diffs",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${changeItems.size} files modified in this session",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    if (changeItems.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onRevertAll,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = QuotaRed),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("Revert All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onAcceptAll,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = QuotaGreen),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("Accept All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FusionCyan, modifier = Modifier.size(28.dp))
            }
        } else if (changeItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No pending workspace changes. All files match clean checkpoint baseline.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            changeItems.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FusionCardBg,
                    border = BorderStroke(1.dp, FusionBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.path,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "+${item.additions} -${item.deletions}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.additions > 0) QuotaGreen else QuotaRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { onUndoItem(item) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Undo, contentDescription = "Revert", tint = QuotaRed, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onKeepItem(item) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Outlined.Check, contentDescription = "Accept", tint = QuotaGreen, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Diff Lines Preview
                        if (item.diffLines.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF030712))
                                    .padding(6.dp)
                            ) {
                                item.diffLines.take(15).forEach { dl ->
                                    val textColor = when (dl.type) {
                                        DiffLineType.ADDITION -> QuotaGreen
                                        DiffLineType.DELETION -> QuotaRed
                                        DiffLineType.INFO -> FusionCyan
                                        else -> Color.White.copy(alpha = 0.6f)
                                    }
                                    Text(
                                        text = dl.text,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = textColor,
                                        lineHeight = 14.sp
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

@Composable
private fun FusionBuildTab(
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
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = FusionCardBg,
            border = BorderStroke(1.dp, FusionBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🤖 Android Project Build Center",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Compiles on-device inside Termux/PRoot using AAPT2 and Gradle wrapper.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Button(
                    onClick = onStartBuild,
                    enabled = !isBuilding,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FusionCyan),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    if (isBuilding) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compiling Gradle Project...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Outlined.Build, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Assemble Debug APK", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Generated APK Card
        if (detectedApk != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = FusionCardBg,
                border = BorderStroke(1.dp, QuotaGreen.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = QuotaGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Debug APK Available", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = QuotaGreen)
                    }
                    Text(
                        text = "${detectedApk.name} · ${detectedApk.length() / 1024 / 1024} MB",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onInstallApk(detectedApk) },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = QuotaGreen),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Install APK", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onLaunchApp(detectedApk) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Launch App", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Build Terminal Output
        if (buildLogs.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF030712),
                border = BorderStroke(1.dp, FusionBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Build Output Terminal:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FusionCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildLogs,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
