package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Clip
import com.example.data.model.Project
import com.example.ui.components.VideoPlayerSimulator
import com.example.ui.theme.*
import com.example.ui.viewmodel.ExportState
import com.example.ui.viewmodel.VideoClipperViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val searchQuery by viewModel.searchQuery.collectAsState()

    val projects by viewModel.projects.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = AI Clips, 1 = Transcript, 2 = Crop & Adjust
    var localSearchText by remember { mutableStateOf(searchQuery) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (project == null) {
            // Landing Workspace: Lists all imported Active Video Projects
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column {
                    Text(
                        text = "Active Video Projects",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select an existing video session to browse AI-detected moments, search captions, and generate viral shorts.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                if (projects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(SurfaceSlate)
                            .border(1.dp, Color(0x1F8D8FA6), RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoCall,
                                contentDescription = "No session",
                                tint = TextMuted,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Video Project Imported",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Head over to the Import Tab at the bottom, paste a video web relation, or load a professional preset to begin.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(projects) { p ->
                            Card(
                                onClick = { viewModel.selectProject(p) },
                                colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0x0F8D8FA6), RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(ContainerGrey, PrimaryNeon.copy(alpha = 0.4f))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MovieFilter,
                                            contentDescription = "Project icon",
                                            tint = PrimaryNeon,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = p.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Duration: ${p.durationSeconds}s",
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteProject(p) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = "Delete",
                                            tint = RatingLow
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Open",
                                        tint = PrimaryNeon
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedClip == null) {
            // WAYINVIDEO DASHBOARD VIEW: Display Grid list of identified clip moments for selected project
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
                    .padding(horizontal = 16.dp)
            ) {
                // Header with Project Details & Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.selectProject(null) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ContainerGrey)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = project!!.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Browse moments to edit",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar: Describe what you want to find in this video
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSlate)
                        .border(1.dp, PrimaryNeon.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    OutlinedTextField(
                        value = localSearchText,
                        onValueChange = {
                            localSearchText = it
                            viewModel.searchQuery.value = it
                        },
                        placeholder = {
                            Text(
                                text = "Describe what you want to find (e.g. \"AI coding\")",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("moments_search_input"),
                        singleLine = true
                    )
                    Button(
                        onClick = { viewModel.searchQuery.value = localSearchText },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Find", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Wayin Tab selectors: Viral Moments
                val filteredClips = remember(clips, searchQuery) {
                    if (searchQuery.isBlank()) {
                        clips
                    } else {
                        clips.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.viralReason.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(ContainerGrey)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Viral Moments",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(PrimaryNeon)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${filteredClips.size}",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ContainerGrey)
                    ) {
                        Icon(Icons.Default.Add, "Add mom", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bulk edit / tools action bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Select all",
                            tint = PrimaryNeon,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Select All", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ContainerGrey)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("NEW", fontSize = 7.sp, color = PrimaryNeon, fontWeight = FontWeight.Bold)
                        }
                    }

                    listOf(
                        "Bulk Edit" to Icons.Default.Edit,
                        "Bulk Schedule" to Icons.Default.Schedule,
                        "Bulk Export" to Icons.Default.Download
                    ).forEach { pair ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ContainerGrey)
                                .clickable {}
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = pair.second,
                                    contentDescription = pair.first,
                                    tint = TextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(pair.first, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Beautiful adaptive Grid representing Wayin Video card moments deck
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredClips) { clip ->
                        val duration = clip.endSec - clip.startSec
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x1F8D8FA6), RoundedCornerShape(16.dp))
                        ) {
                            Column {
                                // Card Top Title Row with Share Icon
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = clip.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.IosShare,
                                        contentDescription = "Share",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Interactive Video Simulator Thumbnail with Crop Lines overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color.Black)
                                        .clickable { viewModel.selectClip(clip) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Custom visual pattern simulating video context
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .drawBehind {
                                                // Outer 16:9 widescreen frame
                                                // Draw central 9:16 cropping portrait guidelines
                                                val w = this.size.width
                                                val h = this.size.height
                                                val portW = h * (9f / 16f)
                                                val startX = (w - portW) / 2
                                                
                                                // Left shaded block
                                                drawRect(
                                                    color = Color.Black.copy(alpha = 0.6f),
                                                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                                                    size = androidx.compose.ui.geometry.Size(startX, h)
                                                )
                                                // Right shaded block
                                                drawRect(
                                                    color = Color.Black.copy(alpha = 0.6f),
                                                    topLeft = androidx.compose.ui.geometry.Offset(startX + portW, 0f),
                                                    size = androidx.compose.ui.geometry.Size(w - (startX + portW), h)
                                                )
                                                // Crop border line
                                                drawRect(
                                                    color = PrimaryNeon.copy(alpha = 0.5f),
                                                    topLeft = androidx.compose.ui.geometry.Offset(startX, 0f),
                                                    size = androidx.compose.ui.geometry.Size(portW, h),
                                                    style = Stroke(width = 2.dp.toPx())
                                                )
                                            }
                                    )

                                    // Time stamp badge top-right
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = String.format("%02d:%02d - %02d:%02d", clip.startSec/60, clip.startSec%60, clip.endSec/60, clip.endSec%60),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Dynamic Play overlay circle icon
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .border(2.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play icon overlay",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // Details block below thumbnail
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Score block e.g. "98 /100" in green
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${clip.viralScore}",
                                                color = PrimaryNeon,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = "/100",
                                                color = TextMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }

                                        // Tool icons row
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 1. Publish Shortcut
                                            IconButton(
                                                onClick = {},
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(ContainerGrey)
                                            ) {
                                                Icon(Icons.Default.SendToMobile, "Publish", tint = Color.White, modifier = Modifier.size(13.dp))
                                            }
                                            // 2. Crop/Scissors (Sets selectedClip to editing mode)
                                            IconButton(
                                                onClick = { viewModel.selectClip(clip) },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(ContainerGrey)
                                            ) {
                                                Icon(Icons.Default.ContentCut, "Crop settings", tint = PrimaryNeon, modifier = Modifier.size(13.dp))
                                            }
                                            // 3. Export Download Shortcut
                                            IconButton(
                                                onClick = {
                                                    viewModel.selectClip(clip)
                                                    viewModel.exportCurrentClip()
                                                },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(ContainerGrey)
                                            ) {
                                                Icon(Icons.Default.Download, "Download", tint = Color.White, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Clip description
                                    Text(
                                        text = clip.viralReason,
                                        color = TextWhite,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // INTERACTIVE VIDEO EDITOR / TRIMMER VIEW: Playback simulator & manual adjusts when clip selected
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
            ) {
                // Return back header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSlate)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.deselectClip() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Dashboard", tint = PrimaryNeon, modifier = Modifier.size(16.dp))
                            Text("Dashboard", color = PrimaryNeon, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedClip!!.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Fine-tune and render crop ratios",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                // Video simulator layout
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
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
                        onPanOffsetChanged = { viewModel.updatePanOffset(it) },
                        onPanOffsetCommit = { viewModel.commitPanOffset(it) }
                    )
                }

                // Play controllers timeline scrubber
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
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

                // Workspace Control Panels card
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
                            listOf(
                                "Other Moments" to Icons.Default.AutoAwesome,
                                "Transcript" to Icons.Default.Receipt,
                                "Crop & Adjust" to Icons.Default.Tune
                            ).forEachIndexed { index, pair ->
                                Tab(
                                    selected = activeTab == index,
                                    onClick = { activeTab = index },
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(pair.second, contentDescription = pair.first, modifier = Modifier.size(12.dp))
                                            Text(pair.first, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        }
                                    }
                                )
                            }
                        }

                        // Tab Contents page switcher
                        when (activeTab) {
                            0 -> {
                                // Other moments Jump layout
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(clips) { clip ->
                                        val isCurrent = selectedClip!!.id == clip.id
                                        
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isCurrent) ContainerGrey else DarkBg
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.selectClip(clip) }
                                                .border(
                                                    1.dp,
                                                    if (isCurrent) PrimaryNeon else Color.Transparent,
                                                    RoundedCornerShape(12.dp)
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .drawBehind {
                                                            drawCircle(
                                                                color = RatingHigh,
                                                                style = Stroke(width = 2.dp.toPx())
                                                            )
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${clip.viralScore}",
                                                        fontSize = 9.sp,
                                                        color = RatingHigh,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = clip.title,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = clip.viralReason,
                                                        fontSize = 10.sp,
                                                        color = TextMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "play",
                                                    tint = PrimaryNeon,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // Dynamic captions word stamps click-to-seek
                                val caps = viewModel.getParsedCaptionsForClip(selectedClip)
                                if (caps.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Receipt, contentDescription = "No captions", tint = TextMuted, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No transcript words mapped. Export generates speech subtitles organically.",
                                            color = TextMuted,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = DarkBg),
                                                modifier = Modifier.weight(1f).border(1.dp, Color(0x158D8FA6), RoundedCornerShape(12.dp))
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("VIRAL INDEX", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                                    Text("${selectedClip!!.viralScore}/100", fontSize = 14.sp, color = PrimaryNeon, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = DarkBg),
                                                modifier = Modifier.weight(1f).border(1.dp, Color(0x158D8FA6), RoundedCornerShape(12.dp))
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("DURATION", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                                    Text("${selectedClip!!.endSec - selectedClip!!.startSec}s", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Text(
                                            text = "TAP WORDS TO SEEK PLAYHEAD INSTANTLY:",
                                            fontSize = 10.sp,
                                            color = PrimaryNeon,
                                            fontWeight = FontWeight.Bold
                                        )

                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            caps.forEach { wordObj ->
                                                val isActive = currentPosMs >= wordObj.startMs && currentPosMs <= wordObj.endMs
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isActive) PrimaryNeon else ContainerGrey)
                                                        .clickable {
                                                            viewModel.currentPositionMs.value = wordObj.startMs
                                                            viewModel.isPlaying.value = false
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = wordObj.word,
                                                        color = if (isActive) Color.Black else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(60.dp))
                                    }
                                }
                            }
                            2 -> {
                                // Manual Tweaks, Subtitle Style selections
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // 1. Aspect ratio
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("OUTPUT FRAME ASPECT RATIO", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>(
                                                "9:16" to Icons.Default.CropPortrait,
                                                "1:1" to Icons.Default.CropSquare,
                                                "16:9" to Icons.Default.CropLandscape
                                            ).forEach { pair ->
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
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(vertical = 4.dp)
                                                ) {
                                                    Icon(icon, contentDescription = ratio, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(ratio, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // 2. Visual subtitle theme
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("SUBTITLES VISUAL THEME", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                                    contentPadding = PaddingValues(vertical = 4.dp)
                                                ) {
                                                    Text(style, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // 3. Timeline crop bounds
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("TIMELINE CLIP BOUNDS", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Start Second", fontSize = 9.sp, color = TextMuted)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { viewModel.updateTrimRange(trimStart - 1, trimEnd) }) {
                                                        Icon(Icons.Default.Remove, "minus", tint = PrimaryNeon, modifier = Modifier.size(18.dp))
                                                    }
                                                    Text(
                                                        text = "$trimStart s",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        modifier = Modifier.weight(1f),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    IconButton(onClick = { viewModel.updateTrimRange(trimStart + 1, trimEnd) }) {
                                                        Icon(Icons.Default.Add, "add", tint = PrimaryNeon, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("End Second", fontSize = 9.sp, color = TextMuted)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { viewModel.updateTrimRange(trimStart, trimEnd - 1) }) {
                                                        Icon(Icons.Default.Remove, "minus", tint = PrimaryNeon, modifier = Modifier.size(18.dp))
                                                    }
                                                    Text(
                                                        text = "$trimEnd s",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        modifier = Modifier.weight(1f),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    IconButton(onClick = { viewModel.updateTrimRange(trimStart, trimEnd + 1) }) {
                                                        Icon(Icons.Default.Add, "add", tint = PrimaryNeon, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        }
                    }
                }

                // Render Trigger
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
        }

        // Standard Rendering progress overlay
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
                            fontSize = 11.sp,
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
                            progress = { progress / 100f },
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

        // Completed export dialog popup
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
                            text = "Your vertical 9:16 portrait clip has been exported successfully with captions and saved to movies! You can view it in the History tab.",
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
