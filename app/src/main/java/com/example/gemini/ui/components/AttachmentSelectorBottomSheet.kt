package com.example.gemini.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.GeminiBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSelectorBottomSheet(
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onPickVideo: () -> Unit = {},
    onCameraCapture: () -> Unit = {},
    onOpenGoogleDrive: () -> Unit = {},
    onOpenNotebookLm: () -> Unit = {},
    onCreatePhoto: () -> Unit = {},
    onCreateVideo: () -> Unit = {},
    onCreateMusic: () -> Unit = {},
    onOpenCanvas: () -> Unit = {},
    onOpenWorkspaceBrowser: () -> Unit = {},
    onOpenAutomation: () -> Unit = {},
    onDeepResearch: () -> Unit = {},
    onLearningMode: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Add & Create with JIN",
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ClaudeTerracotta.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "AI SUITE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClaudeTerracotta,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 1: Capture & Device Media
            Text(
                text = "MEDIA & CLOUD",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CompactFeatureTile(
                        icon = Icons.Outlined.PhotoCamera,
                        iconColor = Color(0xFFEF4444),
                        title = "Camera",
                        onClick = { onDismiss(); onCameraCapture() }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompactFeatureTile(
                        icon = Icons.Outlined.Image,
                        iconColor = ClaudeTerracotta,
                        title = "Photos",
                        onClick = { onDismiss(); onPickImage() }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompactFeatureTile(
                        icon = Icons.Outlined.Folder,
                        iconColor = Color(0xFF3B82F6),
                        title = "Files",
                        onClick = { onDismiss(); onPickFile() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CompactFeatureTile(
                        icon = Icons.Outlined.VideoLibrary,
                        iconColor = Color(0xFF8B5CF6),
                        title = "Videos",
                        onClick = { onDismiss(); onPickVideo() }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompactFeatureTile(
                        icon = Icons.Outlined.CloudUpload,
                        iconColor = Color(0xFF10B981),
                        title = "Google Drive",
                        onClick = { onDismiss(); onOpenGoogleDrive() }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompactFeatureTile(
                        icon = Icons.Outlined.AutoStories,
                        iconColor = Color(0xFFF59E0B),
                        title = "Notebooks",
                        onClick = { onDismiss(); onOpenNotebookLm() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Generative Creations
            Text(
                text = "GENERATE & CREATE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.Brush,
                iconColor = Color(0xFFEC4899),
                title = "Create Photo (AI Image)",
                subtitle = "Generate ultra-realistic images & art with Imagen 3 / FLUX",
                onClick = { onDismiss(); onCreatePhoto() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.Videocam,
                iconColor = Color(0xFFF59E0B),
                title = "Create Video",
                subtitle = "Generate cinematic AI video clips & scene animations",
                onClick = { onDismiss(); onCreateVideo() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.MusicNote,
                iconColor = Color(0xFF06B6D4),
                title = "Create Music",
                subtitle = "Compose melodic soundtracks, beats, and background music",
                onClick = { onDismiss(); onCreateMusic() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Reasoning, Workspaces & Automation
            Text(
                text = "REASONING, WORKSPACES & AUTOMATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.SmartToy,
                iconColor = Color(0xFF10B981),
                title = "AI Automation Controls",
                subtitle = "Automate browser actions, shell commands, and background tasks",
                onClick = { onDismiss(); onOpenAutomation() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.FolderCopy,
                iconColor = Color(0xFF0EA5E9),
                title = "Workspace Files Browser",
                subtitle = "Explore, select, and manage local project files and code directories",
                onClick = { onDismiss(); onOpenWorkspaceBrowser() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.DashboardCustomize,
                iconColor = GeminiBlue,
                title = "Canvas Workspace",
                subtitle = "Interactive side-by-side code, markdown & artifact editor",
                onClick = { onDismiss(); onOpenCanvas() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.TravelExplore,
                iconColor = Color(0xFF6366F1),
                title = "Deep Research",
                subtitle = "Multi-step autonomous web synthesis & technical investigations",
                onClick = { onDismiss(); onDeepResearch() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AttachmentOptionRow(
                icon = Icons.Outlined.School,
                iconColor = Color(0xFF14B8A6),
                title = "Learning & Tutor Mode",
                subtitle = "Step-by-step interactive concepts, code drills, and quizzes",
                onClick = { onDismiss(); onLearningMode() }
            )
        }
    }
}

@Composable
private fun CompactFeatureTile(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
