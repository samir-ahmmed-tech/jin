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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.gemini.data.audio.ChatTtsHelper
import com.example.gemini.data.audio.NativeSpeechTranscriber
import com.example.gemini.data.remote.DirectApiClient
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.GeminiBlue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

enum class AssistantVoiceState {
    LISTENING,   // User speaking or waiting for user
    THINKING,    // AI generating / executing task
    SPEAKING     // TTS playing AI voice back to user
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
 * Immersive, Hands-Free Real-Time Voice Assistant.
 * Allows natural 2-way vocal conversation in Bangla & English and hands-free task execution:
 * 1. Listens continuously to user speech (hybrid SpeechRecognizer + Direct AudioRecord VAD fallback).
 * 2. Transcribes in real-time and detects pauses.
 * 3. Sends prompt to active AI agent AND guarantees a live spoken response via DirectApiClient fallback
 *    even if the local bridge/daemon is offline.
 * 4. Speaks assistant responses back out loud via [ChatTtsHelper] (with auto Bangla/English TTS voice switching).
 * 5. Automatically loops back to listening for seamless hands-free conversation.
 */
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

    var voiceState by remember { mutableStateOf(AssistantVoiceState.LISTENING) }
    var selectedLang by remember { mutableStateOf("auto") } // "auto", "bn-BD", "en-US"
    var userSpokenText by remember { mutableStateOf("") }
    var displayedSpokenText by remember { mutableStateOf("") }
    var directAiResponseText by remember { mutableStateOf("") }
    var showCaptions by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var lastSpokenMessageId by remember {
        mutableStateOf(latestAssistantMessage?.hashCode()?.toString().orEmpty())
    }
    var silenceJob by remember { mutableStateOf<Job?>(null) }
    var fallbackVoiceJob by remember { mutableStateOf<Job?>(null) }

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
            Toast.makeText(context, "Microphone permission required for Voice Assistant", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    // Pre-warm TTS and request audio permission on open
    LaunchedEffect(Unit) {
        ChatTtsHelper.init(context)
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                userSpokenText = partial
                displayedSpokenText = partial
                directAiResponseText = ""
                silenceJob?.cancel()
                // Ignore status placeholders from triggering auto-send
                if (!partial.startsWith("শুনছি...") && !partial.startsWith("Transcribing")) {
                    silenceJob = coroutineScope.launch {
                        delay(1500)
                        if (userSpokenText.isNotBlank() && voiceState == AssistantVoiceState.LISTENING) {
                            val textToSend = userSpokenText.trim()
                            userSpokenText = ""
                            speechTranscriber.stopTranscriptionSession()
                            voiceState = AssistantVoiceState.THINKING
                            onSendMessage(textToSend)
                        }
                    }
                }
            },
            onFinalText = { finalText ->
                userSpokenText = finalText
                displayedSpokenText = finalText
                directAiResponseText = ""
                silenceJob?.cancel()
                silenceJob = coroutineScope.launch {
                    delay(650)
                    if (userSpokenText.isNotBlank() && voiceState == AssistantVoiceState.LISTENING) {
                        val textToSend = userSpokenText.trim()
                        userSpokenText = ""
                        speechTranscriber.stopTranscriptionSession()
                        voiceState = AssistantVoiceState.THINKING
                        onSendMessage(textToSend)
                    }
                }
            },
            onError = { _ ->
                // Handled automatically by NativeSpeechTranscriber hybrid engine
            }
        )
    }

    fun stopListening() {
        silenceJob?.cancel()
        speechTranscriber.stopTranscriptionSession()
    }

    // Guaranteed Direct Voice AI Fallback if main chat backend does not start streaming within 2.2s
    LaunchedEffect(voiceState) {
        fallbackVoiceJob?.cancel()
        if (voiceState == AssistantVoiceState.THINKING) {
            val promptSnapshot = displayedSpokenText.trim()
            val initialMsgHash = latestAssistantMessage?.hashCode()?.toString().orEmpty()
            fallbackVoiceJob = coroutineScope.launch {
                delay(2200)
                val currentMsgHash = latestAssistantMessage?.hashCode()?.toString().orEmpty()
                if (voiceState == AssistantVoiceState.THINKING && !isStreaming && currentMsgHash == initialMsgHash && promptSnapshot.isNotBlank()) {
                    val voiceSystemPrompt = buildString {
                        appendLine("You are Antigravity Live Voice Assistant speaking directly with the user.")
                        appendLine("Keep your response natural, helpful, and concise (2 to 4 sentences) so it sounds great when spoken aloud.")
                        appendLine("Reply in the SAME language the user spoke (if Bangla, reply in natural Bangla; if English, reply in English).")
                    }
                    val sb = StringBuilder()
                    DirectApiClient.streamChatMessages(
                        baseUrl = DirectApiClient.ZERO_KEY_GATEWAY_URL,
                        apiKey = "",
                        model = "openai",
                        messages = listOf("user" to promptSnapshot),
                        systemInstruction = voiceSystemPrompt,
                        onChunk = { chunk: String ->
                            sb.append(chunk)
                            directAiResponseText = sb.toString()
                        },
                        onComplete = {
                            val reply = sb.toString().trim()
                            if (reply.isNotBlank() && voiceState == AssistantVoiceState.THINKING) {
                                val replyHash = reply.hashCode().toString()
                                lastSpokenMessageId = replyHash
                                voiceState = AssistantVoiceState.SPEAKING
                                ChatTtsHelper.speak(context, replyHash, reply)
                            } else if (voiceState == AssistantVoiceState.THINKING) {
                                voiceState = AssistantVoiceState.LISTENING
                                startListening()
                            }
                        },
                        onError = { _ ->
                            if (voiceState == AssistantVoiceState.THINKING) {
                                voiceState = AssistantVoiceState.LISTENING
                                startListening()
                            }
                        }
                    )
                }
            }
        }
    }

    // Initialize listening session on enter
    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            startListening()
        }
    }

    // Transition from THINKING -> SPEAKING when main chat AI completes streaming
    LaunchedEffect(isStreaming, latestAssistantMessage) {
        if (isStreaming && voiceState == AssistantVoiceState.THINKING) {
            // Main chat started streaming! Cancel fallback voice job and show live preview
            fallbackVoiceJob?.cancel()
            if (!latestAssistantMessage.isNullOrBlank()) {
                directAiResponseText = latestAssistantMessage
            }
        }
        if (!isStreaming && voiceState == AssistantVoiceState.THINKING && !latestAssistantMessage.isNullOrBlank()) {
            val msgHash = latestAssistantMessage.hashCode().toString()
            if (msgHash != lastSpokenMessageId) {
                fallbackVoiceJob?.cancel()
                lastSpokenMessageId = msgHash
                directAiResponseText = latestAssistantMessage
                voiceState = AssistantVoiceState.SPEAKING
                ChatTtsHelper.speak(context, msgHash, latestAssistantMessage)
            }
        }
    }

    // Transition from SPEAKING -> LISTENING when TTS finishes speaking
    LaunchedEffect(isTtsSpeaking) {
        if (!isTtsSpeaking && voiceState == AssistantVoiceState.SPEAKING) {
            delay(450) // Brief natural conversational pause
            if (!isMuted) {
                voiceState = AssistantVoiceState.LISTENING
                startListening()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            silenceJob?.cancel()
            fallbackVoiceJob?.cancel()
            speechTranscriber.cancelTranscriptionSession()
            ChatTtsHelper.stop()
        }
    }

    // Orb animation transitions
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceOrbTransition")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
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
                .background(Color(0xFF0F1117))
        ) {
            // Background subtle gradient glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height * 0.40f)
                val glowRadius = size.width * 0.7f
                val glowColor = when (voiceState) {
                    AssistantVoiceState.LISTENING -> Color(0xFF0284C7).copy(alpha = 0.18f)
                    AssistantVoiceState.THINKING -> Color(0xFF7C3AED).copy(alpha = 0.22f)
                    AssistantVoiceState.SPEAKING -> Color(0xFFEA580C).copy(alpha = 0.22f)
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
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar: Model, Language Selector, Captions & Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E2230),
                        border = BorderStroke(1.dp, Color(0xFF2D3348))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = ClaudeTerracotta,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = modelDisplayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Language Switcher Pill: Auto -> বাংলা -> English
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
                            color = Color(0xFF1E2230),
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
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            onClick = { showCaptions = !showCaptions },
                            shape = RoundedCornerShape(16.dp),
                            color = if (showCaptions) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF1E2230),
                            border = BorderStroke(1.dp, if (showCaptions) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0xFF2D3348))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showCaptions) "CC: ON" else "CC: OFF",
                                    fontSize = 11.sp,
                                    color = if (showCaptions) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            onClick = {
                                stopListening()
                                ChatTtsHelper.stop()
                                onDismiss()
                            },
                            shape = CircleShape,
                            color = Color(0xFF1E2230),
                            border = BorderStroke(1.dp, Color(0xFF2D3348)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close voice mode",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.10f))

                // Center 3D Rotating Galaxy Voice Sphere
                val galaxyStars = remember {
                    val list = ArrayList<GalaxyStar>(140)
                    val random = java.util.Random(1337)
                    for (i in 0 until 85) {
                        val phi = random.nextFloat() * (2f * Math.PI.toFloat())
                        val theta = (random.nextFloat() - 0.5f) * Math.PI.toFloat()
                        val dist = 0.35f + random.nextFloat() * 0.65f
                        val size = 2f + random.nextFloat() * 3f
                        val alpha = 0.45f + random.nextFloat() * 0.55f
                        list.add(GalaxyStar(phi, theta, dist, size, alpha, false))
                    }
                    for (i in 0 until 55) {
                        val phi = (i.toFloat() / 55f) * (2f * Math.PI.toFloat())
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
                        .size(240.dp)
                        .clip(CircleShape)
                        .clickable {
                            when (voiceState) {
                                AssistantVoiceState.LISTENING -> {
                                    if (displayedSpokenText.isNotBlank() &&
                                        !displayedSpokenText.startsWith("শুনছি...") &&
                                        !displayedSpokenText.startsWith("Transcribing")
                                    ) {
                                        val textToSend = displayedSpokenText.trim()
                                        stopListening()
                                        voiceState = AssistantVoiceState.THINKING
                                        onSendMessage(textToSend)
                                    } else {
                                        stopListening()
                                        startListening()
                                    }
                                }
                                AssistantVoiceState.SPEAKING -> {
                                    ChatTtsHelper.stop()
                                    voiceState = AssistantVoiceState.LISTENING
                                    startListening()
                                }
                                AssistantVoiceState.THINKING -> {}
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val orbColor = when (voiceState) {
                        AssistantVoiceState.LISTENING -> Color(0xFF38BDF8)
                        AssistantVoiceState.THINKING -> Color(0xFFA855F7)
                        AssistantVoiceState.SPEAKING -> Color(0xFFF59E0B)
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val baseRadius = (size.minDimension / 2.2f) * (1f + liveAmplitude * 0.38f)

                        val coreRadius = baseRadius * 0.42f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    orbColor.copy(alpha = 0.85f * glowAlpha),
                                    orbColor.copy(alpha = 0.35f * glowAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = coreRadius * (1f + liveAmplitude * 0.5f)
                            ),
                            center = Offset(cx, cy),
                            radius = coreRadius * (1f + liveAmplitude * 0.5f)
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
                            val drawRadius = (star.baseSize * k * (1f + liveAmplitude * 0.3f)).coerceAtLeast(1.2f)
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

                    // Inner Floating Icon Core
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF1E2230).copy(alpha = 0.9f),
                                        Color(0xFF0F1117).copy(alpha = 0.95f)
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
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Voice State Title & Guidance
                val (stateTitleBangla, stateTitleEn) = when (voiceState) {
                    AssistantVoiceState.LISTENING -> "শুনছি... কথা বলুন (Tap Orb to Send)" to "Listening... Speak in Bangla or English"
                    AssistantVoiceState.THINKING -> "চিন্তা করছি ও উত্তর তৈরি করছি..." to "Thinking & Executing task..."
                    AssistantVoiceState.SPEAKING -> "উত্তর দিচ্ছি... (Tap Orb to Interrupt)" to "Speaking response aloud..."
                }

                Text(
                    text = stateTitleBangla,
                    fontSize = 18.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stateTitleEn,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Real-time transcribed text & AI voice response card
                AnimatedVisibility(
                    visible = showCaptions,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF161923),
                        border = BorderStroke(
                            1.dp,
                            when {
                                directAiResponseText.isNotBlank() -> Color(0xFFF59E0B).copy(alpha = 0.55f)
                                displayedSpokenText.isNotBlank() -> Color(0xFF38BDF8).copy(alpha = 0.45f)
                                else -> Color(0xFF262C3D)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 76.dp, max = 150.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (displayedSpokenText.isNotBlank()) {
                                Text(
                                    text = "🎤 “$displayedSpokenText”",
                                    fontSize = 14.sp,
                                    color = Color(0xFFE2E8F0),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (directAiResponseText.isNotBlank()) {
                                if (displayedSpokenText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Text(
                                    text = "🔊 $directAiResponseText",
                                    fontSize = 13.5.sp,
                                    color = Color(0xFFFDE68A),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            if (displayedSpokenText.isBlank() && directAiResponseText.isBlank()) {
                                Text(
                                    text = "মুখে যে কোন প্রশ্ন করুন বা নিচের যেকোনো ভয়েস কমান্ডে ট্যাপ করুন",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Tap Voice Prompts (for noisy environments or instant testing)
                val quickVoicePrompts = remember {
                    listOf(
                        "কেমন আছো? তোমার কাজ কী?",
                        "এই প্রজেক্টের ফাইলগুলো চেক করো",
                        "কোড অপ্টিমাইজ এবং বাগ ফিক্স করো",
                        "Explain this workspace architecture"
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
                            onClick = {
                                displayedSpokenText = preset
                                directAiResponseText = ""
                                stopListening()
                                ChatTtsHelper.stop()
                                voiceState = AssistantVoiceState.THINKING
                                onSendMessage(preset)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1A1F2E),
                            border = BorderStroke(1.dp, Color(0xFF2D3548))
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.5.sp,
                                color = Color(0xFFCBD5E1),
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.10f))

                // Bottom Conversational Action Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute / Unmute Button
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
                        color = if (isMuted) Color(0xFFDC2626).copy(alpha = 0.2f) else Color(0xFF1E2230),
                        border = BorderStroke(1.dp, if (isMuted) Color(0xFFDC2626) else Color(0xFF2D3348)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = if (isMuted) Color(0xFFEF4444) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Send Immediately Button (if words captured)
                    if (displayedSpokenText.isNotBlank() && voiceState == AssistantVoiceState.LISTENING) {
                        Surface(
                            onClick = {
                                val textToSend = displayedSpokenText.trim()
                                stopListening()
                                voiceState = AssistantVoiceState.THINKING
                                onSendMessage(textToSend)
                            },
                            shape = CircleShape,
                            color = GeminiBlue,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Send voice query now",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    } else if (voiceState == AssistantVoiceState.SPEAKING || voiceState == AssistantVoiceState.THINKING) {
                        // Interrupt / Stop Speaking or Thinking Button
                        Surface(
                            onClick = {
                                fallbackVoiceJob?.cancel()
                                ChatTtsHelper.stop()
                                voiceState = AssistantVoiceState.LISTENING
                                startListening()
                            },
                            shape = CircleShape,
                            color = ClaudeTerracotta,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Interrupt assistant speech",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // End Session Button
                    Surface(
                        onClick = {
                            stopListening()
                            ChatTtsHelper.stop()
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = Color(0xFF2D1B22),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "End conversation",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
