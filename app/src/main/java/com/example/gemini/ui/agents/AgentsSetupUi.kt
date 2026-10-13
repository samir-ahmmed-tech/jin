package com.example.gemini.ui.agents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.domain.model.AgentKind
import com.example.gemini.ui.components.accent
import kotlinx.coroutines.launch

data class AgentItemInfo(
    val kind: AgentKind,
    val title: String,
    val description: String,
    val badge: String? = null
)

val AGENT_ITEMS: List<AgentItemInfo>
    get() = if (com.example.gemini.BuildConfig.IS_ANTIGRAVITY_ONLY) {
        listOf(
            AgentItemInfo(
                AgentKind.AGY,
                "Antigravity 2.0 (Google)",
                "Google's flagship agent through agy hub. Runs Gemini 2.5 and Claude with full workspace execution.",
                "Dedicated"
            )
        )
    } else {
        listOf(
            AgentItemInfo(
                AgentKind.AGY,
                "Antigravity 2.0 (Google)",
                "Google's flagship agent through agy hub. Runs Gemini 2.5 and Claude 3.7 with code execution.",
                "Core"
            ),
            AgentItemInfo(
                AgentKind.DEEPSEEK_LOCAL,
                "DeepSeek Reasoning Harness",
                "High-performance DeepSeek V3 / Coder reasoning agent. Supports direct API and local execution.",
                "DeepSeek"
            ),
            AgentItemInfo(
                AgentKind.CLAUDE,
                "Claude Code CLI",
                "Anthropic's terminal agent on device: console account or Claude subscription.",
                "Anthropic"
            ),
            AgentItemInfo(
                AgentKind.CODEX,
                "OpenAI Codex CLI",
                "OpenAI coding engine with GPT-4o, Codex code synthesis and deep debugging.",
                "OpenAI"
            ),
            AgentItemInfo(
                AgentKind.OPENROUTER,
                "OpenRouter Universal Swarm",
                "Connects to 100+ open-source models (Llama 3.3, Mistral, Qwen) via OpenRouter API.",
                "Swarm"
            ),
            AgentItemInfo(
                AgentKind.OPENCODE,
                "OpenCode AI Terminal CLI",
                "Open-source terminal AI coding assistant powered by OpenCode Zen & polyglot models.",
                "OpenCode"
            )
        )
    }

/**
 * Flexible Multi-Agent Selector with Concurrency and Presets.
 * Allows running any combination of agents concurrently without turning off Antigravity.
 */
@Composable
fun AgentSelector(
    selected: Set<AgentKind>,
    onSelect: (Set<AgentKind>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authPrefs = remember { AuthPreferences(context) }
    val scope = rememberCoroutineScope()
    var concurrency by remember { mutableIntStateOf(authPrefs.getMultiAgentConcurrencySync()) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!com.example.gemini.BuildConfig.IS_ANTIGRAVITY_ONLY) {
            // Quick Presets Row
            Text(
                "Quick Presets",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(
                    "Solo (AGY)" to setOf(AgentKind.AGY),
                    "Dual (AGY+DeepSeek)" to setOf(AgentKind.AGY, AgentKind.DEEPSEEK_LOCAL),
                    "Trio (+Claude)" to setOf(AgentKind.AGY, AgentKind.DEEPSEEK_LOCAL, AgentKind.CLAUDE),
                    "Swarm (All)" to AgentKind.entries.toSet()
                )

                presets.forEach { (name, set) ->
                    val isCurrent = selected == set
                    Surface(
                        onClick = { onSelect(set) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 10.5.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Active Agents (Multi-Select Enabled)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // Individual Agent Cards
        AGENT_ITEMS.forEach { item ->
            val isOn = item.kind in selected
            val accent = item.kind.accent()

            Surface(
                onClick = {
                    val updated = if (isOn) {
                        // At least one agent must remain enabled
                        if (selected.size > 1) selected - item.kind else selected
                    } else {
                        selected + item.kind
                    }
                    onSelect(updated)
                },
                shape = RoundedCornerShape(16.dp),
                color = if (isOn) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (isOn) 1.5.dp else 1.dp,
                    if (isOn) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AgentMark(item.kind, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.title,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            item.badge?.let { b ->
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accent.copy(alpha = 0.15f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = b,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.description,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // Checkbox indicator
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isOn) accent else Color.Transparent)
                            .border(
                                1.5.dp,
                                if (isOn) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                RoundedCornerShape(7.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isOn) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Enabled",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!com.example.gemini.BuildConfig.IS_ANTIGRAVITY_ONLY) {
            Spacer(Modifier.height(6.dp))

            // Concurrency Controller (1 to 6 Agents)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Concurrent Agent Execution", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Maximum simultaneous running agents (1 to 6)",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "$concurrency Active",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (1..6).forEach { num ->
                            val isSel = concurrency == num
                            Surface(
                                onClick = {
                                    concurrency = num
                                    scope.launch { authPrefs.saveMultiAgentConcurrency(num) }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "$num",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
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

/** First launch screen: which agents the app should run (Full page recreate matching app style). */
@Composable
fun AgentChoiceDialog(onConfirm: (Set<AgentKind>) -> Unit) {
    var selected by remember { mutableStateOf(setOf(AgentKind.AGY)) }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Scrollable list
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(width = 68.dp, height = 48.dp)) {
                            AgentMark(AgentKind.AGY, size = 44.dp)
                            AgentMark(AgentKind.DEEPSEEK_LOCAL, size = 38.dp, modifier = Modifier.offset(x = 24.dp, y = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Select Active Agents",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pick any combination of agents to run simultaneously. You can change this anytime in Settings.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    Spacer(Modifier.height(20.dp))
                    AgentSelector(selected, { selected = it })
                    Spacer(Modifier.height(20.dp))
                }

                // Sticky Bottom Continue Action Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = { onConfirm(selected) },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.gemini.theme.QuotaGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/** Settings → Agents: change selection and concurrency; applying applies the new swarm setup. */
@Composable
fun AgentsSettingsPage(
    enabled: Set<AgentKind>,
    isApplying: Boolean,
    onApply: (Set<AgentKind>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember(enabled) { mutableStateOf(enabled) }
    var confirm by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            "Select which AI agents run concurrently on this device. Running multiple agents allows instantaneous switching and swarm delegation.",
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(14.dp))
        AgentSelector(selected, { selected = it })
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { confirm = true },
            enabled = selected != enabled && !isApplying,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isApplying) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Applying Swarm…")
            } else {
                Text(if (selected == enabled) "Swarm Active" else "Apply Changes", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Apply Agent Configuration?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The local runtime will reconfigure with your selected agents (${selected.size} active). " +
                        "Active sessions will be updated.",
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    onApply(selected)
                }) { Text("Confirm & Apply") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

/** Shown while applying agent updates. */
@Composable
fun AgentsApplyingDialog() {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Configuring Agent Swarm", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Updating local agent execution environments…", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}
