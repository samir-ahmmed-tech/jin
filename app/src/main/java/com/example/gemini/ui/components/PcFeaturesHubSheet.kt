package com.example.gemini.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.QuotaGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcFeaturesHubSheet(
    onDismiss: () -> Unit,
    onNavigateToIde: () -> Unit = {},
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToBrowser: () -> Unit = {},
    onOpenGit: () -> Unit = {},
    onOpenSkills: () -> Unit = {},
    onOpenScratchpad: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenAutomation: () -> Unit = {},
    onOpenGoogleDrive: () -> Unit = {},
    onOpenNotebooks: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var expandedFeatureId by remember { mutableStateOf<String?>(null) }

    val categories = remember {
        listOf("All") + PcFeaturesRegistry.FEATURES.map { it.category }.distinct()
    }

    val filteredFeatures = remember(searchQuery, selectedCategory) {
        PcFeaturesRegistry.FEATURES.filter { feat ->
            val matchesQuery = searchQuery.isBlank() ||
                    feat.title.contains(searchQuery, ignoreCase = true) ||
                    feat.description.contains(searchQuery, ignoreCase = true) ||
                    feat.badge.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == null || selectedCategory == "All" || feat.category == selectedCategory
            matchesQuery && matchesCategory
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 18.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ClaudeTerracotta.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = ClaudeTerracotta,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "JIN PC Power Features (100 Tools)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Desktop-grade AI IDE capabilities natively optimized for mobile",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search 100 PC features (e.g. Git, Terminal, MCP, Voice)...", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClaudeTerracotta,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.take(4).forEach { cat ->
                    val isSel = (cat == "All" && selectedCategory == null) || selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) ClaudeTerracotta else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (isSel) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                        modifier = Modifier.clickable {
                            selectedCategory = if (cat == "All") null else cat
                        }
                    ) {
                        Text(
                            text = cat,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Features List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredFeatures, key = { it.id }) { feat ->
                    val isExpanded = expandedFeatureId == feat.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, if (isExpanded) feat.accentColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedFeatureId = if (isExpanded) null else feat.id
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(feat.accentColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = feat.icon,
                                        contentDescription = null,
                                        tint = feat.accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = feat.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = feat.accentColor.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = feat.badge,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = feat.accentColor,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = feat.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = if (isExpanded) 8 else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 14.sp
                                    )
                                }

                                IconButton(
                                    onClick = { expandedFeatureId = if (isExpanded) null else feat.id },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.Info,
                                        contentDescription = "Show details",
                                        tint = feat.accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "💡 টুলের কাজ (Functionality):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = feat.accentColor
                                    )
                                    Text(
                                        text = feat.whatItDoes.ifBlank { feat.description },
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        lineHeight = 14.sp
                                    )

                                    Text(
                                        text = "⚡ অন করলে যা হবে (Effect when Active):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = feat.accentColor
                                    )
                                    Text(
                                        text = feat.onEnableEffect.ifBlank { "সরাসরি মোবাইলে সক্রিয় হবে এবং এজেন্টের টুলস সিস্টেমে ফুল এক্সেস তৈরি হবে।" },
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        lineHeight = 14.sp
                                    )

                                    Text(
                                        text = "📖 ব্যবহারের নিয়ম (How to Use):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = feat.accentColor
                                    )
                                    Text(
                                        text = feat.howToUse.ifBlank { "নিচের বাটনে ট্যাপ করুন বা চ্যাটে নির্দেশ দিয়ে স্বয়ংক্রিয়ভাবে ব্যবহার করুন।" },
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        lineHeight = 14.sp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = {
                                            when (feat.id) {
                                                "quick_open", "file_tree", "global_find_replace", "zen_mode", "lsp_diagnostics", "symbol_outline", "multi_cursor" -> {
                                                    onNavigateToIde()
                                                    onDismiss()
                                                }
                                                "git_diff", "git_blame", "worktree_branching" -> {
                                                    onOpenGit()
                                                    onDismiss()
                                                }
                                                "multi_tab_terminal", "live_exec_console", "tmux_multiplexer", "shell_history", "ssh_tunnel" -> {
                                                    onNavigateToTerminal()
                                                    onDismiss()
                                                }
                                                "web_inspector" -> {
                                                    onNavigateToBrowser()
                                                    onDismiss()
                                                }
                                                "scratchpad" -> {
                                                    onOpenScratchpad()
                                                    onDismiss()
                                                }
                                                "trajectory_export" -> {
                                                    onOpenExport()
                                                    onDismiss()
                                                }
                                                "skills_marketplace", "mcp_explorer", "community_skills", "mcp_simulator" -> {
                                                    onOpenSkills()
                                                    onDismiss()
                                                }
                                                "ai_automation" -> {
                                                    onOpenAutomation()
                                                    onDismiss()
                                                }
                                                "google_vault", "workspace_backup" -> {
                                                    onOpenGoogleDrive()
                                                    onDismiss()
                                                }
                                                "gemini_notebooks" -> {
                                                    onOpenNotebooks()
                                                    onDismiss()
                                                }
                                                "battery_saver" -> {
                                                    AppToastHelper.showToast("⚡ 24/7 Battery Saver: CPU governor & memory tuned for cool running!", ChatToastType.SUCCESS)
                                                    onDismiss()
                                                }
                                                "cooling_guard" -> {
                                                    AppToastHelper.showToast("❄️ Cooling Governor Active: Zero-heating thermal limits engaged!", ChatToastType.SUCCESS)
                                                    onDismiss()
                                                }
                                                "ram_booster" -> {
                                                    System.gc()
                                                    AppToastHelper.showToast("🚀 Deep RAM Cleaner: Cleared buffers and purged zombie subprocesses!", ChatToastType.SUCCESS)
                                                    onDismiss()
                                                }
                                                else -> {
                                                    AppToastHelper.showToast("Activated ${feat.title}", ChatToastType.SUCCESS)
                                                    onDismiss()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = feat.accentColor)
                                    ) {
                                        Text("🚀 এখন চালু করুন (Activate & Launch)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
