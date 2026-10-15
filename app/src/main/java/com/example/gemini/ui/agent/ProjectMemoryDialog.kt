package com.example.gemini.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemini.data.agent.memory.ProjectMemory
import com.example.gemini.data.agent.memory.ProjectMemoryManager
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.GeminiBlue
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProjectMemoryDialog(
    workspaceDir: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memoryManager = remember { ProjectMemoryManager(context) }
    var memory by remember { mutableStateOf(ProjectMemory(workspaceDir.name)) }
    var newRuleInput by remember { mutableStateOf("") }
    var newDecisionInput by remember { mutableStateOf("") }

    LaunchedEffect(workspaceDir) {
        memory = memoryManager.loadMemory(workspaceDir)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = GeminiBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Project Memory & Rules",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${workspaceDir.name} · .jin/memory.json",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // Section: Architectural Decisions
                        Text(
                            text = "Architectural Decisions (${memory.architecturalDecisions.size})",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClaudeTerracotta
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (memory.architecturalDecisions.isEmpty()) {
                            Text("No architectural decisions recorded.", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            memory.architecturalDecisions.forEach { dec ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "• $dec",
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newDecisionInput,
                                onValueChange = { newDecisionInput = it },
                                placeholder = { Text("e.g. Use Room database for local caching", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = {
                                if (newDecisionInput.isNotBlank()) {
                                    scope.launch {
                                        memoryManager.addDecision(workspaceDir, newDecisionInput)
                                        memory = memoryManager.loadMemory(workspaceDir)
                                        newDecisionInput = ""
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Decision", tint = ClaudeTerracotta)
                            }
                        }
                    }

                    item {
                        // Section: Coding Rules & Constraints
                        Text(
                            text = "Coding Rules & Guidelines (${memory.codingRules.size})",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeminiBlue
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (memory.codingRules.isEmpty()) {
                            Text("No specific coding rules recorded.", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            memory.codingRules.forEach { rule ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "• $rule",
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newRuleInput,
                                onValueChange = { newRuleInput = it },
                                placeholder = { Text("e.g. Always use pnpm for web projects", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = {
                                if (newRuleInput.isNotBlank()) {
                                    scope.launch {
                                        memoryManager.addRule(workspaceDir, newRuleInput)
                                        memory = memoryManager.loadMemory(workspaceDir)
                                        newRuleInput = ""
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Rule", tint = GeminiBlue)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        scope.launch {
                            memoryManager.clearMemory(workspaceDir)
                            memory = memoryManager.loadMemory(workspaceDir)
                        }
                    }) {
                        Text("Reset Memory", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
