package com.example.gemini.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.gemini.data.audio.ChatTtsHelper
import com.example.gemini.data.audio.NativeSpeechTranscriber
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.data.remote.DirectApiClient
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.GeminiBlue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.sin

enum class AssistantVoiceState {
    LISTENING,   // User speaking or waiting for user
    THINKING,    // AI generating / executing task
    SPEAKING     // TTS playing AI voice back to user
}

enum class VoicePersona(val displayName: String, val badgeColor: Color) {
    MAYA("Maya Voice", Color(0xFFEC4899)),
    JARVIS("Jarvis AI", Color(0xFF38BDF8))
}

private data class GalaxyStar(
    val phi: Float,
    val theta: Float,
    val distRatio: Float,
    val baseSize: Float,
    val baseAlpha: Float,
    val isRing: Boolean = false
)

/**
 * Strips code blocks, links, and markdown formatting so spoken TTS sounds completely natural.
 */
private fun cleanTextForVoiceSpeech(raw: String): String {
    return raw
        .replace(Regex("```[\\s\\S]*?```"), " কোড ব্লক তৈরি করা হয়েছে। ")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("[*_`#~>|]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(360)
}

/**
 * Instant, zero-latency bilingual conversational voice brain for Maya & Jarvis.
 * Guarantees natural spoken dialogue (<0.2s) in Bangla or English while workspace actions execute.
 */
private fun buildInstantConversationalVoiceReply(prompt: String, persona: VoicePersona): String {
    val p = prompt.lowercase().trim()
    val hasBangla = prompt.any { it in '\u0980'..'\u09FF' } ||
        listOf("kemon", "tumi", "amar", "koro", "ki", "bhalo", "hii", "hlw").any { p.contains(it) }

    val nameBn = if (persona == VoicePersona.MAYA) "মায়া" else "জার্ভিস"
    val nameEn = if (persona == VoicePersona.MAYA) "Maya" else "Jarvis"

    return if (hasBangla) {
        when {
            p.contains("কেমন আছ") || p.contains("kemon acho") || p.contains("হ্যালো") || p.contains("হাই") || p == "hello" || p == "hi" ->
                "হ্যালো! আমি $nameBn, একদম ভালো আছি এবং আপনার সাথে লাইভ ভয়েসে কথা বলার জন্য প্রস্তুত। বলুন, প্রজেক্ট বা কোডে আজ কী অ্যাকশন নেব?"
            p.contains("কাজ কী") || p.contains("কি করতে পার") || p.contains("তুমি কে") ->
                "আমি $nameBn, আপনার রিয়েল-টাইম ভয়েস এআই এবং কোডিং অ্যাসিস্ট্যান্ট। আমি আপনার সাথে কথা বলার পাশাপাশি সরাসরি প্রজেক্টের ফাইল চেক, কোড এডিট, বাগ ফিক্স এবং টার্মিনাল কমান্ড চালাতে পারি।"
            p.contains("বাগ") || p.contains("ফিক্স") || p.contains("এরর") || p.contains("সমস্যা") ->
                "ঠিক আছে! আমি এখনই আপনার প্রজেক্টের কোড ও বাগগুলো পরীক্ষা করছি এবং ফিক্স করার অ্যাকশন নিচ্ছি।"
            p.contains("ফাইল") || p.contains("চেক") || p.contains("প্রজেক্ট") ->
                "অবশ্যই, আমি এখনই আপনার ওয়ার্কস্পেসের ফাইলগুলো স্ক্যান করে বিস্তারিত দেখছি।"
            p.contains("অপ্টিমাইজ") || p.contains("স্পিড") || p.contains("ইম্প্রুভ") ->
                "দারুণ আইডিয়া! আমি প্রজেক্টের পারফরম্যান্স ও ইউআই অপ্টিমাইজ করার কাজ শুরু করে দিয়েছি।"
            else ->
                "বুঝতে পেরেছি! আমি আপনার নির্দেশ অনুযায়ী এখনই অ্যাকশন নিচ্ছি এবং ফলাফল প্রস্তুত করছি।"
        }
    } else {
        when {
            p.contains("how are you") || p == "hello" || p == "hi" || p.startsWith("hey") ->
                "Hello! I am $nameEn, your real-time voice AI and coding assistant. I'm listening live and ready to run any action in your workspace."
            p.contains("who are you") || p.contains("what can you do") ->
                "I am $nameEn, your live conversational coding agent. I can talk with you naturally while inspecting files, fixing bugs, and executing commands."
            p.contains("bug") || p.contains("fix") || p.contains("error") ->
                "On it! I am inspecting the codebase for bugs and applying the fixes right now."
            p.contains("file") || p.contains("check") || p.contains("analyze") || p.contains("explain") ->
                "Got it! I am analyzing your workspace files right now and will read out the findings."
            else ->
                "Understood! I am executing that action in your workspace right now."
        }
    }
}

@Composable
fun RealtimeVoiceAssistantDialog(
    modelDisplayName: String,
    latestAssistantMessage: String?,
    isStreaming: Boolean,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val speechTranscriber = remember(context) { NativeSpeechTranscriber(context) }
    val authPrefs = remember(context) { AuthPreferences(context) }

    var voiceState by remember { mutableStateOf(AssistantVoiceState.LISTENING) }
    var persona by remember { mutableStateOf(VoicePersona.MAYA) }
    var selectedLang by remember { mutableStateOf("auto") } // "auto", "bn-BD", "en-US"
    var userSpokenText by remember { mutableStateOf("") }
    var displayedSpokenText by remember { mutableStateOf("") }
    var directAiResponseText by remember { mutableStateOf("") }
    var manualInputText by remember { mutableStateOf("") }
    var showCaptions by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    // Track main chat assistant message hash separately so initial voice ack never blocks main chat result speech
    var lastSpokenAgentMsgHash by remember {
        mutableStateOf(latestAssistantMessage?.hashCode()?.toString().orEmpty())
    }
    var pendingAgentSpeechText by remember { mutableStateOf<String?>(null) }

    var silenceJob by remember { mutableStateOf<Job?>(null) }
    var liveVoiceJob by remember { mutableStateOf<Job?>(null) }

    val liveAmplitude by speechTranscriber.latestAmplitude.collectAsState()
    val isTtsSpeaking by ChatTtsHelper.isSpeaking.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            Toast.makeText(context, "Microphone permission required for Live Voice", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        ChatTtsHelper.init(context)
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopListening() {
        silenceJob?.cancel()
        speechTranscriber.stopTranscriptionSession()
    }

    fun speakOutLoud(id: String, text: String) {
        val clean = cleanTextForVoiceSpeech(text)
        if (clean.isBlank()) return
        directAiResponseText = clean
        voiceState = AssistantVoiceState.SPEAKING
        ChatTtsHelper.speak(context, id, clean)
    }

    fun triggerVoiceTurn(rawPrompt: String) {
        val cleanPrompt = rawPrompt.trim()
        if (cleanPrompt.isBlank()) return

        silenceJob?.cancel()
        liveVoiceJob?.cancel()
        speechTranscriber.stopTranscriptionSession()
        ChatTtsHelper.stop()
        pendingAgentSpeechText = null

        userSpokenText = ""
        displayedSpokenText = cleanPrompt
        directAiResponseText = ""
        voiceState = AssistantVoiceState.THINKING

        // 1. Dispatch to main Antigravity / active chat agent so workspace actions execute immediately
        onSendMessage(cleanPrompt)

        // 2. Immediately generate or stream a fast spoken conversational reply so Maya/Jarvis ALWAYS talks back
        liveVoiceJob = coroutineScope.launch {
            val geminiKey = authPrefs.getGeminiApiKeySync().trim()
            val openRouterKey = authPrefs.getOpenRouterApiKeySync().trim()
            val deepSeekKey = authPrefs.getDeepSeekApiKeySync().trim()
            val openAiKey = authPrefs.getOpenAiApiKeySync().trim()

            var streamedReply = ""

            if (geminiKey.isNotBlank() || openRouterKey.isNotBlank() || deepSeekKey.isNotBlank() || openAiKey.isNotBlank()) {
                val (baseUrl, apiKey, model) = when {
                    openRouterKey.isNotBlank() -> Triple("https://openrouter.ai/api/v1", openRouterKey, "openai/gpt-4o-mini")
                    deepSeekKey.isNotBlank() -> Triple("https://api.deepseek.com/v1", deepSeekKey, "deepseek-chat")
                    openAiKey.isNotBlank() -> Triple("https://api.openai.com/v1", openAiKey, "gpt-4o-mini")
                    else -> Triple("https://generativelanguage.googleapis.com/v1beta/openai", geminiKey, "gemini-2.5-flash")
                }
                val personaSystemPrompt = buildString {
                    appendLine("You are ${persona.displayName}, a real-time conversational voice AI and coding assistant.")
                    appendLine("Reply in 1 to 2 natural spoken sentences (no markdown code blocks) in the same language the user used (Bangla or English).")
                }
                val sb = StringBuilder()
                withTimeoutOrNull(3500L) {
                    DirectApiClient.streamChatMessages(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = model,
                        messages = listOf("user" to cleanPrompt),
                        systemInstruction = personaSystemPrompt,
                        onChunk = { chunk ->
                            sb.append(chunk)
                            directAiResponseText = sb.toString()
                        }
                    )
                }
                streamedReply = sb.toString().trim()
            }

            val finalImmediateReply = streamedReply.ifBlank {
                buildInstantConversationalVoiceReply(cleanPrompt, persona)
            }
            speakOutLoud("instant_${System.currentTimeMillis()}", finalImmediateReply)
        }
    }

    fun startListening() {
        if (!hasAudioPermission || isMuted) return
        voiceState = AssistantVoiceState.LISTENING
        userSpokenText = ""
        speechTranscriber.preferredLanguage = selectedLang

        speechTranscriber.startTranscriptionSession(
            scope = coroutineScope,
            cascadeId = "realtime_voice",
            preCursorText = "",
            postCursorText = "",
            onPartialText = { partial ->
                val cleaned = partial.trim()
                if (cleaned.isNotBlank()) {
                    userSpokenText = cleaned
                    displayedSpokenText = cleaned
                    silenceJob?.cancel()
                    silenceJob = coroutineScope.launch {
                        delay(1300)
                        if (userSpokenText.isNotBlank() && voiceState == AssistantVoiceState.LISTENING) {
                            triggerVoiceTurn(userSpokenText)
                        }
                    }
                }
            },
            onFinalText = { finalText ->
                val cleaned = finalText.trim()
                if (cleaned.isNotBlank()) {
                    userSpokenText = cleaned
                    displayedSpokenText = cleaned
                    silenceJob?.cancel()
                    silenceJob = coroutineScope.launch {
                        delay(400)
                        if (userSpokenText.isNotBlank() && voiceState == AssistantVoiceState.LISTENING) {
                            triggerVoiceTurn(userSpokenText)
                        }
                    }
                }
            },
            onError = { _ -> }
        )
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            startListening()
        }
    }

    // Stream & speak the main Antigravity agent's actual response when it arrives
    LaunchedEffect(isStreaming, latestAssistantMessage) {
        if (isStreaming && !latestAssistantMessage.isNullOrBlank()) {
            val cleanedLive = cleanTextForVoiceSpeech(latestAssistantMessage)
            if (cleanedLive.isNotBlank()) {
                directAiResponseText = cleanedLive
            }
        }
        if (!isStreaming && !latestAssistantMessage.isNullOrBlank()) {
            val msgHash = latestAssistantMessage.hashCode().toString()
            if (msgHash != lastSpokenAgentMsgHash) {
                lastSpokenAgentMsgHash = msgHash
                val cleanedFinal = cleanTextForVoiceSpeech(latestAssistantMessage)
                if (cleanedFinal.isNotBlank()) {
                    if (isTtsSpeaking) {
                        // Queue it so as soon as the initial conversational acknowledgement finishes, it speaks the agent result!
                        pendingAgentSpeechText = cleanedFinal
                    } else {
                        speakOutLoud("agent_$msgHash", cleanedFinal)
                    }
                }
            }
        }
    }

    // When TTS finishes speaking: if main agent finished a task response, speak it next; otherwise resume listening!
    LaunchedEffect(isTtsSpeaking) {
        if (!isTtsSpeaking && voiceState == AssistantVoiceState.SPEAKING) {
            val queued = pendingAgentSpeechText
            if (!queued.isNullOrBlank()) {
                pendingAgentSpeechText = null
                delay(250)
                speakOutLoud("queued_${System.currentTimeMillis()}", queued)
            } else {
                delay(400)
                if (!isMuted && hasAudioPermission) {
                    voiceState = AssistantVoiceState.LISTENING
                    startListening()
                } else {
                    voiceState = AssistantVoiceState.LISTENING
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            silenceJob?.cancel()
            liveVoiceJob?.cancel()
            speechTranscriber.cancelTranscriptionSession()
            ChatTtsHelper.stop()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "VoiceOrbTransition")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    Dialog(
        onDismissRequest = {
            stopListening()
            ChatTtsHelper.stop()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E14))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height * 0.36f)
                val glowRadius = size.width * 0.72f
                val glowColor = when (voiceState) {
                    AssistantVoiceState.LISTENING -> persona.badgeColor.copy(alpha = 0.18f)
                    AssistantVoiceState.THINKING -> Color(0xFF8B5CF6).copy(alpha = 0.24f)
                    AssistantVoiceState.SPEAKING -> Color(0xFFF59E0B).copy(alpha = 0.24f)
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = centerOffset,
                        radius = glowRadius
                    ),
                    center = centerOffset,
                    radius = glowRadius
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar: Persona Switcher (Maya / Jarvis), Language Pill, CC & Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            persona = if (persona == VoicePersona.MAYA) VoicePersona.JARVIS else VoicePersona.MAYA
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF171B26),
                        border = BorderStroke(1.dp, persona.badgeColor.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                tint = persona.badgeColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${persona.displayName} • ${modelDisplayName.take(14)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
                                selectedLang = when (selectedLang) {
                                    "auto" -> "bn-BD"
                                    "bn-BD" -> "en-US"
                                    else -> "auto"
                                }
                                speechTranscriber.preferredLanguage = selectedLang
                                if (voiceState == AssistantVoiceState.LISTENING) {
                                    stopListening()
                                    startListening()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF171B26),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = "Language",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (selectedLang) {
                                        "bn-BD" -> "বাংলা"
                                        "en-US" -> "EN"
                                        else -> "Auto"
                                    },
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            onClick = { showCaptions = !showCaptions },
                            shape = RoundedCornerShape(16.dp),
                            color = if (showCaptions) Color(0xFF38BDF8).copy(alpha = 0.18f) else Color(0xFF171B26),
                            border = BorderStroke(1.dp, if (showCaptions) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0xFF2D3348))
                        ) {
                            Text(
                                text = if (showCaptions) "CC" else "CC Off",
                                fontSize = 11.sp,
                                color = if (showCaptions) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            onClick = {
                                stopListening()
                                ChatTtsHelper.stop()
                                onDismiss()
                            },
                            shape = CircleShape,
                            color = Color(0xFF171B26),
                            border = BorderStroke(1.dp, Color(0xFF2D3348)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close voice mode",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3D Interactive Galaxy Voice Sphere
                val galaxyStars = remember {
                    val list = ArrayList<GalaxyStar>(130)
                    val random = java.util.Random(1337)
                    for (i in 0 until 80) {
                        val phi = random.nextFloat() * (2f * Math.PI.toFloat())
                        val theta = (random.nextFloat() - 0.5f) * Math.PI.toFloat()
                        val dist = 0.35f + random.nextFloat() * 0.65f
                        val size = 2f + random.nextFloat() * 3f
                        val alpha = 0.45f + random.nextFloat() * 0.55f
                        list.add(GalaxyStar(phi, theta, dist, size, alpha, false))
                    }
                    for (i in 0 until 50) {
                        val phi = (i.toFloat() / 50f) * (2f * Math.PI.toFloat())
                        val theta = (random.nextFloat() - 0.5f) * 0.22f
                        val dist = 0.75f + random.nextFloat() * 0.32f
                        val size = 2.2f + random.nextFloat() * 2.8f
                        val alpha = 0.65f + random.nextFloat() * 0.35f
                        list.add(GalaxyStar(phi, theta, dist, size, alpha, true))
                    }
                    list
                }

                val galaxyYaw by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(14000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "GalaxyYaw"
                )

                val galaxyPitch by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(22000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "GalaxyPitch"
                )

                Box(
                    modifier = Modifier
                        .size(205.dp)
                        .clip(CircleShape)
                        .clickable {
                            when (voiceState) {
                                AssistantVoiceState.LISTENING -> {
                                    if (displayedSpokenText.isNotBlank()) {
                                        triggerVoiceTurn(displayedSpokenText)
                                    } else {
                                        stopListening()
                                        startListening()
                                    }
                                }
                                AssistantVoiceState.SPEAKING -> {
                                    pendingAgentSpeechText = null
                                    ChatTtsHelper.stop()
                                    voiceState = AssistantVoiceState.LISTENING
                                    startListening()
                                }
                                AssistantVoiceState.THINKING -> {
                                    liveVoiceJob?.cancel()
                                    voiceState = AssistantVoiceState.LISTENING
                                    startListening()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val orbColor = when (voiceState) {
                        AssistantVoiceState.LISTENING -> persona.badgeColor
                        AssistantVoiceState.THINKING -> Color(0xFFA855F7)
                        AssistantVoiceState.SPEAKING -> Color(0xFFF59E0B)
                    }

                    val speakingPulse = if (voiceState == AssistantVoiceState.SPEAKING) 0.28f * glowAlpha else 0f
                    val effectiveAmplitude = (liveAmplitude + speakingPulse).coerceIn(0f, 1f)

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val baseRadius = (size.minDimension / 2.2f) * (1f + effectiveAmplitude * 0.36f)

                        val coreRadius = baseRadius * 0.44f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    orbColor.copy(alpha = 0.85f * glowAlpha),
                                    orbColor.copy(alpha = 0.35f * glowAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = coreRadius * (1f + effectiveAmplitude * 0.5f)
                            ),
                            center = Offset(cx, cy),
                            radius = coreRadius * (1f + effectiveAmplitude * 0.5f)
                        )

                        val radYaw = Math.toRadians(galaxyYaw.toDouble()).toFloat()
                        val radPitch = Math.toRadians(galaxyPitch.toDouble()).toFloat()
                        val cosPitch = cos(radPitch)
                        val sinPitch = sin(radPitch)

                        for (star in galaxyStars) {
                            val r = baseRadius * star.distRatio
                            val anglePhi = star.phi + radYaw
                            val cosTheta = cos(star.theta)
                            val sinTheta = sin(star.theta)

                            val x = r * cosTheta * cos(anglePhi)
                            val y = r * sinTheta
                            val z = r * cosTheta * sin(anglePhi)

                            val yRot = y * cosPitch - z * sinPitch
                            val zRot = y * sinPitch + z * cosPitch
                            val xRot = x

                            val cameraDist = 380f
                            val k = cameraDist / (cameraDist + zRot + 140f)
                            val px = cx + xRot * k
                            val py = cy + yRot * k
                            val drawRadius = (star.baseSize * k * (1f + effectiveAmplitude * 0.3f)).coerceAtLeast(1.2f)
                            val starAlpha = (star.baseAlpha * k * glowAlpha * 1.2f).coerceIn(0.15f, 1f)

                            val starColor = if (star.isRing) {
                                when (voiceState) {
                                    AssistantVoiceState.LISTENING -> Color(0xFF67E8F9)
                                    AssistantVoiceState.THINKING -> Color(0xFFE879F9)
                                    AssistantVoiceState.SPEAKING -> Color(0xFFFDE047)
                                }
                            } else {
                                orbColor
                            }

                            drawCircle(
                                color = starColor.copy(alpha = starAlpha),
                                radius = drawRadius,
                                center = Offset(px, py)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF1E2230).copy(alpha = 0.92f),
                                        Color(0xFF0F1117).copy(alpha = 0.96f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (voiceState) {
                                AssistantVoiceState.LISTENING -> Icons.Rounded.GraphicEq
                                AssistantVoiceState.THINKING -> Icons.Rounded.AutoAwesome
                                AssistantVoiceState.SPEAKING -> Icons.Rounded.VolumeUp
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val (stateTitleBangla, stateTitleEn) = when (voiceState) {
                    AssistantVoiceState.LISTENING ->
                        "${persona.displayName} শুনছে... কথা বলুন" to "Listening live • Speak naturally or tap a command"
                    AssistantVoiceState.THINKING ->
                        "${persona.displayName} উত্তর তৈরি করছে ও অ্যাকশন নিচ্ছে..." to "Thinking & executing action in workspace..."
                    AssistantVoiceState.SPEAKING ->
                        "${persona.displayName} কথা বলছে... (থামাতে ট্যাপ করুন)" to "Speaking aloud • Tap orb anytime to interrupt"
                }

                Text(
                    text = stateTitleBangla,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stateTitleEn,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                AnimatedVisibility(
                    visible = showCaptions,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF141822),
                        border = BorderStroke(
                            1.dp,
                            when {
                                directAiResponseText.isNotBlank() -> Color(0xFFF59E0B).copy(alpha = 0.55f)
                                displayedSpokenText.isNotBlank() -> persona.badgeColor.copy(alpha = 0.5f)
                                else -> Color(0xFF242B3D)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 78.dp, max = 148.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(13.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (displayedSpokenText.isNotBlank()) {
                                Text(
                                    text = "🎤 “$displayedSpokenText”",
                                    fontSize = 13.5.sp,
                                    color = Color(0xFFE2E8F0),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (directAiResponseText.isNotBlank()) {
                                if (displayedSpokenText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                Text(
                                    text = "🔊 $directAiResponseText",
                                    fontSize = 13.sp,
                                    color = Color(0xFFFDE68A),
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (displayedSpokenText.isBlank() && directAiResponseText.isBlank()) {
                                Text(
                                    text = "“হ্যালো মায়া, কেমন আছো?” অথবা “এই প্রজেক্টের বাগ চেক করো” বলুন",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val quickVoicePrompts = remember {
                    listOf(
                        "হ্যালো মায়া, কেমন আছো?",
                        "এই প্রজেক্টের ফাইলগুলো চেক করো",
                        "কোড অপ্টিমাইজ এবং বাগ ফিক্স করো",
                        "Jarvis, analyze workspace status"
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickVoicePrompts.forEach { preset ->
                        Surface(
                            onClick = { triggerVoiceTurn(preset) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF171C28),
                            border = BorderStroke(1.dp, Color(0xFF2B3346))
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.5.sp,
                                color = Color(0xFFCBD5E1),
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        placeholder = {
                            Text(
                                "Type or speak to ${persona.displayName}...",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = persona.badgeColor,
                            unfocusedBorderColor = Color(0xFF283044),
                            focusedContainerColor = Color(0xFF141822),
                            unfocusedContainerColor = Color(0xFF141822),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    FilledIconButton(
                        onClick = {
                            if (manualInputText.isNotBlank()) {
                                val msg = manualInputText
                                manualInputText = ""
                                triggerVoiceTurn(msg)
                            }
                        },
                        enabled = manualInputText.isNotBlank(),
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = persona.badgeColor,
                            disabledContainerColor = Color(0xFF1E2434)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send to voice assistant",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            isMuted = !isMuted
                            if (isMuted) {
                                stopListening()
                            } else if (voiceState == AssistantVoiceState.LISTENING) {
                                startListening()
                            }
                        },
                        shape = CircleShape,
                        color = if (isMuted) Color(0xFFDC2626).copy(alpha = 0.2f) else Color(0xFF171B26),
                        border = BorderStroke(1.dp, if (isMuted) Color(0xFFDC2626) else Color(0xFF2D3348)),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = if (isMuted) Color(0xFFEF4444) else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (displayedSpokenText.isNotBlank() && voiceState == AssistantVoiceState.LISTENING) {
                        Surface(
                            onClick = { triggerVoiceTurn(displayedSpokenText) },
                            shape = CircleShape,
                            color = GeminiBlue,
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Send voice query now",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    } else if (voiceState == AssistantVoiceState.SPEAKING || voiceState == AssistantVoiceState.THINKING) {
                        Surface(
                            onClick = {
                                pendingAgentSpeechText = null
                                liveVoiceJob?.cancel()
                                ChatTtsHelper.stop()
                                voiceState = AssistantVoiceState.LISTENING
                                startListening()
                            },
                            shape = CircleShape,
                            color = ClaudeTerracotta,
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Interrupt speech",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    } else if (directAiResponseText.isNotBlank()) {
                        Surface(
                            onClick = {
                                speakOutLoud("replay_${System.currentTimeMillis()}", directAiResponseText)
                            },
                            shape = CircleShape,
                            color = Color(0xFF171B26),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = "Replay voice response",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            stopListening()
                            ChatTtsHelper.stop()
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = Color(0xFF2D1B22),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "End conversation",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
