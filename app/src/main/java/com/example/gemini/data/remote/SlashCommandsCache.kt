package com.example.gemini.data.remote

import android.util.Log
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.data.remote.services.AgyProjectService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SlashCommandItem(
    val name: String,
    val command: String,
    val description: String,
    val type: String = "skill", // "skill" or "command"
    val pluginName: String? = null,
    val path: String = "",
    /** What the command expects after its name (Claude's `argumentHint`); blank = it runs as is. */
    val argumentHint: String = ""
)

object SlashCommandsCache {
    private const val TAG = "SlashCommandsCache"
    private val mutex = Mutex()
    private var inMemoryCache: List<SlashCommandItem>? = null

    // Standard built-in Antigravity slash commands
    val BUILTIN_COMMANDS = listOf(
        SlashCommandItem("optimize", "/optimize", "Open Antigravity 2.0 Turbo Optimizer & PC Suite (Speed, Accuracy, RAM, WAL)", "command"),
        SlashCommandItem("subagent", "/subagent", "Manage and invoke Antigravity 2.0 subagents (self, research, image-generator, custom)", "command"),
        SlashCommandItem("rules", "/rules", "Edit workspace & global GEMINI.md / AGENTS.md rules", "command"),
        SlashCommandItem("skills", "/skills", "Create, edit, or migrate SKILL.md skills", "command"),
        SlashCommandItem("goal", "/goal", "Run a long-running task overnight and be extra thorough until the goal is achieved", "command"),
        SlashCommandItem("schedule", "/schedule", "Schedule a one-shot timer or recurring cron job", "command"),
        SlashCommandItem("browser", "/browser", "Interact with web applications or search and browse the web", "command"),
        SlashCommandItem("plan", "/plan", "Create an implementation plan before taking action", "command"),
        SlashCommandItem("grill-me", "/grill-me", "Interactive interview to resolve design decisions and align on approach", "command"),
        SlashCommandItem("teamwork-preview", "/teamwork-preview", "Coordinate a team of autonomous agents working together", "command"),
        SlashCommandItem("learn", "/learn", "Persist custom setup and learned behaviors for future tasks", "command"),
        SlashCommandItem("boost", "/boost", "Deep thinking, strategic planning, and verification for complex tasks", "command"),
        SlashCommandItem("terminal", "/terminal", "Open terminal session or execute local shell commands", "command"),
        SlashCommandItem("ide", "/ide", "Open the code editor IDE", "command")
    )

    // Fallback known skills
    // Fallback known skills and 30 awesome Agentic Skills
    val DEFAULT_SKILLS = listOf(
        SlashCommandItem("android-cli", "/android-cli", "Android CLI tools, emulator, SDK management, APK building", "skill", "android-cli-plugin"),
        SlashCommandItem("youtube-control", "/youtube-control", "Full YouTube Studio API control, video upload, analytics, channel management", "skill", "youtube-agent-plugin"),
        SlashCommandItem("instagram-automation", "/instagram-automation", "Full Insta control: posting, DM management, analytics, story scheduling", "skill", "insta-agent-plugin"),
        SlashCommandItem("facebook-page-manager", "/facebook-page-manager", "Facebook Graph API: auto-reply, post scheduling, page insights", "skill", "fb-agent-plugin"),
        SlashCommandItem("tiktok-creator", "/tiktok-creator", "TikTok content upload, trending audio analysis, stats tracker", "skill", "tiktok-agent-plugin"),
        SlashCommandItem("github-copilot-local", "/github-copilot-local", "Local code completions, PR reviews, issue management via GitHub API", "skill", "permissioned-github"),
        SlashCommandItem("openrouter-free-tier", "/openrouter-free-tier", "Access 1B+ token limits using OpenRouter free endpoints", "skill", "openrouter-plugin"),
        SlashCommandItem("deepseek-coder", "/deepseek-coder", "Local execution and code generation with DeepSeek V3", "skill", "deepseek-plugin"),
        SlashCommandItem("web-scraper-pro", "/web-scraper-pro", "Advanced headless browser automation and data extraction", "skill", "browser-automation-mcp"),
        SlashCommandItem("telegram-bot-builder", "/telegram-bot-builder", "Instantly deploy and manage Telegram bots", "skill", "tg-bot-plugin"),
        SlashCommandItem("discord-mod-ai", "/discord-mod-ai", "Discord server moderation, auto-reply, webhook integrations", "skill", "discord-plugin"),
        SlashCommandItem("whatsapp-auto", "/whatsapp-auto", "WhatsApp Business API auto-responder and CRM", "skill", "wa-business-plugin"),
        SlashCommandItem("spotify-dj", "/spotify-dj", "Spotify API: playlist generation, playback control", "skill", "spotify-plugin"),
        SlashCommandItem("notion-second-brain", "/notion-second-brain", "Notion API: organize notes, databases, auto-sync tasks", "skill", "notion-plugin"),
        SlashCommandItem("google-drive-sync", "/google-drive-sync", "Full Google Drive file management, Docs/Sheets automation", "skill", "gdrive-plugin"),
        SlashCommandItem("aws-admin", "/aws-admin", "AWS CLI automation: EC2, S3, Lambda management", "skill", "aws-plugin"),
        SlashCommandItem("docker-orchestrator", "/docker-orchestrator", "Docker & Kubernetes local cluster management", "skill", "docker-plugin"),
        SlashCommandItem("vercel-deployer", "/vercel-deployer", "One-click deployment and CI/CD management for web apps", "skill", "vercel-plugin"),
        SlashCommandItem("figma-to-code", "/figma-to-code", "Convert Figma designs to Jetpack Compose / React code", "skill", "figma-plugin"),
        SlashCommandItem("crypto-trader", "/crypto-trader", "Binance API: live ticker monitoring and algorithmic trading", "skill", "crypto-plugin"),
        SlashCommandItem("stripe-billing", "/stripe-billing", "Stripe API: manage subscriptions, invoices, and payments", "skill", "stripe-plugin"),
        SlashCommandItem("twilio-sms", "/twilio-sms", "Twilio API: send SMS, voice calls, and OTPs", "skill", "twilio-plugin"),
        SlashCommandItem("sendgrid-mailer", "/sendgrid-mailer", "Email marketing automation and transactional emails", "skill", "sendgrid-plugin"),
        SlashCommandItem("slack-workspace", "/slack-workspace", "Slack API: team messaging, custom bot integrations", "skill", "slack-plugin"),
        SlashCommandItem("jira-agile", "/jira-agile", "Jira API: sprint planning, issue tracking, agile boards", "skill", "jira-plugin"),
        SlashCommandItem("cloudflare-dns", "/cloudflare-dns", "Cloudflare API: DNS management, caching, security rules", "skill", "cloudflare-plugin"),
        SlashCommandItem("reddit-scraper", "/reddit-scraper", "Reddit API: subreddit monitoring, trend analysis", "skill", "reddit-plugin"),
        SlashCommandItem("x-twitter-bot", "/x-twitter-bot", "X (Twitter) API: automated tweeting, engagement tracking", "skill", "twitter-plugin"),
        SlashCommandItem("huggingface-hub", "/huggingface-hub", "Download, run, and finetune models from HuggingFace", "skill", "huggingface-plugin"),
        SlashCommandItem("sql-database-admin", "/sql-database-admin", "Direct MySQL/PostgreSQL schema management and queries", "skill", "database-plugin"),
        SlashCommandItem("termux-root-tools", "/termux-root-tools", "Full Termux root file system control and proot environment", "skill", "termux-plugin"),
        SlashCommandItem("modern-web-guidance", "/modern-web-guidance", "Search tool for modern web development best practices", "skill", "modern-web-guidance-plugin"),
        SlashCommandItem("agy-customizations", "/agy-customizations", "Comprehensive guide for skills, rules, plugins, hooks, MCP servers", "skill"),
        SlashCommandItem("antigravity-guide", "/antigravity-guide", "Comprehensive guide and quick reference for Google Antigravity", "skill"),
        SlashCommandItem("generative_ui", "/generative_ui", "Render rich interactive HTML widgets inline in chat", "skill"),
        SlashCommandItem("migrate-workflows", "/migrate-workflows", "Automatically migrate legacy workflows to modern skills", "skill")
    )

    /**
     * Returns cached slash commands & skills immediately from RAM.
     * When cache is empty, queries the gRPC AGY Hub API (/exa.language_server_pb.LanguageServerService/GetAllSkills),
     * merges skills and built-in slash commands, and caches the result in RAM.
     */
    suspend fun getCommands(
        hubUrl: String = AuthPreferences.currentHubUrl,
        forceRefresh: Boolean = false
    ): List<SlashCommandItem> {
        // Fast-path: RAM Cache hit (0ms)
        if (!forceRefresh && inMemoryCache != null && inMemoryCache!!.isNotEmpty()) {
            return inMemoryCache!!
        }

        return mutex.withLock {
            if (!forceRefresh && inMemoryCache != null && inMemoryCache!!.isNotEmpty()) {
                return@withLock inMemoryCache!!
            }

            val items = mutableListOf<SlashCommandItem>()
            val seenNames = mutableSetOf<String>()

            // 1. Fetch live skills from AGY Hub gRPC API (GetAllSkills)
            try {
                val skillResult = AgyProjectService.instance.fetchAllSkills(hubUrl)
                if (skillResult.isSuccess) {
                    val hubSkills = skillResult.getOrThrow()
                    for (s in hubSkills) {
                        if (seenNames.add(s.name.lowercase())) {
                            val cleanDesc = s.description.trim().lines().firstOrNull { it.isNotBlank() } ?: s.description.trim()
                            items.add(
                                SlashCommandItem(
                                    name = s.name,
                                    command = "/${s.name}",
                                    description = cleanDesc,
                                    type = "skill",
                                    pluginName = s.pluginName,
                                    path = s.path
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GetAllSkills RPC failed: ${e.message}")
            }

            // 2. Add default fallback skills if not already found
            for (s in DEFAULT_SKILLS) {
                if (seenNames.add(s.name.lowercase())) {
                    items.add(s)
                }
            }

            // 3. Add built-in slash commands
            for (cmd in BUILTIN_COMMANDS) {
                if (seenNames.add(cmd.name.lowercase())) {
                    items.add(cmd)
                }
            }

            items.sortBy { it.name.lowercase() }
            inMemoryCache = items
            items
        }
    }

    fun getCachedSync(): List<SlashCommandItem> {
        return inMemoryCache ?: (DEFAULT_SKILLS + BUILTIN_COMMANDS).sortedBy { it.name.lowercase() }
    }

    fun clearCache() {
        inMemoryCache = null
    }
}

