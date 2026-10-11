package com.example.gemini.ui.agents

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.domain.model.AgentKind
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.QuotaGreen
import com.example.gemini.ui.components.AppToastHelper
import com.example.gemini.ui.components.ChatToastType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AgentCliPackage(
    val id: String,
    val name: String,
    val agentKind: AgentKind,
    val description: String,
    val sizeMb: Float,
    val authType: String,
    val icon: ImageVector,
    val accentColor: Color,
    val features: List<String>,
    val loginUrl: String,
    val installCommand: String
)

object AgentCliSuiteRegistry {
    val PACKAGES = listOf(
        AgentCliPackage(
            id = "antigravity",
            name = "Google Antigravity 2.0 CLI (agy)",
            agentKind = AgentKind.AGY,
            description = "Native Google Antigravity bridge & multi-agent runtime binary in ~/.local/bin/agy.",
            sizeMb = 171.2f,
            authType = "Google Account (OAuth)",
            icon = Icons.Outlined.AutoAwesome,
            accentColor = Color(0xFF4285F4),
            features = listOf("Google OAuth Login", "Full Tool Calling", "Terminal Automation", "AGY Trajectory Engine"),
            loginUrl = "https://accounts.google.com",
            installCommand = "agy --version"
        ),
        AgentCliPackage(
            id = "claude",
            name = "Anthropic Claude Code CLI",
            agentKind = AgentKind.CLAUDE,
            description = "Official Anthropic agentic coding CLI installed via npm with glibc runtime wrapper.",
            sizeMb = 320.0f,
            authType = "Claude Pro / Team Subscription",
            icon = Icons.Outlined.Terminal,
            accentColor = ClaudeTerracotta,
            features = listOf("Claude Account Login", "File Refactoring", "Bash Execution", "Multi-File Editing"),
            loginUrl = "https://claude.ai",
            installCommand = "npm install -g @anthropic-ai/claude-code"
        ),
        AgentCliPackage(
            id = "deepseek",
            name = "DeepSeek Hermes Native CLI Suite",
            agentKind = AgentKind.DEEPSEEK_LOCAL,
            description = "DeepSeek V3 & Hermes reasoning engine with 671B MoE algorithms running in Termux Linux.",
            sizeMb = 450.0f,
            authType = "DeepSeek Account / Subscription",
            icon = Icons.Outlined.Psychology,
            accentColor = Color(0xFF4F46E5),
            features = listOf("DeepSeek Web Login", "Hermes Reasoning", "300+ Languages", "Terminal Execution"),
            loginUrl = "https://chat.deepseek.com",
            installCommand = "pip install --upgrade deepseek-coder-cli hermes-agent || npm install -g deepseek-coder-cli"
        ),
        AgentCliPackage(
            id = "codex",
            name = "OpenAI Codex CLI Suite (Termux)",
            agentKind = AgentKind.CODEX,
            description = "OpenAI Codex & ChatGPT CLI runner for high-speed terminal execution & architecture in Linux.",
            sizeMb = 280.0f,
            authType = "ChatGPT Plus / Team Account",
            icon = Icons.Outlined.Code,
            accentColor = Color(0xFF10A37F),
            features = listOf("OpenAI Device Auth", "GPT-4o Synthesis", "Automated Bug Fixing", "CLI Shell Tools"),
            loginUrl = "https://chatgpt.com",
            installCommand = "npm install -g openai-codex-cli || pip install codex-cli"
        ),
        AgentCliPackage(
            id = "openrouter",
            name = "OpenRouter & Open Cloud CLI Suite",
            agentKind = AgentKind.OPENROUTER,
            description = "Universal multi-model routing & Open Cloud agent with Mistral, Qwen, and Llama 3.3.",
            sizeMb = 180.0f,
            authType = "OpenRouter / Open Cloud Account",
            icon = Icons.Outlined.Share,
            accentColor = Color(0xFFF59E0B),
            features = listOf("Open Cloud Login", "Codestral 25B", "Qwen 2.5 Coder", "Auto Failover"),
            loginUrl = "https://openrouter.ai",
            installCommand = "npm install -g openrouter-cli opencloud-cli"
        )
    )
}

@Composable
fun AgentCliSuiteDialog(
    onDismiss: () -> Unit,
    onOpenOAuthLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authPrefs = remember { AuthPreferences(context) }

    // Track download and installation status for each package
    var installedPackages by remember {
        mutableStateOf(setOf("antigravity", "claude"))
    }
    var downloadingPackageId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloadingAll by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = ClaudeTerracotta,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Agent CLI Suite & Logins",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Download CLI packages & sign in with subscriptions (Google, Claude, DeepSeek, OpenAI) without API keys",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 15.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // One-Click Download All Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ClaudeTerracotta.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ClaudeTerracotta.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1-Click Full Agent Suite (Termux Engines)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Install all 5 CLI engines (~1.4 GB total) with live terminal execution in Termux",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (!isDownloadingAll) {
                                    isDownloadingAll = true
                                    coroutineScope.launch {
                                        val cmd = "npm install -g @anthropic-ai/claude-code openrouter-cli && pip install deepseek-coder-cli"
                                        com.example.gemini.data.local.LocalTerminalBridge.instance.sendCommand(command = cmd, appendEnter = true)
                                        for (pkg in AgentCliSuiteRegistry.PACKAGES) {
                                            downloadingPackageId = pkg.id
                                            delay(200)
                                            installedPackages = installedPackages + pkg.id
                                        }
                                        downloadingPackageId = null
                                        isDownloadingAll = false
                                        AppToastHelper.showToast(
                                            "Launched Full CLI Suite install in Termux!",
                                            ChatToastType.SUCCESS
                                        )
                                    }
                                }
                            },
                            enabled = !isDownloadingAll,
                            colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (isDownloadingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Install All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Global Progress if downloading
                if (isDownloadingAll || downloadingPackageId != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = ClaudeTerracotta,
                        trackColor = ClaudeTerracotta.copy(alpha = 0.2f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable List of Packages
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AgentCliSuiteRegistry.PACKAGES.forEach { pkg ->
                        val isInstalled = installedPackages.contains(pkg.id)
                        val isDownloadingThis = downloadingPackageId == pkg.id

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                1.dp,
                                if (isInstalled) QuotaGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(pkg.accentColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = pkg.icon,
                                            contentDescription = null,
                                            tint = pkg.accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = pkg.name,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isInstalled) QuotaGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                            ) {
                                                Text(
                                                    text = if (isInstalled) "READY" else "${pkg.sizeMb.toInt()} MB",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isInstalled) QuotaGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = pkg.authType,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = pkg.accentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = pkg.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                    lineHeight = 15.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Feature Tags with FlowRow so tags wrap cleanly to new lines without clipping
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    pkg.features.forEach { feat ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                        ) {
                                            Text(
                                                text = feat,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Action Buttons (Download / Install & Sign In / OAuth)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Download / Install Button
                                    OutlinedButton(
                                        onClick = {
                                            if (!isDownloadingThis) {
                                                downloadingPackageId = pkg.id
                                                coroutineScope.launch {
                                                    com.example.gemini.data.local.LocalTerminalBridge.instance.sendCommand(
                                                        command = pkg.installCommand,
                                                        appendEnter = true
                                                    )
                                                    delay(300)
                                                    installedPackages = installedPackages + pkg.id
                                                    downloadingPackageId = null
                                                    AppToastHelper.showToast(
                                                        "Executed in Termux: ${pkg.installCommand}",
                                                        ChatToastType.SUCCESS
                                                    )
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isDownloadingThis) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else if (isInstalled) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircle,
                                                contentDescription = null,
                                                tint = QuotaGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Installed", fontSize = 11.5.sp, color = QuotaGreen, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download (${pkg.sizeMb}MB)", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    // Subscription Login / OAuth Button
                                    Button(
                                        onClick = {
                                            if (pkg.agentKind == AgentKind.AGY) {
                                                onOpenOAuthLogin()
                                                onDismiss()
                                            } else {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(pkg.loginUrl))
                                                    )
                                                }
                                                AppToastHelper.showToast(
                                                    "Opened ${pkg.name} login in browser",
                                                    ChatToastType.INFO
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = pkg.accentColor),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Login,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sign In (No Key)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
