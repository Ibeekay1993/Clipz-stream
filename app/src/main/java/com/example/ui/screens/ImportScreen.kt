package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.SampleVideos
import com.example.ui.theme.*
import com.example.ui.viewmodel.LoadingState
import com.example.ui.viewmodel.VideoClipperViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns

fun getFileName(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Imported Video File"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: VideoClipperViewModel,
    onNavigateToEditor: () -> Unit
) {
    val loadingState by viewModel.loadingState.collectAsState()
    val context = LocalContext.current

    var inputUrl by remember { mutableStateOf("") }
    var inputTitle by remember { mutableStateOf("") }
    var inputDesc by remember { mutableStateOf("") }

    var expandedAdvanced by remember { mutableStateOf(false) }

    var selectedImportTab by remember { mutableStateOf(0) } // 0 = Web Link, 1 = Upload File
    
    var localVideoUri by remember { mutableStateOf<Uri?>(null) }
    var localVideoName by remember { mutableStateOf("") }
    var localVideoDuration by remember { mutableStateOf(0L) }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            localVideoUri = uri
            localVideoName = getFileName(context, uri)
            
            val retriever = MediaMetadataRetriever()
            localVideoDuration = try {
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durMs = durationStr?.toLong() ?: 60000L
                durMs / 1000L
            } catch (e: Exception) {
                120L // fallback
            } finally {
                retriever.release()
            }
            
            if (inputTitle.isEmpty()) {
                inputTitle = localVideoName.substringBeforeLast(".")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            // Header Banner
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Import Media Source",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Paste a long video web-link or tap one of our pre-configured studio podcasts to generate viral clips instantly.",
                        fontSize = 13.sp,
                        color = TextMuted,
                        lineHeight = 18.sp
                    )
                }
            }

            // Input panel
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Visual Segment Tabs Toggle selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ContainerGrey)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Web Link" to Icons.Default.Link, "Upload File" to Icons.Default.CloudUpload).forEachIndexed { index, pair ->
                            val active = selectedImportTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) SurfaceSlate else Color.Transparent)
                                    .clickable { selectedImportTab = index }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = pair.second,
                                        contentDescription = pair.first,
                                        tint = if (active) PrimaryNeon else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = pair.first,
                                        fontSize = 13.sp,
                                        color = if (active) PrimaryNeon else TextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (selectedImportTab == 0) {
                        // Original Web Link tab
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x2B49454F), RoundedCornerShape(28.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // AI Powered Badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SecondaryNeon)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "AI POWERED",
                                            color = PrimaryNeon,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = "Link", tint = PrimaryNeon)
                                    Text(
                                        text = "Paste your link below",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextWhite
                                    )
                                }

                                // URL Field
                                OutlinedTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it },
                                    placeholder = { Text("https://www.youtube.com/watch?v=...", color = Color.Gray, fontSize = 13.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryNeon,
                                        unfocusedBorderColor = Color(0x4F939099),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("import_video_url_input")
                                )

                                // Toggle manual metadata
                                Row(
                                    modifier = Modifier
                                        .clickable { expandedAdvanced = !expandedAdvanced }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (expandedAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle Settings",
                                        tint = PrimaryNeon,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Advanced Video Specifics (Optional)",
                                        color = PrimaryNeon,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                AnimatedVisibility(visible = expandedAdvanced) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Title override",
                                            fontSize = 12.sp,
                                            color = TextMuted,
                                            fontWeight = FontWeight.Bold
                                        )
                                        OutlinedTextField(
                                            value = inputTitle,
                                            onValueChange = { inputTitle = it },
                                            placeholder = { Text("e.g., Tech Talk on Robotics", color = Color.Gray, fontSize = 13.sp) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = PrimaryNeon,
                                                unfocusedBorderColor = Color(0x4F939099),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Text(
                                            text = "Transcription / Description (Helps AI finding core clips)",
                                            fontSize = 12.sp,
                                            color = TextMuted,
                                            fontWeight = FontWeight.Bold
                                        )
                                        OutlinedTextField(
                                            value = inputDesc,
                                            onValueChange = { inputDesc = it },
                                            placeholder = { Text("Paste transcript keywords or details...", color = Color.Gray, fontSize = 13.sp) },
                                            minLines = 2,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = PrimaryNeon,
                                                unfocusedBorderColor = Color(0x4F939099),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // Submit Button
                                Button(
                                    onClick = {
                                        if (inputUrl.isNotEmpty()) {
                                            val t = if (inputTitle.isNotEmpty()) inputTitle else "Custom Ingested Stream File"
                                            val d = if (inputDesc.isNotEmpty()) inputDesc else "Custom imported high retention lecture video file."
                                            viewModel.importVideo(
                                                title = t,
                                                description = d,
                                                sourceUrl = inputUrl,
                                                duration = 120,
                                                transcript = if (inputDesc.isNotEmpty()) inputDesc else "Today we are exploring future-facing creative technology nodes, system architecture, engineering pipelines and scaling product concepts fast."
                                            )
                                        }
                                    },
                                    enabled = inputUrl.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryNeon,
                                        contentColor = SecondaryNeon,
                                        disabledContainerColor = PrimaryNeon.copy(alpha = 0.15f),
                                        disabledContentColor = TextMuted
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("import_video_submit_button")
                                ) {
                                    Icon(Icons.Default.Camera, contentDescription = "Gears", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Bake Viral Clips", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        // File picker upload tab
                        if (localVideoUri == null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { videoPickerLauncher.launch("video/*") }
                                    .border(1.dp, Color(0x2B49454F), RoundedCornerShape(28.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .drawBehind {
                                            drawRoundRect(
                                                color = Color(0x3FD0BCFF),
                                                style = Stroke(
                                                    width = 2.dp.toPx(),
                                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                                ),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx())
                                            )
                                        }
                                        .padding(vertical = 40.dp, horizontal = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Upload",
                                        tint = PrimaryNeon,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "Select Local Video File",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Pick MP4, WEBM, MKV directly from your device storage to play, subtitle & crop natively.",
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        } else {
                            // Video detail view
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0x2B49454F), RoundedCornerShape(28.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SecondaryNeon),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VideoFile,
                                                contentDescription = "VideoFile",
                                                tint = PrimaryNeon,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = localVideoName,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Duration: ${localVideoDuration}s",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                        IconButton(onClick = {
                                            localVideoUri = null
                                            localVideoName = ""
                                            localVideoDuration = 0
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear selected", tint = RatingLow)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Video Theme / Transcription Hints (Optional)",
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedTextField(
                                        value = inputDesc,
                                        onValueChange = { inputDesc = it },
                                        placeholder = { Text("What happens in this video? Helps the AI structure dynamic short clips...", color = Color.Gray, fontSize = 13.sp) },
                                        minLines = 2,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PrimaryNeon,
                                            unfocusedBorderColor = Color(0x4F939099),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Action to ingest local file
                                    Button(
                                        onClick = {
                                            val t = if (inputTitle.isNotEmpty()) inputTitle else localVideoName.substringBeforeLast(".")
                                            val d = if (inputDesc.isNotEmpty()) inputDesc else "Imported high retention device video file."
                                            viewModel.importVideo(
                                                title = t,
                                                description = d,
                                                sourceUrl = localVideoUri.toString(),
                                                duration = localVideoDuration,
                                                transcript = if (inputDesc.isNotEmpty()) inputDesc else "Today we are testing an uploaded local video file, configuring modern speech components, syncing responsive captions, and exporting short vertical highlights."
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PrimaryNeon,
                                            contentColor = SecondaryNeon
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "Bake", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Bake Viral Clips", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // API Status Check Panel
            item {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val isKeyActive = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isKeyActive) PrimaryNeon.copy(alpha = 0.1f) else Color(0x15FFB4AB)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isKeyActive) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = "Status Key",
                            tint = if (isKeyActive) PrimaryNeon else RatingLow,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isKeyActive) "GEMINI ENGINE ACTIVE" else "DUMMY MODE / PROTOTYPE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isKeyActive) PrimaryNeon else RatingLow,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isKeyActive) 
                                    "Connected to Gemini-3.5-Flash for analyzing viral hooks and transcribing clips dynamically."
                                else 
                                    "No Gemini key configured in Secrets panel. Using high-fidelity local templates to simulate clip extractions.",
                                fontSize = 10.sp,
                                color = TextMuted,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // Quick Start Title
            item {
                Text(
                    text = "Studio Podcast Presets (Test Instantly!)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Pre-configured videos grid
            items(SampleVideos.list) { video ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.importVideo(
                                title = video.title,
                                description = video.description,
                                sourceUrl = video.url,
                                duration = video.durationSeconds,
                                transcript = video.transcript
                            )
                        }
                        .border(1.dp, Color(0x0F8D8FA6), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Custom vector icon placeholder mimicking a high-end podcast clip
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = if (video.url.contains("ai")) 
                                            listOf(SecondaryNeon, PrimaryNeon)
                                        else 
                                            listOf(SecondaryNeon, Color(0xFF5D4E8C))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (video.url.contains("ai")) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                    contentDescription = "Podcast",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "${video.durationSeconds}s",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = video.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = video.description,
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // TRANSCRIPT READING & AI ANALYZER FULL-SCREEN VIEW (Overlay when loading)
        AnimatedVisibility(
            visible = loadingState is LoadingState.Analyzing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val state = loadingState as? LoadingState.Analyzing
            val progress = state?.progress ?: 0
            val currentStep = state?.currentStep ?: ""
            val preview = state?.transcriptPreview ?: ""

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Pulsing Ring Indicator around glowing Logo
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .drawBehind {
                                var alpha = (progress % 50) / 50f
                                if (alpha < 0.2f) alpha = 0.6f
                                drawCircle(
                                    color = PrimaryNeon.copy(alpha = 1.0f - alpha),
                                    radius = (60f + (progress * 1.5f)).dp.toPx(),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "AI Core",
                            tint = PrimaryNeon,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "BAKING DYNAMIC CLIPS",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )

                    // Progress Loader
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        color = PrimaryNeon,
                        trackColor = ContainerGrey,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Text(
                        text = "$progress%",
                        color = PrimaryNeon,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Analyzing step text description
                    Text(
                        text = currentStep,
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    if (preview.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .border(1.dp, Color(0x1F8D8FA6), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "TRANSCRIPTION TRACKER",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNeon,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = preview,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = TextWhite,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto navigate to Editor on synthesis Success
    LaunchedEffect(loadingState) {
        if (loadingState is LoadingState.Success) {
            onNavigateToEditor()
        }
    }
}
