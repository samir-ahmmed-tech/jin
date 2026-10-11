package com.example.gemini.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.translation.SupportedLanguage
import com.example.gemini.data.translation.TranslationService
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.QuotaGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingTranslatorSheet(
    onDismiss: () -> Unit,
    lastAiMessageText: String? = null,
    onSendToChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var sourceLangCode by remember { mutableStateOf("en") }
    var targetLangCode by remember { mutableStateOf("bn") }

    var showSourceLangMenu by remember { mutableStateOf(false) }
    var showTargetLangMenu by remember { mutableStateOf(false) }

    fun doTranslate() {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) return

        isTranslating = true
        errorMessage = null
        coroutineScope.launch {
            val result = TranslationService.translateText(
                text = trimmed,
                targetLang = targetLangCode,
                sourceLang = sourceLangCode
            )
            isTranslating = false
            result.onSuccess { res ->
                translatedText = res
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Translation failed"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ClaudeTerracotta.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = null,
                        tint = ClaudeTerracotta,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "JIN AI Floating Translator",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Universal real-time multilingual translator with Bengali support",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Language Selector Row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Source Lang Box
                    Box {
                        val srcLang = TranslationService.LANGUAGES.find { it.code == sourceLangCode }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                            modifier = Modifier.clickable { showSourceLangMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = srcLang?.name ?: "English",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Outlined.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showSourceLangMenu,
                            onDismissRequest = { showSourceLangMenu = false }
                        ) {
                            TranslationService.LANGUAGES.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text("${lang.name} (${lang.nativeName})", fontSize = 12.sp) },
                                    onClick = {
                                        sourceLangCode = lang.code
                                        showSourceLangMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Swap Button
                    IconButton(
                        onClick = {
                            val temp = sourceLangCode
                            sourceLangCode = targetLangCode
                            targetLangCode = temp
                            if (translatedText.isNotBlank()) {
                                inputText = translatedText
                                translatedText = ""
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = "Swap Languages",
                            tint = ClaudeTerracotta,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Target Lang Box
                    Box {
                        val tgtLang = TranslationService.LANGUAGES.find { it.code == targetLangCode }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ClaudeTerracotta.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ClaudeTerracotta.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { showTargetLangMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${tgtLang?.name ?: "Bengali"} (${tgtLang?.nativeName ?: "বাংলা"})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ClaudeTerracotta
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Outlined.ArrowDropDown, null, tint = ClaudeTerracotta, modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showTargetLangMenu,
                            onDismissRequest = { showTargetLangMenu = false }
                        ) {
                            TranslationService.LANGUAGES.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text("${lang.name} (${lang.nativeName})", fontSize = 12.sp) },
                                    onClick = {
                                        targetLangCode = lang.code
                                        showTargetLangMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Paste Clipboard
                AssistChip(
                    onClick = {
                        val clip = clipboardManager.getText()?.text.orEmpty()
                        if (clip.isNotBlank()) {
                            inputText = clip
                        }
                    },
                    label = { Text("📋 Paste", fontSize = 11.sp) },
                    leadingIcon = null,
                    modifier = Modifier.height(30.dp)
                )

                // Translate Last AI Message
                if (!lastAiMessageText.isNullOrBlank()) {
                    AssistChip(
                        onClick = {
                            inputText = lastAiMessageText
                            doTranslate()
                        },
                        label = { Text("💬 Last Reply", fontSize = 11.sp) },
                        leadingIcon = null,
                        modifier = Modifier.height(30.dp)
                    )
                }

                // Clear
                if (inputText.isNotBlank() || translatedText.isNotBlank()) {
                    AssistChip(
                        onClick = {
                            inputText = ""
                            translatedText = ""
                            errorMessage = null
                        },
                        label = { Text("🧹 Clear", fontSize = 11.sp) },
                        leadingIcon = null,
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Input Text Field
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Paste or type English text, code explanations, or questions here...",
                        fontSize = 12.5.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 160.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClaudeTerracotta,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Translate Action Button
            Button(
                onClick = { doTranslate() },
                enabled = inputText.isNotBlank() && !isTranslating,
                colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Translating with AI...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Outlined.Translate, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Translate to বাংলা / Target", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.5.sp
                )
            }

            // Translated Result Box
            if (translatedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, QuotaGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "অনুবাদ (Translated):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = QuotaGreen
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(translatedText))
                                        AppToastHelper.showToast("অনুবাদ কপি করা হয়েছে!", ChatToastType.SUCCESS)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (onSendToChat != null) {
                                    IconButton(
                                        onClick = {
                                            onSendToChat(translatedText)
                                            onDismiss()
                                            AppToastHelper.showToast("চ্যাটে যুক্ত করা হয়েছে!", ChatToastType.SUCCESS)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Send,
                                            contentDescription = "Send to Chat",
                                            tint = ClaudeTerracotta,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        SelectionContainer {
                            Text(
                                text = translatedText,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
