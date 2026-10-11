package com.example.gemini.ui.notebook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemini.data.notebook.NotebookItem
import com.example.gemini.data.notebook.NotebookManager
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.GeminiBlue
import com.example.gemini.theme.QuotaGreen
import com.example.gemini.ui.components.AppToastHelper
import com.example.gemini.ui.components.ChatToastType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotebookDialog(
    onDismiss: () -> Unit,
    onNotebookAttached: (NotebookItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notebookManager = remember { NotebookManager.getInstance(context) }

    val notebooks by notebookManager.notebooks.collectAsState()
    val attachedNotebook by notebookManager.attachedNotebook.collectAsState()

    var isCreatingOrEditing by remember { mutableStateOf(false) }
    var editingNotebook by remember { mutableStateOf<NotebookItem?>(null) }

    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var editSourceUrl by remember { mutableStateOf("") }
    var editTags by remember { mutableStateOf("") }

    fun startCreate() {
        editingNotebook = null
        editTitle = ""
        editContent = ""
        editSourceUrl = ""
        editTags = ""
        isCreatingOrEditing = true
    }

    fun startEdit(item: NotebookItem) {
        editingNotebook = item
        editTitle = item.title
        editContent = item.content
        editSourceUrl = item.sourceUrl
        editTags = item.tags.joinToString(", ")
        isCreatingOrEditing = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GeminiBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint = GeminiBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCreatingOrEditing) {
                                if (editingNotebook != null) "Edit Notebook" else "New Notebook"
                            } else "Notebooks & Sources",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isCreatingOrEditing) "Save knowledge & notes for agent context" else "Add notebook sources directly to chat (Gemini style)",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isCreatingOrEditing) {
                    // Editor Form
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Notebook Title", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Backend API Architecture", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editSourceUrl,
                            onValueChange = { editSourceUrl = it },
                            label = { Text("Source Link / URL (Optional)", fontSize = 12.sp) },
                            placeholder = { Text("https://example.com or local file", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editTags,
                            onValueChange = { editTags = it },
                            label = { Text("Tags (comma separated)", fontSize = 12.sp) },
                            placeholder = { Text("python, architecture, notes", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editContent,
                            onValueChange = { editContent = it },
                            label = { Text("Notebook Notes & Knowledge Content", fontSize = 12.sp) },
                            placeholder = { Text("Paste code snippets, project notes, guidelines, or research here...", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isCreatingOrEditing = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val title = editTitle.trim().ifBlank { "Untitled Notebook" }
                                    val tags = editTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    val item = editingNotebook?.copy(
                                        title = title,
                                        content = editContent,
                                        sourceUrl = editSourceUrl.trim(),
                                        tags = tags,
                                        updatedAt = System.currentTimeMillis()
                                    ) ?: NotebookItem(
                                        title = title,
                                        content = editContent,
                                        sourceUrl = editSourceUrl.trim(),
                                        tags = tags
                                    )
                                    scope.launch {
                                        notebookManager.saveNotebook(item)
                                        isCreatingOrEditing = false
                                        AppToastHelper.showToast("Saved Notebook: $title", ChatToastType.SUCCESS)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue)
                            ) {
                                Text("Save Notebook", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    // Notebooks List
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SAVED NOTEBOOKS (${notebooks.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        TextButton(
                            onClick = { startCreate() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp), tint = GeminiBlue)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Notebook", fontSize = 12.sp, color = GeminiBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (notebooks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No notebooks added yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(notebooks, key = { it.id }) { notebook ->
                                val isAttached = attachedNotebook?.id == notebook.id
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isAttached) GeminiBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isAttached) GeminiBlue.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Book,
                                                contentDescription = null,
                                                tint = if (isAttached) GeminiBlue else ClaudeTerracotta,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = notebook.title,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isAttached) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(QuotaGreen.copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        "ATTACHED",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = QuotaGreen
                                                    )
                                                }
                                            }
                                        }

                                        if (notebook.content.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = notebook.content.take(120).replace("\n", " "),
                                                fontSize = 11.5.sp,
                                                lineHeight = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Actions: Attach, Edit, Delete
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val timeStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(notebook.updatedAt))
                                            Text(
                                                text = timeStr,
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                IconButton(
                                                    onClick = { startEdit(notebook) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Edit, "Edit", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            notebookManager.deleteNotebook(notebook.id)
                                                            AppToastHelper.showToast("Deleted notebook", ChatToastType.INFO)
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Delete, "Delete", modifier = Modifier.size(14.dp), tint = Color(0xFFEF4444))
                                                }

                                                if (isAttached) {
                                                    Button(
                                                        onClick = {
                                                            notebookManager.detachNotebook()
                                                            AppToastHelper.showToast("Detached notebook from chat", ChatToastType.INFO)
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Text("Detach", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            notebookManager.attachNotebook(notebook)
                                                            onNotebookAttached(notebook)
                                                            onDismiss()
                                                            AppToastHelper.showToast("Attached '${notebook.title}' to chat context!", ChatToastType.SUCCESS)
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(Icons.Outlined.PushPin, null, modifier = Modifier.size(13.dp), tint = Color.White)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Add to Chat", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
        }
    }
}
