package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clip
import com.example.ui.components.VideoPlayerSimulator
import com.example.ui.theme.*
import com.example.ui.viewmodel.ExportState
import com.example.ui.viewmodel.VideoClipperViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    viewModel: VideoClipperViewModel,
    onNavigateToHistory: () -> Unit
) {
    val project by viewModel.selectedProject.collectAsState()
    val clips by viewModel.projectClips.collectAsState()
    val selectedClip by viewModel.selectedClip.collectAsState()

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosMs by viewModel.currentPositionMs.collectAsState()

    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val captionStyle by viewModel.captionStyle.collectAsState()
    val panOffset by viewModel.panOffset.collectAsState()

    val trimStart by viewModel.trimStartSec.collectAsState()
    val trimEnd by viewModel.trimEndSec.collectAsState()

    val exportState by viewModel.exportState.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = AI Clips, 1 = Adjust Frame/Trimming

    Box(modifier = Modifier.fillMaxSize()) {
        if (project == null) {
            // Empty state view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideoCall,
                    contentDescription = "No project",
                    tint = TextMuted,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Active Video Session",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Visit the Import Tab first, or load one of our professional speech preset galleries to populate editing clips.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        } else {
            // Main workspace layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
            ) {
                // Video simulator panel (Fixed upper section)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    VideoPlayerSimulator(
                        title = project!!.title,
                        thumbnailType = project!!.thumbnailUrl,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPosMs,
                        aspectRatio = aspectRatio,
                        captionStyle = captionStyle,
                        panOffset = panOffset,
                        captions = viewModel.getParsedCaptionsForClip(selectedClip),
                        videoUri = project!!.sourceUrl,
                        onPanOffsetChanged = { viewModel.updatePanOffset(it) }
                    )
                }

                // Interactive Audio player timeline scrubber
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val clipDurationMs = (trimEnd - trimStart) * 1000L
                    val relativeProgress = if (clipDurationMs > 0) {
                        ((currentPosMs - (trimStart * 1000L)).toFloat() / clipDurationMs).coerceIn(0f, 1f)
                    } else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play Pause Trigger
                        IconButton(
                            onClick = { viewModel.isPlaying.value = !isPlaying },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryNeon)
                                .testTag("play_pause_video_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play Trigger",
                                tint = Color.Black
                            )
                        }

                        // Display formatted time coordinates
                        Text(
                            text = String.format("%02d:%02d", (currentPosMs / 1000) / 60, (currentPosMs / 1000) % 60) + 
                                   " / " + 
                                   String.format("%02d:%02d", trimEnd / 60, trimEnd % 60),
                            fontSize = 12.sp,
                            color = PrimaryNeon,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Timeline Slider track representation
                    Slider(
                        value = relativeProgress,
                        onValueChange = { newVal ->
                            val seekTarget = (trimStart * 1000L) + (newVal * clipDurationMs).toLong()
                            viewModel.currentPositionMs.value = seekTarget
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryNeon,
                            activeTrackColor = PrimaryNeon,
                            inactiveTrackColor = ContainerGrey
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Workspace Control Panels & Trim List Scrolling (Lower Section)
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.3f)
                        .border(1.dp, Color(0x0F8D8FA6), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sliding Tabs Header
                        TabRow(
                            selectedTabIndex = activeTab,
                            containerColor = Color.Transparent,
                            contentColor = PrimaryNeon,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                    color = PrimaryNeon
                                )
                            }
                        ) {
                            Tab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI", modifier = Modifier.size(16.dp))
                                        Text("AI Clips", fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                            Tab(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = "Manual", modifier = Modifier.size(16.dp))
                                        Text("Manual Trim", fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }

                        // Tab Contents page switcher
                        when (activeTab) {
                            0 -> {
                                // List of AI Identified Clips (just like Opus/Nexus highlights list)
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(clips) { clip ->
                                        val isSelected = selectedClip?.id == clip.id
                                        
                                        // Colors based on virality severity score
                                        val ratingColor = when {
                                            clip.viralScore >= 95 -> RatingHigh
                                            clip.viralScore >= 80 -> RatingMedi
                                            else -> RatingLow
                                        }

                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) ContainerGrey else DarkBg
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.selectClip(clip) }
                                                .border(
                                                    1.dp,
                                                    if (isSelected) PrimaryNeon else Color.Transparent,
                                                    RoundedCornerShape(12.dp)
                                                )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        // Score circle badge
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .drawBehind {
                                                                    drawCircle(
                                                                        color = ratingColor,
                                                                        style = Stroke(width = 2.dp.toPx())
                                                                    )
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "${clip.viralScore}",
                                                                fontSize = 9.sp,
                                                                color = ratingColor,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        Text(
                                                            text = clip.title,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                    }

                                                    // Duration label
                                                    Text(
                                                        text = "${clip.endSec - clip.startSec}s",
                                                        fontSize = 11.sp,
                                                        color = TextMuted,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = clip.viralReason,
                                                    fontSize = 11.sp,
                                                    color = TextMuted,
                                                    lineHeight = 14.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                // Manual Tweaks, Subtitle selections, aspect trims
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    // 1. Aspect Ratio selector row
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("OUTPUT FRAME ASPECT RATIO", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            listOf<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>("9:16" to Icons.Default.CropPortrait, "1:1" to Icons.Default.CropSquare, "16:9" to Icons.Default.CropLandscape).forEach { pair ->
                                                val ratio = pair.first
                                                val icon = pair.second
                                                val active = aspectRatio == ratio
                                                Button(
                                                    onClick = { viewModel.updateAspectRatio(ratio) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (active) PrimaryNeon else ContainerGrey,
                                                        contentColor = if (active) Color.Black else Color.White
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(icon, contentDescription = ratio, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(ratio, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // 2. Subtitle styles selector row
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("SUBTITLES VISUAL THEME", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            listOf("Kinetic Yellow", "Cyber Glow", "Minimal Bold").forEach { style ->
                                                val active = captionStyle == style
                                                Button(
                                                    onClick = { viewModel.updateCaptionStyle(style) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (active) PrimaryNeon else ContainerGrey,
                                                        contentColor = if (active) Color.Black else Color.White
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                                ) {
                                                    Text(style, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // 3. Manual Scrubber slider numeric inputs
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("TIMELINE CLIP BOUNDS", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Start Sec Scrubber
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Start Second", fontSize = 10.sp, color = TextMuted)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { viewModel.updateTrimRange(trimStart - 1, trimEnd) }) {
                                                        Icon(Icons.Default.Remove, "minus", tint = PrimaryNeon)
                                                    }
                                                    Text(
                                                        "$trimStart s",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    IconButton(onClick = { viewModel.updateTrimRange(trimStart + 1, trimEnd) }) {
                                                        Icon(Icons.Default.Add, "add", tint = PrimaryNeon)
                                                    }
                                                }
                                            }

                                            // End Sec Scrubber
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("End Second", fontSize = 10.sp, color = TextMuted)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { viewModel.updateTrimRange(trimStart, trimEnd - 1) }) {
                                                        Icon(Icons.Default.Remove, "minus", tint = PrimaryNeon)
                                                    }
                                                    Text(
                                                        "$trimEnd s",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    IconButton(onClick = { viewModel.updateTrimRange(trimStart, trimEnd + 1) }) {
                                                        Icon(Icons.Default.Add, "add", tint = PrimaryNeon)
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

            // EXPORT SUBMIT FLOATING TRIGGER BUTTON (visible when project loaded)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.exportCurrentClip() },
                    text = { Text("Export Clip", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Publish, contentDescription = "Export") },
                    containerColor = PrimaryNeon,
                    contentColor = Color.Black,
                    modifier = Modifier
                        .padding(bottom = 76.dp)
                        .testTag("editor_export_floating_button")
                )
            }
        }

        // EXPORT RENDERING CINEMATIC OVERLAY (visible when baking)
        AnimatedVisibility(
            visible = exportState is ExportState.Exporting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val state = exportState as? ExportState.Exporting
            val progress = state?.progress ?: 0
            val stepText = state?.currentStep ?: ""

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE0050508))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .border(1.dp, Color(0x2E8D8FA6), RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryNeon,
                            modifier = Modifier.size(56.dp)
                        )

                        Text(
                            text = "RENDERING VERTICAL HIGH IMPACT CLIP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = PrimaryNeon,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "$progress%",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = stepText,
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        LinearProgressIndicator(
                            progress = progress / 100f,
                            color = PrimaryNeon,
                            trackColor = ContainerGrey,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                    }
                }
            }
        }

        // COMPLETED EXPORT DIALOG POPUP
        if (exportState is ExportState.Completed) {
            val completed = exportState as ExportState.Completed
            AlertDialog(
                onDismissRequest = { viewModel.resetExportState() },
                containerColor = SurfaceSlate,
                icon = { Icon(Icons.Default.CloudDone, contentDescription = "Success", tint = PrimaryNeon, modifier = Modifier.size(40.dp)) },
                title = { Text("Export Completed!", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = completed.clipTitle,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your vertical 9:16 portrait clip has been baked successfully with captions and saved to movies! You can view it in the History tab.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetExportState()
                            onNavigateToHistory()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon, contentColor = Color.Black)
                    ) {
                        Text("Open History", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.resetExportState() }) {
                        Text("Edit More", color = Color.White)
                    }
                }
            )
        }
    }
}
