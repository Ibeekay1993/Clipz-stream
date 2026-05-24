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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clip
import com.example.ui.components.VideoPlayerSimulator
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoClipperViewModel
import kotlinx.coroutines.delay

@Composable
fun CompletedScreen(
    viewModel: VideoClipperViewModel
) {
    val exportedClips by viewModel.exportedClips.collectAsState()
    val projects by viewModel.projects.collectAsState()
    var activePlaybackClip by remember { mutableStateOf<Clip?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column(modifier = Modifier.padding(top = 24.dp)) {
                Text(
                    text = "Exported Clips Studio",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Access all your finished vertical viral clips. Baked, compressed, and ready for TikTok, Shorts, and Reels distribution.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }

            if (exportedClips.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "No clips",
                            tint = TextMuted,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Studio Gallery is Empty",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Exported clips will appear here permanently. Choose a video in the Editor and click 'Lock & Bake' to start export.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                // List of baked files
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(exportedClips) { clip ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x0F8D8FA6), RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Mini visual clip thumbnail representation with Aspect mark
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(PrimaryNeon, Color(0xFF03221C))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.VerticalShades,
                                            contentDescription = "Vertical Video",
                                            tint = Color.Black,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = clip.aspectRatio,
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = clip.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Duration: ${clip.endSec - clip.startSec} seconds (${clip.startSec}s - ${clip.clipStyleLabel()})",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = clip.exportedFilePath ?: "sdcard/Movies/clip.mp4",
                                        fontSize = 9.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = PrimaryNeon,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Playback preview trigger
                                    IconButton(
                                        onClick = { activePlaybackClip = clip },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(ContainerGrey)
                                            .testTag("preview_finished_clip_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = "Play back completed clip",
                                            tint = PrimaryNeon,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Delete key
                                    IconButton(
                                        onClick = { viewModel.deleteClipById(clip.id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = RatingLow,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // BACK PLAY PREVIEW LIGHTBOX OVERLAY
        AnimatedVisibility(
            visible = activePlaybackClip != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val playClip = activePlaybackClip
            if (playClip != null) {
                var localPlayPos by remember { mutableStateOf(playClip.startSec * 1000L) }
                var isLocalPlaying by remember { mutableStateOf(true) }

                LaunchedEffect(isLocalPlaying) {
                    while (isLocalPlaying) {
                        delay(60)
                        val endL = playClip.endSec * 1000L
                        val next = localPlayPos + 60
                        if (next >= endL) {
                            localPlayPos = playClip.startSec * 1000L
                        } else {
                            localPlayPos = next
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title bar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "STUDIO PLAYER",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNeon,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = playClip.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { 
                                    isLocalPlaying = false
                                    activePlaybackClip = null 
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(ContainerGrey)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        // Player viewport
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            val captionsWord = viewModel.getParsedCaptionsForClip(playClip)
                            val parentProject = projects.find { it.id == playClip.projectId }
                            val videoUriVal = parentProject?.sourceUrl ?: ""
                            
                            // Reuses our highly precise component
                            VideoPlayerSimulator(
                                title = playClip.title,
                                thumbnailType = "ai",
                                isPlaying = isLocalPlaying,
                                currentPositionMs = localPlayPos,
                                aspectRatio = playClip.aspectRatio,
                                captionStyle = playClip.captionStyle,
                                panOffset = playClip.panOffset,
                                captions = captionsWord,
                                videoUri = playClip.exportedFilePath ?: videoUriVal,
                                onPanOffsetChanged = {}
                            )
                        }

                        // Controls bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isLocalPlaying = !isLocalPlaying },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryNeon)
                            ) {
                                Icon(
                                    imageVector = if (isLocalPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Trigger",
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper expansion function for labeling styles beautifully
fun Clip.clipStyleLabel(): String {
    return "Style: $captionStyle, Crop: $aspectRatio, Pan: ${(panOffset * 100).toInt()}%"
}
