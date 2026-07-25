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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.VideoPlayerSimulator
import com.example.ui.components.extractYoutubeId
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
    
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableStateOf(0L) }
    
    var selectedTool by remember { mutableStateOf("AI Clipping") }
    var selectedLanguage by remember { mutableStateOf("Auto / No translation") }
    var selectedClipLength by remember { mutableStateOf("Auto (<90s)") }
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var lengthDropdownExpanded by remember { mutableStateOf(false) }
    var fetchedYtChannel by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(inputUrl) {
        if (inputUrl.isNotBlank()) {
            val ytId = extractYoutubeId(inputUrl)
            if (ytId != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$ytId&format=json"
                        val conn = java.net.URL(oembedUrl).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        if (conn.responseCode == 200) {
                            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                            val jsonObj = org.json.JSONObject(jsonStr)
                            val title = jsonObj.optString("title")
                            val author = jsonObj.optString("author_name")
                            withContext(Dispatchers.Main) {
                                if (!title.isNullOrBlank()) {
                                    inputTitle = title
                                }
                                if (!author.isNullOrBlank()) {
                                    fetchedYtChannel = "YouTube • $author"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Safe fallback
                    }
                }
            } else {
                fetchedYtChannel = null
            }
        } else {
            fetchedYtChannel = null
        }
    }
    
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
                                        text = "Choose what to do with this video",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextWhite,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Paste a video link or upload to generate clips",
                                        fontSize = 13.sp,
                                        color = TextMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Tool Selection Chips (Wayin-style action selector)
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    items(listOf(
                                        "AI Clipping" to Icons.Default.VideoCall,
                                        "Find Moments" to Icons.Default.Search,
                                        "Game Clipping" to Icons.Default.SportsEsports,
                                        "Video Editor" to Icons.Default.Edit,
                                        "Video Summary" to Icons.Default.Description,
                                        "Video Transcripts" to Icons.Default.Subtitles,
                                        "AI Subtitles" to Icons.Default.ClosedCaption,
                                        "Speech Enhancer" to Icons.Default.GraphicEq,
                                        "AI Reframe" to Icons.Default.Crop,
                                        "B-roll" to Icons.Default.Movie,
                                        "AI Hook" to Icons.Default.AutoAwesome
                                    )) { (toolName, toolIcon) ->
                                        val isSelected = selectedTool == toolName
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedTool = toolName },
                                            label = { Text(toolName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = toolIcon,
                                                    contentDescription = toolName,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isSelected) Color.Black else PrimaryNeon
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryNeon,
                                                selectedLabelColor = Color.Black,
                                                containerColor = ContainerGrey,
                                                labelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(20.dp),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = Color(0x3B8D8FA6),
                                                selectedBorderColor = PrimaryNeon
                                            )
                                        )
                                    }
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

                                // Interactive Video Preview Box (Show source video before clipping)
                                AnimatedVisibility(visible = inputUrl.isNotBlank()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(ContainerGrey)
                                            .border(1.dp, PrimaryNeon.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayCircleOutline,
                                                    contentDescription = "Source Video Preview",
                                                    tint = PrimaryNeon,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Source Video Preview",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            IconButton(
                                                onClick = { isPreviewPlaying = !isPreviewPlaying },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPreviewPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                    contentDescription = if (isPreviewPlaying) "Pause Preview" else "Play Preview",
                                                    tint = PrimaryNeon,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        ) {
                                            val ytPreviewId = remember(inputUrl) { extractYoutubeId(inputUrl) }
                                            if (ytPreviewId != null) {
                                                AndroidView(
                                                    factory = { ctx ->
                                                        WebView(ctx).apply {
                                                            setBackgroundColor(android.graphics.Color.BLACK)
                                                            settings.javaScriptEnabled = true
                                                            settings.domStorageEnabled = true
                                                            settings.mediaPlaybackRequiresUserGesture = false
                                                            webViewClient = WebViewClient()
                                                            webChromeClient = WebChromeClient()
                                                            val embedHtml = """
                                                                <!DOCTYPE html>
                                                                <html>
                                                                <head>
                                                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                                                <style>
                                                                body, html { margin:0; padding:0; width:100%; height:100%; background-color:#000; overflow:hidden; }
                                                                iframe { width:100%; height:100%; border:none; }
                                                                </style>
                                                                </head>
                                                                <body>
                                                                <iframe src="https://www.youtube-nocookie.com/embed/$ytPreviewId?autoplay=1&mute=0&controls=1&playsinline=1&rel=0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>
                                                                </body>
                                                                </html>
                                                            """.trimIndent()
                                                            loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null)
                                                        }
                                                    },
                                                    update = { view ->
                                                        val tag = view.tag as? String
                                                        if (tag != ytPreviewId) {
                                                            val embedHtml = """
                                                                <!DOCTYPE html>
                                                                <html>
                                                                <head>
                                                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                                                <style>
                                                                body, html { margin:0; padding:0; width:100%; height:100%; background-color:#000; overflow:hidden; }
                                                                iframe { width:100%; height:100%; border:none; }
                                                                </style>
                                                                </head>
                                                                <body>
                                                                <iframe src="https://www.youtube-nocookie.com/embed/$ytPreviewId?autoplay=1&mute=0&controls=1&playsinline=1&rel=0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>
                                                                </body>
                                                                </html>
                                                            """.trimIndent()
                                                            view.loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null)
                                                            view.tag = ytPreviewId
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                VideoPlayerSimulator(
                                                    modifier = Modifier.fillMaxSize(),
                                                    title = if (inputTitle.isNotEmpty()) inputTitle else "Source Video Preview",
                                                    thumbnailType = "ai",
                                                    isPlaying = isPreviewPlaying,
                                                    currentPositionMs = previewPositionMs,
                                                    aspectRatio = "16:9",
                                                    captionStyle = "Kinetic Yellow",
                                                    panOffset = 0.5f,
                                                    captions = emptyList(),
                                                    videoUri = inputUrl,
                                                    onPanOffsetChanged = {}
                                                )
                                            }

                                            // Top Right Source Badge
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Black.copy(alpha = 0.8f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (ytPreviewId != null) "YOUTUBE HD" else "MP4 SOURCE",
                                                    color = PrimaryNeon,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Video Title & Channel Source info (Wayin-style)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (inputTitle.isNotBlank()) inputTitle else "Video Source Loaded",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(SurfaceSlate)
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = fetchedYtChannel ?: "YouTube Source",
                                                        color = PrimaryNeon,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
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

                                    // Local Video File Interactive Preview Player
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(ContainerGrey)
                                            .border(1.dp, PrimaryNeon.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayCircleOutline,
                                                    contentDescription = "File Video Preview",
                                                    tint = PrimaryNeon,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Uploaded Video Preview",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            IconButton(
                                                onClick = { isPreviewPlaying = !isPreviewPlaying },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPreviewPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                    contentDescription = if (isPreviewPlaying) "Pause Preview" else "Play Preview",
                                                    tint = PrimaryNeon,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        ) {
                                            VideoPlayerSimulator(
                                                modifier = Modifier.fillMaxSize(),
                                                title = localVideoName,
                                                thumbnailType = "ai",
                                                isPlaying = isPreviewPlaying,
                                                currentPositionMs = previewPositionMs,
                                                aspectRatio = "16:9",
                                                captionStyle = "Kinetic Yellow",
                                                panOffset = 0.5f,
                                                captions = emptyList(),
                                                videoUri = localVideoUri.toString(),
                                                onPanOffsetChanged = {}
                                            )
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
            // Wayin-style Options: Language & Clip Length Dropdown Selectors
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x1F8D8FA6), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Language Dropdown Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Language", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Box {
                                OutlinedButton(
                                    onClick = { languageDropdownExpanded = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3B8D8FA6))
                                ) {
                                    Text(text = selectedLanguage, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = TextMuted)
                                }
                                DropdownMenu(
                                    expanded = languageDropdownExpanded,
                                    onDismissRequest = { languageDropdownExpanded = false }
                                ) {
                                    listOf("Auto / No translation", "English", "Spanish", "French", "German", "Japanese", "Mandarin").forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(lang, fontSize = 13.sp) },
                                            onClick = {
                                                selectedLanguage = lang
                                                languageDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Divider(color = Color(0x1F8D8FA6))

                        // Clip Length Dropdown Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Clip Length", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Box {
                                OutlinedButton(
                                    onClick = { lengthDropdownExpanded = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3B8D8FA6))
                                ) {
                                    Text(text = selectedClipLength, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = TextMuted)
                                }
                                DropdownMenu(
                                    expanded = lengthDropdownExpanded,
                                    onDismissRequest = { lengthDropdownExpanded = false }
                                ) {
                                    listOf("Auto (<90s)", "Short (<30s)", "Medium (30-60s)", "Long (60-90s)").forEach { len ->
                                        DropdownMenuItem(
                                            text = { Text(len, fontSize = 13.sp) },
                                            onClick = {
                                                selectedClipLength = len
                                                lengthDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Unified "Create Viral Clips" CTA
            item {
                val buttonEnabled = if (selectedImportTab == 0) inputUrl.isNotEmpty() else localVideoUri != null
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (selectedImportTab == 0) {
                                if (inputUrl.isNotEmpty()) {
                                    val t = if (inputTitle.isNotEmpty()) inputTitle else "GEHGEH DIDN'T HOLD BACK: PELLER, JARVIS"
                                    val d = if (inputDesc.isNotEmpty()) inputDesc else "High engagement interview podcast clip"
                                    viewModel.importVideo(
                                        title = t,
                                        description = d,
                                        sourceUrl = inputUrl,
                                        duration = 120,
                                        transcript = if (inputDesc.isNotEmpty()) inputDesc else "Today we discuss tradition vs modernity, young marriage arguments, cultural expectations and personal growth.",
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
                                        transcript = if (inputDesc.isNotEmpty()) inputDesc else "Today we process uploaded local video files, configuring speech components, syncing responsive captions, and exporting short highlights.",
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
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("import_video_submit_button")
                    ) {
                        Text(
                            text = "Create Viral Clips",
                            color = if (buttonEnabled) Color.Black else TextMuted,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = "By continuing, you confirm the video is your own. Using others' content may violate copyright laws.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }        // TRANSCRIPT READING & AI ANALYZER FULL-SCREEN VIEW (Overlay when loading or error)
        AnimatedVisibility(
            visible = loadingState is LoadingState.Analyzing || loadingState is LoadingState.Error,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg.copy(alpha = 0.95f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (loadingState is LoadingState.Error) {
                    val errState = loadingState as LoadingState.Error
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, RatingLow, RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(RatingLow.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = RatingLow,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = "Processing Interrupted",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = errState.message,
                                color = TextMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    viewModel.resetError()
                                    selectedImportTab = 1 // Switch to Upload Video file tab!
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon, contentColor = Color.Black),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Try Uploading Video File", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.resetError() },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Text("Dismiss", color = Color.White)
                            }
                        }
                    }
                } else {
                    val state = loadingState as? LoadingState.Analyzing
                    val progress = state?.progress ?: 0
                    val currentStep = state?.currentStep ?: ""
                    val preview = state?.transcriptPreview ?: ""

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
                            text = "We're processing your video. This won't take long...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        // Wayin-style Progress Box (Green pill background)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(ContainerGrey)
                                .border(1.dp, Color(0x3B8D8FA6), RoundedCornerShape(22.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress / 100f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(PrimaryNeon)
                            )
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$progress%",
                                    color = if (progress > 50) Color.Black else PrimaryNeon,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

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
    }

    // Auto navigate to Editor on synthesis Success
    LaunchedEffect(loadingState) {
        if (loadingState is LoadingState.Success) {
            onNavigateToEditor()
        }
    }
}
