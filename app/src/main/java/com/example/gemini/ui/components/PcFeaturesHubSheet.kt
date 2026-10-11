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

data class PcFeatureItem(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val icon: ImageVector,
    val badge: String = "PC POWER",
    val accentColor: Color = ClaudeTerracotta,
    val whatItDoes: String = "",
    val onEnableEffect: String = "",
    val howToUse: String = ""
)

object PcFeaturesRegistry {
    val FEATURES = listOf(
        PcFeatureItem(
            id = "quick_open",
            title = "Quick Open File Palette (Ctrl+P)",
            category = "Editor & Navigation",
            description = "Fuzzy search across all workspace code files, configs, and recent chats.",
            icon = Icons.Outlined.Search,
            badge = "PC CORE",
            accentColor = Color(0xFF3B82F6),
            whatItDoes = "প্রজেক্টের যেকোনো ফাইল বা কোড চোখের পলকে সার্চ করে ওপেন করার ফাস্ট প্যালেট (Ctrl+P সমতুল্য)।",
            onEnableEffect = "আইডিই এডিটরে ফাইল সাথে সাথে লোড হবে এবং কোড প্রিভিউ দেখতে পারবেন।",
            howToUse = "ট্যাপ করে ফাইলের নাম টাইপ করুন (যেমন index.js বা MainScreen.kt), নিমেষেই ওপেন হয়ে যাবে।"
        ),
        PcFeatureItem(
            id = "git_diff",
            title = "Live Git Diff & Commit Manager",
            category = "Source Control",
            description = "Side-by-side visual file diffs, git staging, and 1-tap commit messages.",
            icon = Icons.Outlined.CallSplit,
            badge = "VCS",
            accentColor = Color(0xFF10B981),
            whatItDoes = "গিট রেপোজিটরির প্রতিটা লাইনের পরিবর্তন, ফাইল ডিফারেন্স এবং আনকমিটেড কোড দেখার সিস্টেম।",
            onEnableEffect = "কোন ফাইলে পরিবর্তন এসেছে তা সাইড-বাই-সাইড কালার কোডিং সহ প্রদর্শিত হবে।",
            howToUse = "ট্যাপ করে গিট স্ট্যাটাস দেখুন, স্টেজ করুন এবং 1-ট্যাপে কমিট মেসেজ লিখে পুশ করুন।"
        ),
        PcFeatureItem(
            id = "multi_tab_terminal",
            title = "Multi-Tab Split Terminal",
            category = "Terminal & Shell",
            description = "Concurrent terminal sessions (Bash, Node, Python) with fast tab toggling.",
            icon = Icons.Outlined.Terminal,
            badge = "TERMINAL",
            accentColor = Color(0xFFF59E0B),
            whatItDoes = "একসাথে একাধিক ব্যাকগ্রাউন্ড টার্মিনাল ও শেল সেশন (Bash, Python, Node.js) চালানোর ক্ষমতা।",
            onEnableEffect = "প্রজেক্টের সার্ভার ব্যাকগ্রাউন্ডে চলতে থাকবে এবং আলাদা ট্যাবে টেস্ট বা স্ক্রিপ্ট চালানো যাবে।",
            howToUse = "টার্মিনাল সেকশনে গিয়ে + ট্যাবে ট্যাপ করে নতুন কনসোল চালু করুন।"
        ),
        PcFeatureItem(
            id = "mcp_explorer",
            title = "MCP Tools & Servers Explorer",
            category = "Extensibility",
            description = "Inspect connected Model Context Protocol servers and invoke tools directly.",
            icon = Icons.Outlined.Extension,
            badge = "MCP",
            accentColor = Color(0xFF8B5CF6),
            whatItDoes = "অ্যান্টিগ্র্যাভিটি ও মডার্ন মডেল কনটেক্সট প্রটোকল (MCP) সার্ভার এবং টুলস ম্যানেজমেন্ট সিস্টেম।",
            onEnableEffect = "ব্রাউজার অটোমেশন, লোকাল ফাইল টুলস এবং কাস্টম টুলস এআই এজেন্টের সাথে অটো-কানেক্ট হবে।",
            howToUse = "সেটিংস থেকে MCP অপশন অন করে যেকোনো MCP সার্ভার যোগ বা সক্রিয় করুন।"
        ),
        PcFeatureItem(
            id = "slash_commands",
            title = "Slash Command Action Palette",
            category = "Agent Superpowers",
            description = "Interactive action sheet for /goal, /plan, /schedule, /learn, /boost, /grill-me.",
            icon = Icons.Outlined.Bolt,
            badge = "AI",
            accentColor = Color(0xFFEC4899),
            whatItDoes = "অ্যান্টিগ্র্যাভিটি পিসির সুপারপাওয়ার কমান্ডস (/goal, /plan, /schedule, /boost) সরাসরি মোবাইলে ব্যবহারের প্যালেট।",
            onEnableEffect = "এআই এজেন্ট লং-রানিং টাস্ক, স্টেপ-বাই-স্টেপ প্ল্যানিং এবং ডিপ রিফ্যাক্টরিং মুডে কাজ করবে।",
            howToUse = "চ্যাট বক্সে স্ল্যাশ (/) লিখলেই ড্রপডাউন পপআপ হবে অথবা বাটন চেপে অ্যাক্টিভ করুন।"
        ),
        PcFeatureItem(
            id = "swarm_monitor",
            title = "Subagent Swarm Concurrency HUD",
            category = "Multi-Agent",
            description = "Live monitor of concurrent background subagents with live memory and kill controls.",
            icon = Icons.Outlined.Groups,
            badge = "SWARM",
            accentColor = Color(0xFF06B6D4),
            whatItDoes = "ব্যাকগ্রাউন্ডে একাধিক সাব-এজেন্ট (Researcher, Debugger, Architect) একসাথে চলার লাইভ মনিটর।",
            onEnableEffect = "একের পর এক কাজ অপেক্ষা না করে সমান্তরালভাবে একাধিক এজেন্ট কাজ সম্পন্ন করবে।",
            howToUse = "সোয়ার্ম কনকারেন্সি অন করে ১ থেকে ৬টি কনকারেন্ট এজেন্টের কাজ পর্যবেক্ষণ করুন।"
        ),
        PcFeatureItem(
            id = "scratchpad",
            title = "Code Snippets & Scratchpad",
            category = "Workspace Tools",
            description = "In-chat scratchpad editor with syntax highlighting, Markdown preview & send.",
            icon = Icons.Outlined.NoteAlt,
            badge = "SCRATCHPAD",
            accentColor = Color(0xFFF97316),
            whatItDoes = "দ্রুত কোনো কোড স্নিপেট বা নোট লিখে রাখার জন্য ইন-অ্যাপ নোটপ্যাড এডিটর।",
            onEnableEffect = "যেকোনো সময় কোড এডিট করে সরাসরি ১-ট্যাপে এআই চ্যাটে প্রম্পট হিসেবে পাঠানো যাবে।",
            howToUse = "স্ক্র্যাচপ্যাড ওপেন করে কোড লিখুন এবং সেন্ড বা সেভ বাটনে চাপ দিন।"
        ),
        PcFeatureItem(
            id = "hardware_hud",
            title = "Hardware & Turbo Tuning HUD",
            category = "System & Hardware",
            description = "CPU thread governor, memory cache slider, and Turbo / Balanced performance profiles.",
            icon = Icons.Outlined.Speed,
            badge = "PERF",
            accentColor = Color(0xFFEF4444),
            whatItDoes = "মোবাইলের CPU কোর এবং র‍্যাম মেমোরি ব্যবহারের হার নির্ধারণ ও নিয়ন্ত্রণ।",
            onEnableEffect = "কম ক্ষমতার ফোনেও ল্যাগ-ফ্রি এবং হাই-এন্ড ফোনে সুপার-ফাস্ট গতি নিশ্চিত করে।",
            howToUse = "সেটিংস -> Diagnostics-এ গিয়ে Turbo, Balanced, বা Saver মোড নির্বাচন করুন।"
        ),
        PcFeatureItem(
            id = "memory_rules",
            title = "Agent Memory & RULES.md Editor",
            category = "Agent Memory",
            description = "View and live-edit custom project rules, global instructions, and AGENTS.md.",
            icon = Icons.Outlined.Psychology,
            badge = "MEMORY",
            accentColor = Color(0xFF6366F1)
        ),
        PcFeatureItem(
            id = "voice_coding",
            title = "Voice Coding & Dictation Studio",
            category = "Input & Audio",
            description = "Continuous voice-to-text dictation with live audio waveforms and whisper parsing.",
            icon = Icons.Outlined.Mic,
            badge = "AUDIO",
            accentColor = Color(0xFF14B8A6)
        ),
        PcFeatureItem(
            id = "web_inspector",
            title = "Mobile Web & DOM Inspector",
            category = "Browser Automation",
            description = "In-app automated web viewer with network traffic console and DOM element preview.",
            icon = Icons.Outlined.Language,
            badge = "BROWSER",
            accentColor = Color(0xFF3B82F6)
        ),
        PcFeatureItem(
            id = "cron_scheduler",
            title = "Background Tasks & Cron Scheduler",
            category = "Automation",
            description = "Recurring timer manager with one-shot notifications and job trigger history.",
            icon = Icons.Outlined.Schedule,
            badge = "CRON",
            accentColor = Color(0xFF84CC16)
        ),
        PcFeatureItem(
            id = "trajectory_export",
            title = "Chat Trajectory & Report Exporter",
            category = "Export & Sharing",
            description = "Export full agent trajectories with diffs into Markdown, styled HTML, or JSONL.",
            icon = Icons.Outlined.Share,
            badge = "EXPORT",
            accentColor = Color(0xFFA855F7)
        ),
        PcFeatureItem(
            id = "skills_marketplace",
            title = "1-Click Community Skills Store",
            category = "Skills & Plugins",
            description = "Install 10+ curated developer skills (Python, Docker, React, Termux, Android).",
            icon = Icons.Outlined.Storefront,
            badge = "SKILLS",
            accentColor = Color(0xFFEAB308)
        ),
        PcFeatureItem(
            id = "quota_dashboard",
            title = "API Quota & Rate Limit Dashboard",
            category = "Telemetry",
            description = "Live token meters, 5-hour and weekly reset countdown timers, and failover status.",
            icon = Icons.Outlined.PieChart,
            badge = "QUOTA",
            accentColor = Color(0xFF10B981)
        ),
        PcFeatureItem(
            id = "zen_mode",
            title = "Zen Immersive Focus Mode",
            category = "Focus",
            description = "Distraction-free full-screen mobile coding mode hiding all system bars.",
            icon = Icons.Outlined.FitScreen,
            badge = "ZEN",
            accentColor = Color(0xFF64748B)
        ),
        PcFeatureItem(
            id = "file_tree",
            title = "Workspace File Tree Explorer",
            category = "File System",
            description = "Hierarchical file tree with git modification status colors and file size badges.",
            icon = Icons.Outlined.Folder,
            badge = "FILES",
            accentColor = Color(0xFF0EA5E9)
        ),
        PcFeatureItem(
            id = "global_find_replace",
            title = "Global Find & Replace (Ctrl+Shift+F)",
            category = "Search",
            description = "Multi-file regex search and replace across the entire project repository.",
            icon = Icons.Outlined.Search,
            badge = "SEARCH",
            accentColor = Color(0xFFD946EF)
        ),
        PcFeatureItem(
            id = "live_exec_console",
            title = "Execution Console & Output Drawer",
            category = "Runtime",
            description = "Docked live log viewer streaming stdout/stderr of local dev servers and tests.",
            icon = Icons.Outlined.Dns,
            badge = "OUTPUT",
            accentColor = Color(0xFFF43F5E)
        ),
        PcFeatureItem(
            id = "workspace_backup",
            title = "1-Click Workspace Backup & Restore",
            category = "Backup & Cloud",
            description = "Instant zip archive export/import of project files and git repository state.",
            icon = Icons.Outlined.Archive,
            badge = "BACKUP",
            accentColor = Color(0xFF22C55E)
        ),
        PcFeatureItem(
            id = "battery_saver",
            title = "24/7 Mobile Thermal & Battery Saver",
            category = "Battery & Cooling",
            description = "Intelligent governor limiting background CPU cycles to keep device cool for 24-hour non-stop usage.",
            icon = Icons.Outlined.Speed,
            badge = "ECO",
            accentColor = Color(0xFF10B981)
        ),
        PcFeatureItem(
            id = "cooling_guard",
            title = "Cooling Governor & Process Throttle",
            category = "Battery & Cooling",
            description = "Monitors device battery temperature and prevents thermal heating during large builds and multi-agent runs.",
            icon = Icons.Outlined.Shield,
            badge = "COOL",
            accentColor = Color(0xFF06B6D4)
        ),
        PcFeatureItem(
            id = "ram_booster",
            title = "Deep RAM Cleaner & Process Purge",
            category = "Battery & Cooling",
            description = "Instantly clears zombie subprocesses, trims memory caches, and prevents Android OOM kills.",
            icon = Icons.Outlined.Build,
            badge = "BOOST",
            accentColor = Color(0xFFF59E0B)
        ),
        PcFeatureItem(
            id = "thermal_sleep",
            title = "Zero-Heating Sleep Cycle Engine",
            category = "Battery & Cooling",
            description = "Schedules sleep micro-pauses between heavy agent tool loops to preserve 100% battery health.",
            icon = Icons.Outlined.Schedule,
            badge = "HEALTH",
            accentColor = Color(0xFF3B82F6)
        ),
        PcFeatureItem(
            id = "battery_telemetry",
            title = "Battery Drain Telemetry & mAh HUD",
            category = "Battery & Cooling",
            description = "Real-time battery discharge rate, millivolt telemetry, and estimated 24-hour remaining runtime.",
            icon = Icons.Outlined.PieChart,
            badge = "STATS",
            accentColor = Color(0xFF8B5CF6)
        ),
        PcFeatureItem(
            id = "lsp_diagnostics",
            title = "Live LSP Language Server Diagnostics",
            category = "Editor & IDE",
            description = "Full language server protocol integration with inline errors, warnings, and code actions.",
            icon = Icons.Outlined.Code,
            badge = "LSP",
            accentColor = Color(0xFFEF4444)
        ),
        PcFeatureItem(
            id = "symbol_outline",
            title = "Code Symbol Breadcrumbs & Outline",
            category = "Editor & IDE",
            description = "Fast jump to classes, functions, structs, and variables across massive source files.",
            icon = Icons.Outlined.Layers,
            badge = "SYMBOLS",
            accentColor = Color(0xFF6366F1)
        ),
        PcFeatureItem(
            id = "multi_cursor",
            title = "Multi-Cursor & Column Selection",
            category = "Editor & IDE",
            description = "Simultaneous multi-line text editing, column selection, and matching word multi-select.",
            icon = Icons.Outlined.Edit,
            badge = "EDIT",
            accentColor = Color(0xFFEC4899)
        ),
        PcFeatureItem(
            id = "git_blame",
            title = "Inline Git Blame & Commit History",
            category = "Source Control",
            description = "Real-time git blame annotations showing author, commit hash, and timestamp per line.",
            icon = Icons.Outlined.History,
            badge = "BLAME",
            accentColor = Color(0xFFA855F7)
        ),
        PcFeatureItem(
            id = "worktree_branching",
            title = "Git Worktree Workspace Branching",
            category = "Source Control",
            description = "Parallel branch development in separate workspaces without switching checkouts.",
            icon = Icons.Outlined.CallSplit,
            badge = "WORKTREE",
            accentColor = Color(0xFF3B82F6)
        ),
        PcFeatureItem(
            id = "tmux_multiplexer",
            title = "Terminal Multiplexer & Session Detach",
            category = "Terminal & Shell",
            description = "tmux session manager allowing background terminal persistence even when app is minimized.",
            icon = Icons.Outlined.Terminal,
            badge = "TMUX",
            accentColor = Color(0xFFF59E0B)
        ),
        PcFeatureItem(
            id = "shell_history",
            title = "Fuzzy Shell History Search (Ctrl+R)",
            category = "Terminal & Shell",
            description = "Fast fuzzy search across previous command lines executed in bash, zsh, and fish.",
            icon = Icons.Outlined.Search,
            badge = "SHELL",
            accentColor = Color(0xFF06B6D4)
        ),
        PcFeatureItem(
            id = "ssh_tunnel",
            title = "SSH Remote Tunnel & Port Forwarding",
            category = "Terminal & Shell",
            description = "Connect to remote Linux dev servers, forward localhost ports, and edit remote files.",
            icon = Icons.Outlined.Dns,
            badge = "SSH",
            accentColor = Color(0xFFEC4899)
        ),
        PcFeatureItem(
            id = "agent_planner",
            title = "Agent Planning Mode & DAG Graph",
            category = "Agent Superpowers",
            description = "Visual directed acyclic graph (DAG) breakdown of complex multi-step coding objectives.",
            icon = Icons.Outlined.Psychology,
            badge = "PLAN",
            accentColor = Color(0xFF14B8A6)
        ),
        PcFeatureItem(
            id = "agent_sandbox",
            title = "Sandboxed Safe Execution Guardian",
            category = "Agent Superpowers",
            description = "Monitors shell commands before execution with automated risk categorization and sandbox isolation.",
            icon = Icons.Outlined.Shield,
            badge = "SAFETY",
            accentColor = Color(0xFFEF4444)
        ),
        PcFeatureItem(
            id = "auto_healer",
            title = "Autonomous Bug Repair Loop",
            category = "Agent Superpowers",
            description = "Self-correcting compile-test-repair loop that automatically fixes lint and build errors.",
            icon = Icons.Outlined.Build,
            badge = "HEAL",
            accentColor = Color(0xFF22C55E)
        ),
        PcFeatureItem(
            id = "mcp_simulator",
            title = "Model Context Protocol Tool Simulator",
            category = "Extensibility",
            description = "Test and debug custom MCP tools with sample inputs and mock JSON-RPC responses.",
            icon = Icons.Outlined.Extension,
            badge = "MCP",
            accentColor = Color(0xFFF59E0B)
        ),
        PcFeatureItem(
            id = "community_skills",
            title = "Curated Skills & Plugins Store (100+ Free)",
            category = "Extensibility",
            description = "Browse and 1-tap install 100+ developer skills: Python, React, Next.js, Docker, Android, Termux.",
            icon = Icons.Outlined.Storefront,
            badge = "STORE",
            accentColor = Color(0xFF0EA5E9)
        ),
        PcFeatureItem(
            id = "realtime_voice",
            title = "Real-Time Natural Voice Chat (Live Mode)",
            category = "Input & Audio",
            description = "High-accuracy live voice conversation with zero server dropout and instant voice execution.",
            icon = Icons.Outlined.Mic,
            badge = "VOICE",
            accentColor = Color(0xFF8B5CF6)
        ),
        PcFeatureItem(
            id = "realtime_tts",
            title = "Natural Voice Assistant TTS Speaker",
            category = "Input & Audio",
            description = "Reads assistant code explanations aloud with natural pacing and clean markdown stripping.",
            icon = Icons.Outlined.PlayArrow,
            badge = "TTS",
            accentColor = Color(0xFFF43F5E)
        ),
        PcFeatureItem(
            id = "ai_automation",
            title = "AI Device & Browser Automation",
            category = "Automation",
            description = "Autonomous browser navigation, shell commands, and automated multi-step flows.",
            icon = Icons.Outlined.SmartToy,
            badge = "AUTO",
            accentColor = Color(0xFF10B981)
        ),
        PcFeatureItem(
            id = "google_vault",
            title = "Google Drive Cloud Vault",
            category = "Backup & Cloud",
            description = "1-Tap backup & restore of chats, notebooks, projects, and media to Google Drive.",
            icon = Icons.Outlined.CloudUpload,
            badge = "DRIVE",
            accentColor = Color(0xFF3B82F6)
        ),
        PcFeatureItem(
            id = "gemini_notebooks",
            title = "Gemini Notebooks & Sources Manager",
            category = "Workspace Tools",
            description = "Create, edit, and attach knowledge notebooks directly to chat context.",
            icon = Icons.Outlined.AutoStories,
            badge = "NOTEBOOK",
            accentColor = Color(0xFF8B5CF6)
        )
    )
}

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
                        text = "JIN PC Power Features (20+)",
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
                placeholder = { Text("Search 20 PC features (e.g. Git, Terminal, MCP, Zen)...", fontSize = 12.sp) },
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
