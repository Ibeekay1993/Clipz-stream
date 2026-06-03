package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WordTimestamp
import com.example.ui.theme.ContainerGrey
import com.example.ui.theme.PrimaryNeon
import com.example.ui.theme.SecondaryNeon
import com.example.ui.theme.SurfaceSlate
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient

private class PositionRef(var value: Long)
private class PlaybackStateRef(var isPlaying: Boolean? = null)

@Composable
fun VideoPlayerSimulator(
    modifier: Modifier = Modifier,
    title: String,
    thumbnailType: String, // "ai" or "routine"
    isPlaying: Boolean,
    currentPositionMs: Long,
    aspectRatio: String, // "9:16", "1:1", "16:9"
    captionStyle: String, // "Kinetic Yellow", "Cyber Glow", "Minimal Bold"
    panOffset: Float, // 0f to 1f horizontal center shifted
    captions: List<WordTimestamp>,
    videoUri: String = "",
    onPanOffsetChanged: (Float) -> Unit,
    onPanOffsetCommit: (Float) -> Unit = {}
) {
    // Waveform scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "playerWave")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    // Calculate currently active subtitle word
    val activeWord = captions.find { currentPositionMs in it.startMs..it.endMs }
    val activeWordIndex = captions.indexOf(activeWord)

    val lastPositionRef = remember { PositionRef(0L) }
    val playStateRef = remember { PlaybackStateRef() }
    val lastSeekTimeRef = remember { PositionRef(0L) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .testTag("video_player_simulator_container")
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        val context = androidx.compose.ui.platform.LocalContext.current
        var videoViewLoadFailed by remember(videoUri) { mutableStateOf(false) }
        var webViewLoadFailed by remember(videoUri) { mutableStateOf(false) }

        val isWebViewAvailable = remember(context) {
            try {
                android.webkit.CookieManager.getInstance()
                Class.forName("android.webkit.WebView")
                true
            } catch (t: Throwable) {
                false
            }
        }

        val isRealVideo = remember(videoUri, videoViewLoadFailed) {
            videoUri.isNotEmpty() && !videoViewLoadFailed && (
                videoUri.startsWith("content:") || 
                videoUri.startsWith("file:") || 
                videoUri.startsWith("/") || 
                (videoUri.startsWith("http") && (videoUri.lowercase().endsWith(".mp4") || videoUri.lowercase().endsWith(".mkv") || videoUri.lowercase().endsWith(".m3u8") || videoUri.lowercase().endsWith(".webm")))
            )
        }

        val resolvedUri = remember(videoUri) {
            try {
                if (videoUri.startsWith("/")) {
                    Uri.fromFile(java.io.File(videoUri))
                } else {
                    Uri.parse(videoUri)
                }
            } catch (e: Exception) {
                Uri.EMPTY
            }
        }

        val youtubeId = remember(videoUri) {
            extractYoutubeId(videoUri)
        }
        val isYoutube = remember(youtubeId, isWebViewAvailable, webViewLoadFailed) {
            youtubeId != null && isWebViewAvailable && !webViewLoadFailed
        }

        // Dynamic Speaker visualizer background reflecting video topic
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Luxurious slate cosmic radial gradient
                    val centerColor = if (thumbnailType == "ai") Color(0xFF1B0B30) else Color(0xFF03221C)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(centerColor, Color(0xFF07070B)),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width
                        )
                    )

                    // Overlay high-tech vector nodes in background grid
                    val gridSpacing = 40.dp.toPx()
                    val rows = (size.height / gridSpacing).toInt()
                    val cols = (size.width / gridSpacing).toInt()
                    for (r in 0..rows) {
                        for (c in 0..cols) {
                            drawCircle(
                                color = Color(0x0C00FF87),
                                radius = 2f,
                                center = Offset(c * gridSpacing, r * gridSpacing)
                            )
                        }
                    }
                }
        ) {
            // Video aspect bounding content viewport
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            ) {
                if (isRealVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = ((0.5f - panOffset) * containerWidth.value * 0.5f).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { context ->
                                try {
                                    android.widget.VideoView(context).apply {
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            // Prevents standard error dialog and activity crash
                                            true
                                        }
                                        try {
                                            setVideoURI(resolvedUri)
                                            seekTo(currentPositionMs.toInt())
                                        } catch (e: Exception) {
                                            // safe fallback
                                        }
                                    }
                                } catch (t: Throwable) {
                                    android.util.Log.e("VideoPlayerSimulator", "VideoView instantiation failed", t)
                                    videoViewLoadFailed = true
                                    android.view.View(context)
                                }
                            },
                            update = { videoView ->
                                if (videoView is android.widget.VideoView) {
                                    val tag = videoView.tag as? String
                                    if (tag != videoUri) {
                                        try {
                                            videoView.setVideoURI(resolvedUri)
                                            videoView.seekTo(currentPositionMs.toInt())
                                            playStateRef.isPlaying = null
                                        } catch (e: Exception) {
                                            // safe fallback
                                        }
                                        videoView.tag = videoUri
                                    }

                                    if (isPlaying) {
                                        if (playStateRef.isPlaying != true) {
                                            try {
                                                videoView.start()
                                                playStateRef.isPlaying = true
                                            } catch (e: Exception) {
                                                // Handle element recycle gracefully
                                            }
                                        }
                                    } else {
                                        if (playStateRef.isPlaying != false) {
                                            try {
                                                videoView.pause()
                                                playStateRef.isPlaying = false
                                            } catch (e: Exception) {
                                                // Handle element recycle gracefully
                                            }
                                        }
                                    }

                                    // Sync position with zero-IPC local change detection to avoid blocking UI main thread
                                    try {
                                        val lastPos = lastPositionRef.value
                                        val isJump = lastPos == 0L || 
                                                     currentPositionMs < lastPos || 
                                                     (currentPositionMs - lastPos) > 1500L
                                        
                                        if ((!isPlaying && currentPositionMs != lastPos) || isJump) {
                                            val now = System.currentTimeMillis()
                                            if (now - lastSeekTimeRef.value > 250L || isJump) {
                                                videoView.seekTo(currentPositionMs.toInt())
                                                lastSeekTimeRef.value = now
                                            }
                                        }
                                        lastPositionRef.value = currentPositionMs
                                    } catch (e: Throwable) {
                                        // Handle element recycle gracefully
                                    }
                                }
                            },
                            onRelease = { videoView ->
                                if (videoView is android.widget.VideoView) {
                                    try {
                                        videoView.stopPlayback()
                                    } catch (e: Exception) {
                                        // safe fallback
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (isYoutube && youtubeId != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = ((0.5f - panOffset) * containerWidth.value * 0.5f).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { context ->
                                try {
                                    WebView(context).apply {
                                        setBackgroundColor(android.graphics.Color.BLACK)
                                        settings.javaScriptEnabled = true
                                        settings.mediaPlaybackRequiresUserGesture = false
                                        settings.domStorageEnabled = true
                                        webViewClient = WebViewClient()
                                        webChromeClient = WebChromeClient()
                                        
                                        val startSecState = (currentPositionMs / 1000).toInt()
                                        val embedUrl = "https://www.youtube.com/embed/$youtubeId?autoplay=1&mute=0&controls=1&loop=1&playlist=$youtubeId&start=$startSecState"
                                        loadUrl(embedUrl)
                                    }
                                } catch (t: Throwable) {
                                    android.util.Log.e("VideoPlayerSimulator", "WebView creation failed", t)
                                    webViewLoadFailed = true
                                    android.view.View(context)
                                }
                            },
                            update = { view ->
                                if (view is WebView) {
                                    val lastPos = lastPositionRef.value
                                    val isJump = lastPos == 0L || 
                                                 currentPositionMs < lastPos || 
                                                 (currentPositionMs - lastPos) > 1500L
                                                 
                                    val tag = view.tag as? String
                                    val autopl = if (isPlaying) 1 else 0
                                    if (tag != videoUri) {
                                        val startSecState = (currentPositionMs / 1000).toInt()
                                        val embedUrl = "https://www.youtube.com/embed/$youtubeId?autoplay=$autopl&mute=0&controls=1&loop=1&playlist=$youtubeId&start=$startSecState&enablejsapi=1"
                                        view.loadUrl(embedUrl)
                                        view.tag = videoUri
                                        lastPositionRef.value = currentPositionMs
                                        playStateRef.isPlaying = isPlaying
                                        lastSeekTimeRef.value = System.currentTimeMillis()
                                    } else if (isJump) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastSeekTimeRef.value > 1000L) {
                                            val startSecState = (currentPositionMs / 1000).toInt()
                                            val embedUrl = "https://www.youtube.com/embed/$youtubeId?autoplay=$autopl&mute=0&controls=1&loop=1&playlist=$youtubeId&start=$startSecState&enablejsapi=1"
                                            view.loadUrl(embedUrl)
                                            lastPositionRef.value = currentPositionMs
                                            playStateRef.isPlaying = isPlaying
                                            lastSeekTimeRef.value = now
                                        }
                                    } else {
                                        if (isPlaying) {
                                            if (playStateRef.isPlaying != true) {
                                                view.evaluateJavascript("const v = document.querySelector('video'); if (v) { v.play(); } else { document.querySelector('iframe')?.contentWindow?.postMessage('{\"event\":\"command\",\"func\":\"playVideo\",\"args\":\"\"}', '*'); }", null)
                                                playStateRef.isPlaying = true
                                            }
                                        } else {
                                            if (playStateRef.isPlaying != false) {
                                                view.evaluateJavascript("const v = document.querySelector('video'); if (v) { v.pause(); } else { document.querySelector('iframe')?.contentWindow?.postMessage('{\"event\":\"command\",\"func\":\"pauseVideo\",\"args\":\"\"}', '*'); }", null)
                                                playStateRef.isPlaying = false
                                            }
                                        }
                                    }
                                    // ALWAYS update lastPositionRef.value to match currentPositionMs so we never drift and loop-reload
                                    lastPositionRef.value = currentPositionMs
                                }
                            },
                            onRelease = { view ->
                                if (view is WebView) {
                                    try {
                                        view.stopLoading()
                                        view.loadUrl("about:blank")
                                    } catch (e: Exception) {
                                        // safe fallback
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                    }
                } else {
                    // Speaker Core - Glowing animated circular face
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(
                                x = ((panOffset - 0.5f) * containerWidth.value * 0.4f).dp,
                                y = (-20).dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .drawBehind {
                                    // Draw pulsating magnetic vector rings around speaker
                                    val baseRadius = 55.dp.toPx()
                                    val scaleMult = if (isPlaying) waveScale else 1.0f
                                    drawCircle(
                                        color = if (thumbnailType == "ai") Color(0x1F9C27B0) else Color(0x1F00FF87),
                                        radius = baseRadius * 1.3f * scaleMult,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    drawCircle(
                                        color = if (thumbnailType == "ai") Color(0x3B9C27B0) else Color(0x3B00FF87),
                                        radius = baseRadius * 1.15f * (scaleMult * 0.95f),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                                .clip(CircleShape)
                                .background(if (thumbnailType == "ai") Color(0xFF2E1A47) else Color(0xFF0B2E1C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = "Speaker Feed",
                                tint = if (thumbnailType == "ai") Color(0xFFD0BCFF) else PrimaryNeon,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Host tag
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x5E000000))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) PrimaryNeon else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (thumbnailType == "ai") "LEX FRIDMAN" else "MOTIVATOR PRO",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // SUBTITLE CAPTIONS DISPLAY LAYER (Overlaid in Center of cropping box)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                        .offset(x = ((panOffset - 0.5f) * containerWidth.value * 0.4f).dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (captions.isNotEmpty()) {
                        RenderDynamicCaptions(
                            captions = captions,
                            activeIndex = activeWordIndex,
                            style = captionStyle
                        )
                    } else {
                        // Helpful subtitle instructions when no clips analyzed
                        Text(
                            text = "[ AI Subtitles Auto-Generate Here ]",
                            color = Color(0x8CFFFFFF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // CROPPING OVERLAY MASKING VIEWFINDER (9:16 or 1:1)
            // Displays black translucent overlays covering regions outside of the crop viewport.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val fullW = size.width
                val fullH = size.height

                // Define crop rectangle based on aspectRatio selection
                val cropW = when (aspectRatio) {
                    "9:16" -> fullH * (9f / 16f)
                    "1:1" -> fullW * 0.85f
                    else -> fullW // 16:9 is full size
                }
                val cropH = when (aspectRatio) {
                    "9:16" -> fullH
                    "1:1" -> fullW * 0.85f
                    else -> fullH
                }

                // Determine X centered position based on manual panOffset
                val maxOffsetRange = fullW - cropW
                val startX = maxOffsetRange * panOffset
                val endX = startX + cropW

                val startY = (fullH - cropH) / 2f
                val endY = startY + cropH

                // If less than 16:9, draw the background dimming borders
                if (aspectRatio != "16:9") {
                    // Left Dim
                    drawRect(
                        color = Color(0xAA000000),
                        size = Size(startX, fullH)
                    )
                    // Right Dim
                    drawRect(
                        color = Color(0xAA000000),
                        topLeft = Offset(endX, 0f),
                        size = Size(fullW - endX, fullH)
                    )
                    // Top Dim (for 1:1)
                    drawRect(
                        color = Color(0xAA000000),
                        topLeft = Offset(startX, 0f),
                        size = Size(cropW, startY)
                    )
                    // Bottom Dim (for 1:1)
                    drawRect(
                        color = Color(0xAA000000),
                        topLeft = Offset(startX, endY),
                        size = Size(cropW, fullH - endY)
                    )

                    // Glowing bounding frame representing vertical clip camera viewfinder
                    drawRect(
                        color = Color(0x8C00FF87),
                        topLeft = Offset(startX, startY),
                        size = Size(cropW, cropH),
                        style = Stroke(width = 3f)
                    )

                    // Subtle crop handles at center left/right
                    drawCircle(
                        color = PrimaryNeon,
                        radius = 12f,
                        center = Offset(startX, fullH / 2f)
                    )
                    drawCircle(
                        color = PrimaryNeon,
                        radius = 12f,
                        center = Offset(endX, fullH / 2f)
                    )
                }
            }

            val currentPanOffset by rememberUpdatedState(panOffset)

            // Draggable Crop Area gesture hook
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                onPanOffsetCommit(currentPanOffset)
                            },
                            onDragCancel = {
                                onPanOffsetCommit(currentPanOffset)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val deltaX = dragAmount.x
                                val fullW = size.width
                                val step = deltaX / fullW
                                val nextOffset = (currentPanOffset + step).coerceIn(0f, 1f)
                                onPanOffsetChanged(nextOffset)
                            }
                        )
                    }
            )

            // Dynamic header displaying crop profile inside the viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xD9101115))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE PREVIEW",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "${aspectRatio} Frame Crop",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ContainerGrey)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
fun RenderDynamicCaptions(
    captions: List<WordTimestamp>,
    activeIndex: Int,
    style: String
) {
    // Collect preceding, active and following indices to display a sliding 3-word focal capsule
    val rangeSize = 3
    val startIndex = (activeIndex - 1).coerceAtLeast(0)
    val endIndex = (activeIndex + 2).coerceAtMost(captions.size - 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (style) {
            "Cyber Glow" -> {
                // Energetic Neon Cyber Subtitle block - ALL CAPS with neon stroke backings
                val text = buildAnnotatedString {
                    for (i in startIndex..endIndex) {
                        val wordToken = captions[i].word.uppercase()
                        if (i == activeIndex) {
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF00FF87),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    background = Color(0x78000000)
                                )
                            ) {
                                append(" $wordToken ")
                            }
                        } else {
                            withStyle(
                                SpanStyle(
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    background = Color(0x3B000000)
                                )
                            ) {
                                append(" $wordToken ")
                            }
                        }
                    }
                }
                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
            }
            "Minimal Bold" -> {
                // High contrast clean display box like classical tutorials
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val text = buildAnnotatedString {
                        for (i in startIndex..endIndex) {
                            val wordToken = captions[i].word
                            if (i == activeIndex) {
                                withStyle(
                                    SpanStyle(
                                        color = Color.Black,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(" $wordToken ")
                                }
                            } else {
                                withStyle(
                                    SpanStyle(
                                        color = Color.Gray,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                ) {
                                    append(" $wordToken ")
                                }
                            }
                        }
                    }
                    Text(
                        text = text,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                // "Kinetic Yellow" (Standard Opus-Clips Yellow Highlight)
                val text = buildAnnotatedString {
                    for (i in startIndex..endIndex) {
                        val wordToken = captions[i].word
                        if (i == activeIndex) {
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFFFFCC00), // Opus Gold Yellow Accent
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    background = Color(0xB5000000)
                                )
                            ) {
                                append(" $wordToken ")
                            }
                        } else {
                            withStyle(
                                SpanStyle(
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    background = Color(0x40000000)
                                )
                            ) {
                                append(" $wordToken ")
                            }
                        }
                    }
                }
                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
            }
        }
    }
}

private fun extractYoutubeId(url: String): String? {
    return try {
        val trimmed = url.trim()
        if (trimmed.contains("youtu.be/")) {
            trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
        } else if (trimmed.contains("youtube.com/embed/")) {
            trimmed.substringAfter("youtube.com/embed/").substringBefore("?").substringBefore("/")
        } else if (trimmed.contains("/shorts/")) {
            trimmed.substringAfter("/shorts/").substringBefore("?").substringBefore("/")
        } else if (trimmed.contains("v=")) {
            trimmed.substringAfter("v=").substringBefore("&")
        } else if (trimmed.contains("/watch/")) {
            trimmed.substringAfter("/watch/").substringBefore("?").substringBefore("/")
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
