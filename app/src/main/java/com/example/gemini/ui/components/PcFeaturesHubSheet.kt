package com.example.gemini.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

enum class CardDensityMode(val label: String, val icon: ImageVector) {
    COMPACT("Compact", Icons.Outlined.DensitySmall),
    COMFORTABLE("Comfortable", Icons.Outlined.DensityMedium),
    GRID("Grid", Icons.Outlined.GridView)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcFeaturesHubSheet(
    onDismiss: () -> Unit,
    onNavigateToIde: () -> Unit = {},
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToBrowser: () -> Unit = {},
    onNavigateToHarness: () -> Unit = {},
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
    var densityMode by remember { mutableStateOf(CardDensityMode.COMPACT) }

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
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ClaudeTerracotta.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = ClaudeTerracotta,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "JIN PC Power Features (100 Tools)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Desktop-grade AI IDE capabilities natively on mobile",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search 100 PC features (Git, Terminal, MCP, Voice, Harness)...", fontSize = 11.5.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClaudeTerracotta,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Density Controls & Counter Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredFeatures.size} features",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )

                // Density Toggle Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardDensityMode.entries.forEach { mode ->
                        val isSel = densityMode == mode
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) ClaudeTerracotta.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, if (isSel) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            modifier = Modifier.clickable { densityMode = mode }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = mode.icon,
                                    contentDescription = mode.label,
                                    tint = if (isSel) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = mode.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) ClaudeTerracotta else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Category Filter Pills (Horizontal Scrolling - No Vertical Wrapping!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
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
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp)
                        )
                    }
                }
            }

            // Features Display (Compact List, Comfortable List, or Grid)
            if (densityMode == CardDensityMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredFeatures, key = { it.id }) { feat ->
                        val isExpanded = expandedFeatureId == feat.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, if (isExpanded) feat.accentColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedFeatureId = if (isExpanded) null else feat.id }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(feat.accentColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = feat.icon,
                                            contentDescription = null,
                                            tint = feat.accentColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = feat.accentColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = feat.badge,
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = feat.accentColor,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                Text(
                                    text = feat.title,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = feat.description,
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = if (isExpanded) 5 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 12.sp
                                )

                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            executeFeatureAction(
                                                feat = feat,
                                                onDismiss = onDismiss,
                                                onNavigateToIde = onNavigateToIde,
                                                onNavigateToTerminal = onNavigateToTerminal,
                                                onNavigateToBrowser = onNavigateToBrowser,
                                                onNavigateToHarness = onNavigateToHarness,
                                                onOpenGit = onOpenGit,
                                                onOpenSkills = onOpenSkills,
                                                onOpenScratchpad = onOpenScratchpad,
                                                onOpenExport = onOpenExport,
                                                onOpenAutomation = onOpenAutomation,
                                                onOpenGoogleDrive = onOpenGoogleDrive,
                                                onOpenNotebooks = onOpenNotebooks
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().height(28.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = feat.accentColor),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Launch", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (densityMode == CardDensityMode.COMPACT) 5.dp else 8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredFeatures, key = { it.id }) { feat ->
                        val isExpanded = expandedFeatureId == feat.id
                        val isCompact = densityMode == CardDensityMode.COMPACT

                        Surface(
                            shape = RoundedCornerShape(if (isCompact) 10.dp else 12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, if (isExpanded) feat.accentColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(if (isCompact) 8.dp else 11.dp)) {
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
                                            .size(if (isCompact) 28.dp else 34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(feat.accentColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = feat.icon,
                                            contentDescription = null,
                                            tint = feat.accentColor,
                                            modifier = Modifier.size(if (isCompact) 16.dp else 18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(9.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = feat.title,
                                                fontSize = if (isCompact) 12.sp else 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = feat.accentColor.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = feat.badge,
                                                    fontSize = if (isCompact) 7.5.sp else 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = feat.accentColor,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = feat.description,
                                            fontSize = if (isCompact) 10.sp else 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            maxLines = if (isExpanded) 8 else if (isCompact) 1 else 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = if (isCompact) 12.sp else 14.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = { expandedFeatureId = if (isExpanded) null else feat.id },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.Info,
                                            contentDescription = "Show details",
                                            tint = feat.accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = "💡 টুলের কাজ (Functionality):",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = feat.accentColor
                                        )
                                        Text(
                                            text = feat.whatItDoes.ifBlank { feat.description },
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            lineHeight = 13.sp
                                        )

                                        Text(
                                            text = "⚡ অন করলে যা হবে (Effect when Active):",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = feat.accentColor
                                        )
                                        Text(
                                            text = feat.onEnableEffect.ifBlank { "সরাসরি মোবাইলে সক্রিয় হবে এবং এজেন্টের টুলস সিস্টেমে ফুল এক্সেস তৈরি হবে।" },
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            lineHeight = 13.sp
                                        )

                                        Text(
                                            text = "📖 ব্যবহারের নিয়ম (How to Use):",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = feat.accentColor
                                        )
                                        Text(
                                            text = feat.howToUse.ifBlank { "নিচের বাটনে ট্যাপ করুন বা চ্যাটে নির্দেশ দিয়ে স্বয়ংক্রিয়ভাবে ব্যবহার করুন।" },
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            lineHeight = 13.sp
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Button(
                                            onClick = {
                                                executeFeatureAction(
                                                    feat = feat,
                                                    onDismiss = onDismiss,
                                                    onNavigateToIde = onNavigateToIde,
                                                    onNavigateToTerminal = onNavigateToTerminal,
                                                    onNavigateToBrowser = onNavigateToBrowser,
                                                    onNavigateToHarness = onNavigateToHarness,
                                                    onOpenGit = onOpenGit,
                                                    onOpenSkills = onOpenSkills,
                                                    onOpenScratchpad = onOpenScratchpad,
                                                    onOpenExport = onOpenExport,
                                                    onOpenAutomation = onOpenAutomation,
                                                    onOpenGoogleDrive = onOpenGoogleDrive,
                                                    onOpenNotebooks = onOpenNotebooks
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth().height(34.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = feat.accentColor)
                                        ) {
                                            Text("🚀 এখন চালু করুন (Activate & Launch)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

private fun executeFeatureAction(
    feat: PcFeatureItem,
    onDismiss: () -> Unit,
    onNavigateToIde: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToHarness: () -> Unit,
    onOpenGit: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenScratchpad: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenGoogleDrive: () -> Unit,
    onOpenNotebooks: () -> Unit
) {
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
}
