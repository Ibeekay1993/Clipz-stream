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
import com.example.ui.theme.*
import com.example.ui.viewmodel.LoadingState
import com.example.ui.viewmodel.VideoClipperViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    
    var numClips by remember { mutableStateOf(3) }
    
    val scope = rememberCoroutineScope()
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            localVideoUri = uri
            localVideoName = getFileName(context, uri)
            
            scope.launch {
                val durSec = withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durMs = durationStr?.toLong() ?: 60000L
                        durMs / 1000L
                    } catch (e: Exception) {
                        120L // fallback
                    } finally {
                        try {
                            retriever.release()
                        } catch (e: Exception) {}
                    }
                }
                localVideoDuration = durSec
                if (inputTitle.isEmpty()) {
                    inputTitle = localVideoName.substringBeforeLast(".")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            // Header Banner minimal: brand logo + settings icon
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryNeon),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieFilter,
                                contentDescription = "Logo",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "LClipz Studio",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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
                            .background(SurfaceSlate)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Web Link" to Icons.Default.Link, "Upload File" to Icons.Default.CloudUpload).forEachIndexed { index, pair ->
                            val active = selectedImportTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) ContainerGrey else Color.Transparent)
                                    .clickable { selectedImportTab = index }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x1F8D8FA6), RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // AI Powered Features Enabled

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Paste a video link to begin",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextWhite,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "YouTube, Twitch, or online media URLs",
                                        fontSize = 13.sp,
                                        color = TextMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // URL Field
                                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                OutlinedTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it },
                                    placeholder = { Text("https://www.youtube.com/watch?v=...", color = TextMuted, fontSize = 13.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryNeon,
                                        unfocusedBorderColor = Color(0x3B8D8FA6),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = ContainerGrey,
                                        unfocusedContainerColor = ContainerGrey
                                    ),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val text = clipboardManager.getText()?.text
                                                    if (!text.isNullOrBlank()) {
                                                        inputUrl = text
                                                    }
                                                } catch (e: Exception) {
                                                    // fail-safe
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Paste Clipboard",
                                                tint = PrimaryNeon,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("import_video_url_input")
                                )

                                // Minimal Example Link
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Not sure what to clip? ",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = "Try an example: Lex Fridman",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryNeon,
                                        modifier = Modifier.clickable {
                                            inputUrl = "https://www.youtube.com/watch?v=AaMdXZMvT3w"
                                            inputTitle = "🤖 Lex Fridman"
                                            inputDesc = "AI expert conversation on future software engineering & deep models."
                                        }
                                    )
                                }

                                // Toggle manual metadata
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { expandedAdvanced = !expandedAdvanced }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Toggle Settings",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Advanced Specifications",
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = if (expandedAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle Settings",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
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
                                            placeholder = { Text("e.g., Tech Talk on Robotics", color = TextMuted, fontSize = 13.sp) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = PrimaryNeon,
                                                unfocusedBorderColor = Color(0x3B8D8FA6),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedContainerColor = ContainerGrey,
                                                unfocusedContainerColor = ContainerGrey
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
                                            placeholder = { Text("Paste transcript keywords or details...", color = TextMuted, fontSize = 13.sp) },
                                            minLines = 2,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = PrimaryNeon,
                                                unfocusedBorderColor = Color(0x3B8D8FA6),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedContainerColor = ContainerGrey,
                                                unfocusedContainerColor = ContainerGrey
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // File picker upload tab
                        if (localVideoUri == null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { videoPickerLauncher.launch("video/*") }
                                    .border(1.dp, Color(0x1F8D8FA6), RoundedCornerShape(24.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .drawBehind {
                                            drawRoundRect(
                                                color = PrimaryNeon.copy(alpha = 0.3f),
                                                style = Stroke(
                                                    width = 2.dp.toPx(),
                                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                                ),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
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
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x1400FF87)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, PrimaryNeon.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "info",
                                        tint = PrimaryNeon,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "On-Device Video Clipper",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Upload and process any MP4/MKV video directly from your device storage to trim or split selected high-retention vertical frames locally with our native video processing engine.",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            // Video detail view
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0x1F8D8FA6), RoundedCornerShape(24.dp))
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
                                        placeholder = { Text("What happens in this video? Helps the AI structure dynamic short clips...", color = TextMuted, fontSize = 13.sp) },
                                        minLines = 2,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = PrimaryNeon,
                                            unfocusedBorderColor = Color(0x3B8D8FA6),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedContainerColor = ContainerGrey,
                                            unfocusedContainerColor = ContainerGrey
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // OutlinedTextField and column close (No internal button here)
                                }
                            }
                        }
                    }
                }
            }
            // Number of Clips selector
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Number of Clips to Generate",
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$numClips",
                            fontSize = 16.sp,
                            color = PrimaryNeon,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = numClips.toFloat(),
                        onValueChange = { numClips = it.toInt() },
                        valueRange = 1f..8f,
                        steps = 6,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryNeon,
                            activeTrackColor = PrimaryNeon,
                            inactiveTrackColor = Color(0x3B8D8FA6)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Unified "Analyse & generate clips" outer CTA
            item {
                val buttonEnabled = if (selectedImportTab == 0) inputUrl.isNotEmpty() else localVideoUri != null
                Button(
                    onClick = {
                        if (selectedImportTab == 0) {
                            if (inputUrl.isNotEmpty()) {
                                val t = if (inputTitle.isNotEmpty()) inputTitle else "Custom Ingested Stream"
                                val d = if (inputDesc.isNotEmpty()) inputDesc else "Custom imported high retention stream presentation."
                                viewModel.importVideo(
                                    title = t,
                                    description = d,
                                    sourceUrl = inputUrl,
                                    duration = 120,
                                    transcript = if (inputDesc.isNotEmpty()) inputDesc else "Today we are exploring future-facing creative technology nodes, system architecture, engineering pipelines and scaling product concepts fast.",
                                    numClips = numClips
                                )
                            }
                        } else {
                            localVideoUri?.let { uri ->
                                val t = if (inputTitle.isNotEmpty()) inputTitle else localVideoName.substringBeforeLast(".")
                                val d = if (inputDesc.isNotEmpty()) inputDesc else "Imported high retention device video file."
                                    viewModel.importVideo(
                                        title = t,
                                        description = d,
                                        sourceUrl = uri.toString(),
                                        duration = localVideoDuration,
                                        transcript = if (inputDesc.isNotEmpty()) inputDesc else "Today we are processing an uploaded local video file, configuring modern speech components, syncing responsive captions, and exporting short vertical highlights.",
                                        numClips = numClips
                                    )
                            }
                        }
                    },
                    enabled = buttonEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryNeon,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0x1F8D8FA6),
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("import_video_submit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Sparkles",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generate Viral Clips",
                        color = if (buttonEnabled) Color.Black else TextMuted,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
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
                        progress = { progress / 100f },
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
