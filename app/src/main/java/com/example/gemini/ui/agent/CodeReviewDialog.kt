package com.example.gemini.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemini.data.agent.review.CodeReviewEngine
import com.example.gemini.data.agent.review.CodeReviewReport
import com.example.gemini.data.agent.review.ReviewFinding
import com.example.gemini.data.agent.review.ReviewSeverity
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.GeminiBlue
import com.example.gemini.theme.QuotaGreen
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CodeReviewDialog(
    workspaceDir: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reviewEngine = remember { CodeReviewEngine(context) }
    var report by remember { mutableStateOf<CodeReviewReport?>(null) }
    var isScanning by remember { mutableStateOf(true) }

    LaunchedEffect(workspaceDir) {
        isScanning = true
        report = reviewEngine.reviewWorkspace(workspaceDir)
        isScanning = false
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
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
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
                            imageVector = Icons.Outlined.SecurityUpdateGood,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Autonomous Code Review",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${workspaceDir.name} · Security & Performance Audit",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isScanning) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = GeminiBlue)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Analyzing workspace files for security & performance...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    val rep = report
                    if (rep != null) {
                        // Summary Stats Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatCard(title = "Security", count = rep.securityCount, color = Color(0xFFEF4444), modifier = Modifier.weight(1f))
                            StatCard(title = "Bug Risks", count = rep.bugRiskCount, color = Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                            StatCard(title = "Performance", count = rep.performanceCount, color = GeminiBlue, modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (rep.findings.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = QuotaGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, QuotaGreen.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = QuotaGreen, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Zero Issues Detected", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = QuotaGreen)
                                    Text("Scanned ${rep.totalFilesScanned} files. No security leaks, unbounded scopes, or cleartext hazards found.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(rep.findings) { finding ->
                                    ReviewFindingCard(finding = finding)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$count", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = title, fontSize = 10.sp, color = color)
        }
    }
}

@Composable
private fun ReviewFindingCard(finding: ReviewFinding) {
    val color = when (finding.severity) {
        ReviewSeverity.SECURITY_CRITICAL -> Color(0xFFEF4444)
        ReviewSeverity.BUG_RISK -> Color(0xFFF59E0B)
        ReviewSeverity.PERFORMANCE_ISSUE -> GeminiBlue
        ReviewSeverity.STYLE_WARNING -> QuotaGreen
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = finding.severity.label,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "${finding.filePath}${if (finding.lineNumber != null) ":${finding.lineNumber}" else ""}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = finding.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = finding.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            if (finding.suggestedFixSnippet != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E1E1E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = finding.suggestedFixSnippet,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF34D399),
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}
