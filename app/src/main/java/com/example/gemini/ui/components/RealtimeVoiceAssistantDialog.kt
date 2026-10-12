package com.example.gemini.ui.components

import android.Manifest
import android.content.Context
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
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.gemini.data.audio.ChatTtsHelper
import com.example.gemini.data.audio.NativeSpeechTranscriber
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.GeminiBlue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * Immersive, Hands-Free ChatGPT-Style Real-Time Voice Assistant.
 * Allows natural 2-way vocal conversation and hands-free task execution:
 * 1. Listens continuously to user speech.
 * 2. Transcribes in real-time and detects pauses.
 * 3. Sends prompt to Antigravity / Claude / AI agent.
 * 4. Speaks assistant responses back out loud via [ChatTtsHelper].
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
    var userSpokenText by remember { mutableStateOf("") }
    var displayedSpokenText by remember { mutableStateOf("") }
    var showCaptions by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var lastSpokenMessageId by remember { mutableStateOf("") }
    var silenceJob by remember { mutableStateOf<Job?>(null) }

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

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun startListening() {
        if (!hasAudioPermission || isMuted) return
        voiceState = AssistantVoiceState.LISTENING
        userSpokenText = ""

        speechTranscriber.startTranscriptionSession(
            scope = coroutineScope,
            cascadeId = "realtime_voice",
            preCursorText = "",
            postCursorText = "",
            onPartialText = { partial ->
                userSpokenText = partial
                displayedSpokenText = partial
                silenceJob?.cancel()
                // Restart silence timeout: send automatically after 1.6s of silence
                silenceJob = coroutineScope.launch {
                    delay(1600)
                    if (userSpokenText.isNotBlank() && voiceState == AssistantVoiceState.LISTENING) {
                        val textToSend = userSpokenText.trim()
                        userSpokenText = ""
                        speechTranscriber.stopTranscriptionSession()
                        voiceState = AssistantVoiceState.THINKING
                        onSendMessage(textToSend)
                    }
                }
            },
            onFinalText = { finalText ->
                userSpokenText = finalText
                displayedSpokenText = finalText
                silenceJob?.cancel()
                silenceJob = coroutineScope.launch {
                    delay(800)
                    if (userSpokenText.isNotBlank() && voiceState == AssistantVoiceState.LISTENING) {
                        val textToSend = userSpokenText.trim()
                        userSpokenText = ""
                        speechTranscriber.stopTranscriptionSession()
                        voiceState = AssistantVoiceState.THINKING
                        onSendMessage(textToSend)
                    }
                }
            },
            onError = { err ->
                // Auto-recover or notify quietly
            }
        )
    }

    fun stopListening() {
        silenceJob?.cancel()
        speechTranscriber.stopTranscriptionSession()
    }

    // Initialize listening session on enter
    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            startListening()
        }
    }

    // Transition from THINKING -> SPEAKING when AI completes streaming
    LaunchedEffect(isStreaming, latestAssistantMessage) {
        if (!isStreaming && voiceState == AssistantVoiceState.THINKING) {
            if (!latestAssistantMessage.isNullOrBlank()) {
                val msgHash = latestAssistantMessage.hashCode().toString()
                if (msgHash != lastSpokenMessageId) {
                    lastSpokenMessageId = msgHash
                    voiceState = AssistantVoiceState.SPEAKING
                    ChatTtsHelper.speak(context, msgHash, latestAssistantMessage)
                } else {
                    // Nothing new to speak, loop back to listening
                    voiceState = AssistantVoiceState.LISTENING
                    startListening()
                }
            } else {
                voiceState = AssistantVoiceState.LISTENING
                startListening()
            }
        }
    }

    // Transition from SPEAKING -> LISTENING when TTS finishes speaking
    LaunchedEffect(isTtsSpeaking) {
        if (!isTtsSpeaking && voiceState == AssistantVoiceState.SPEAKING) {
            delay(400) // Brief natural conversational pause
            if (!isMuted) {
                voiceState = AssistantVoiceState.LISTENING
                startListening()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            silenceJob?.cancel()
            speechTranscriber.cancelTranscriptionSession()
            ChatTtsHelper.stop()
        }
    }

    // Orb animation transitions
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceOrbTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
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
                val centerOffset = Offset(size.width / 2f, size.height * 0.42f)
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
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar: Model & Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E2230),
                        border = BorderStroke(1.dp, Color(0xFF2D3348))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = ClaudeTerracotta,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = modelDisplayName,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
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
                        Surface(
                            onClick = { showCaptions = !showCaptions },
                            shape = RoundedCornerShape(16.dp),
                            color = if (showCaptions) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF1E2230),
                            border = BorderStroke(1.dp, if (showCaptions) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0xFF2D3348))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (showCaptions) Color(0xFF38BDF8) else Color(0xFF64748B))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showCaptions) "Captions: ON" else "Captions: OFF",
                                    fontSize = 11.5.sp,
                                    color = if (showCaptions) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            onClick = {
                                stopListening()
                                ChatTtsHelper.stop()
                                onDismiss()
                            },
                            shape = CircleShape,
                            color = Color(0xFF1E2230),
                            border = BorderStroke(1.dp, Color(0xFF2D3348)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close voice mode",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.12f))

                // Center 3D Rotating Galaxy Voice Sphere
                val galaxyStars = remember {
                    val list = ArrayList<GalaxyStar>(140)
                    val random = java.util.Random(1337)
                    // Spherical 3D star cluster
                    for (i in 0 until 85) {
                        val phi = random.nextFloat() * (2f * Math.PI.toFloat())
                        val theta = (random.nextFloat() - 0.5f) * Math.PI.toFloat()
                        val dist = 0.35f + random.nextFloat() * 0.65f
                        val size = 2f + random.nextFloat() * 3f
                        val alpha = 0.45f + random.nextFloat() * 0.55f
                        list.add(GalaxyStar(phi, theta, dist, size, alpha, false))
                    }
                    // Galaxy equatorial accretion ring
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
                    modifier = Modifier.size(260.dp),
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

                        // 1. Draw glowing cosmic nebula in center
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

                        // 2. Project and draw each 3D galaxy star particle
                        val radYaw = Math.toRadians(galaxyYaw.toDouble()).toFloat()
                        val radPitch = Math.toRadians(galaxyPitch.toDouble()).toFloat()
                        val cosPitch = cos(radPitch)
                        val sinPitch = sin(radPitch)

                        for (star in galaxyStars) {
                            val r = baseRadius * star.distRatio
                            val anglePhi = star.phi + radYaw
                            val cosTheta = cos(star.theta)
                            val sinTheta = sin(star.theta)

                            // Spherical 3D coordinates
                            val x = r * cosTheta * cos(anglePhi)
                            val y = r * sinTheta
                            val z = r * cosTheta * sin(anglePhi)

                            // Pitch rotation around X axis
                            val yRot = y * cosPitch - z * sinPitch
                            val zRot = y * sinPitch + z * cosPitch
                            val xRot = x

                            // 3D Perspective projection
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
                            .size(68.dp)
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
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Voice State Title & Guidance
                val (stateTitleBangla, stateTitleEn) = when (voiceState) {
                    AssistantVoiceState.LISTENING -> "শুনছি... কথা বলুন" to "Listening... Speak naturally"
                    AssistantVoiceState.THINKING -> "চিন্তা করছি ও টাস্ক রান করছি..." to "Thinking & Executing task..."
                    AssistantVoiceState.SPEAKING -> "উত্তর দিচ্ছি..." to "Speaking response..."
                }

                Text(
                    text = stateTitleBangla,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stateTitleEn,
                    fontSize = 13.5.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Real-time transcribed text display card with AnimatedVisibility
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
                            if (displayedSpokenText.isNotBlank()) Color(0xFF38BDF8).copy(alpha = 0.45f) else Color(0xFF262C3D)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp, max = 130.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            if (displayedSpokenText.isNotBlank()) {
                                Text(
                                    text = "“$displayedSpokenText”",
                                    fontSize = 14.5.sp,
                                    color = Color(0xFFE2E8F0),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = "মুখে যে কোন প্রশ্ন করুন বা টাস্ক এক্সিকিউট করতে বলুন (যেমন: 'লিস্ট ফাইল', 'ক্রিয়েট রিয়েক্ট অ্যাপ')",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.12f))

                // Bottom Conversational Action Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
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
                                displayedSpokenText = ""
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
                    } else if (voiceState == AssistantVoiceState.SPEAKING) {
                        // Interrupt / Stop Speaking Button
                        Surface(
                            onClick = {
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
