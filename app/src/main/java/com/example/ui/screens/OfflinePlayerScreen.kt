package com.example.ui.screens

import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.MainActivity
import com.example.ui.viewmodel.MovieViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.lazy.LazyColumn
import com.example.data.local.DownloadEntity

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun OfflinePlayerScreen(
    mediaId: String,
    title: String,
    localFilePath: String,
    viewModel: MovieViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    // Manage Fullscreen & Landscape & Keep Screen On
    DisposableEffect(Unit) {
        activity?.let {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val window = it.window
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = it.window
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        onBack()
    }

    val prefs = context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE)
    
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var isSpeedUp by remember { mutableStateOf(false) }

    var wasLongPress by remember { mutableStateOf(false) }

    // Overlays
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var showSubtitleDrawer by remember { mutableStateOf(false) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    // Subtitle Custom Variables
    var subtitleYOffset by remember { mutableFloatStateOf(prefs.getFloat("sub_y", -16f)) }
    var subtitleSize by remember { mutableFloatStateOf(prefs.getFloat("sub_size", 20f)) }
    var subtitleTimeOffsetMs by remember { mutableLongStateOf(0L) }
    var parsedSubtitles by remember { mutableStateOf<List<com.example.ui.viewmodel.SubtitleLine>>(emptyList()) }
    var activeSubtitleText by remember { mutableStateOf("") }
    var searchSubsList by remember { mutableStateOf<List<com.example.ui.viewmodel.SubtitleHelper.SubtitleItem>?>(null) }
    var isDownloadingSub by remember { mutableStateOf(false) }

    // Update active subtitle
    LaunchedEffect(currentPosition, subtitleTimeOffsetMs, parsedSubtitles) {
        val effectiveTime = currentPosition + subtitleTimeOffsetMs
        val currentSub = parsedSubtitles.find { effectiveTime in it.startTime..it.endTime }
        activeSubtitleText = currentSub?.text ?: ""
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying, showEpisodesDrawer, showSubtitleDrawer) {
        if (showControls && isPlaying && !showEpisodesDrawer && !showSubtitleDrawer) {
            delay(4000)
            showControls = false
        }
    }

    // Active media tracking (allows switching episodes without leaving screen)
    var activeId by remember { mutableStateOf(mediaId) }
    var activeTitle by remember { mutableStateOf(title) }
    var activeLocalFilePath by remember { mutableStateOf(localFilePath) }

    val downloadsList by viewModel.downloads.collectAsState(initial = emptyList())
    val parentTmdbId = if (activeId.contains("-s")) activeId.substringBefore("-s") else activeId
    val seriesEpisodes = remember(downloadsList, parentTmdbId) {
        downloadsList.filter { it.mediaId == parentTmdbId && it.status == "completed" }
    }
    
    val isTv = activeId.contains("-s")

    // Setup ExoPlayer
    val exoPlayer = remember {
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractorsFactory)
        ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build().apply {
            playWhenReady = true
        }
    }

    // Load Media
    LaunchedEffect(activeLocalFilePath) {
        if (activeLocalFilePath.isNotEmpty()) {
            val file = File(activeLocalFilePath)
            if (file.exists()) {
                val mediaItemBuilder = MediaItem.Builder().setUri(Uri.fromFile(file))
                
                val srtFile = File(context.filesDir, "downloads/$activeId.srt")
                val vttFile = File(context.filesDir, "downloads/$activeId.vtt")
                if (srtFile.exists()) {
                    parsedSubtitles = com.example.ui.viewmodel.SubtitleParser.parseBlock(srtFile)
                } else if (vttFile.exists()) {
                    parsedSubtitles = com.example.ui.viewmodel.SubtitleParser.parseBlock(vttFile)
                } else {
                    parsedSubtitles = emptyList()
                }
                
                exoPlayer.setMediaItem(mediaItemBuilder.build())
                val lastPos = prefs.getLong("pos_$activeId", 0L)
                if (lastPos > 0) {
                    exoPlayer.seekTo(lastPos)
                }
                exoPlayer.prepare()
                exoPlayer.play()
                
                // Update position periodically with higher frequency for smooth subtitles
                while (true) {
                    currentPosition = exoPlayer.currentPosition
                    val dur = exoPlayer.duration
                    if (dur > 0) totalDuration = dur
                    isPlaying = exoPlayer.isPlaying
                    // Save position periodically (every 5 seconds) to avoid spamming SharedPreferences
                    if (currentPosition > 0 && currentPosition % 5000 < 50) {
                        prefs.edit().putLong("pos_$activeId", currentPosition).apply()
                    }
                    delay(50)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            prefs.edit().putLong("pos_$activeId", exoPlayer.currentPosition).apply()
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!wasLongPress) {
                            showControls = !showControls
                        }
                        wasLongPress = false
                    },
                    onDoubleTap = { offset ->
                        // Double tap to seek
                        wasLongPress = false
                        val width = this.size.width
                        if (offset.x > width / 2) {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                        } else {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                        }
                    },
                    onPress = { offset ->
                        wasLongPress = false
                        // Long press to 2x speed
                        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                        val pressJob = scope.launch {
                            kotlinx.coroutines.delay(500) // wait for 500ms to consider it a long press
                            wasLongPress = true
                            exoPlayer.setPlaybackSpeed(2f)
                            isSpeedUp = true
                            showControls = false
                        }
                        try {
                            awaitRelease()
                        } finally {
                            pressJob.cancel()
                            exoPlayer.setPlaybackSpeed(1f)
                            isSpeedUp = false
                        }
                    }
                )
            }
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    subtitleView?.visibility = android.view.View.GONE
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2x Speed Badge
        if (isSpeedUp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "2× Speed",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // Custom Subtitle Overlay
        if (activeSubtitleText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .offset(y = subtitleYOffset.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = activeSubtitleText,
                    color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily(
                            androidx.compose.ui.text.googlefonts.Font(
                                googleFont = androidx.compose.ui.text.googlefonts.GoogleFont("IBM Plex Sans Arabic"),
                                fontProvider = androidx.compose.ui.text.googlefonts.GoogleFont.Provider(
                                    providerAuthority = "com.google.android.gms.fonts",
                                    providerPackage = "com.google.android.gms",
                                    certificates = com.example.R.array.com_google_android_gms_fonts_certs
                                ),
                                weight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        ),
                        fontSize = subtitleSize.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = (subtitleSize * 1.5).sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                            blurRadius = 6f
                        )
                    ),
                    modifier = Modifier
                        .padding(bottom = 60.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Custom UI Controls Overlay
        AnimatedVisibility(
            visible = showControls || showEpisodesDrawer || showSubtitleDrawer,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (showEpisodesDrawer || showSubtitleDrawer) Color.Black.copy(alpha = 0.5f) else Color.Transparent)
            ) {
                // Top Bar
                if (!isSpeedUp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Subtitle Button
                        IconButton(
                            onClick = {
                                showSubtitleDrawer = !showSubtitleDrawer
                                showEpisodesDrawer = false
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (showSubtitleDrawer) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Subtitles, contentDescription = "ترجمة", tint = Color.White)
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))

                        // Episodes Button
                        if (isTv) {
                            IconButton(
                                onClick = {
                                    showEpisodesDrawer = !showEpisodesDrawer
                                    showSubtitleDrawer = false
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (showEpisodesDrawer) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f))
                            ) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = "الحلقات", tint = Color.White)
                            }
                        }
                    }
                }

                // Center Play/Pause & +- 10s
                if (!showEpisodesDrawer && !showSubtitleDrawer && !isSpeedUp) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(40.dp)
                    ) {
                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Default.Replay10, "تأخير ١٠ ثوان", tint = Color.White, modifier = Modifier.size(40.dp))
                        }

                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                isPlaying = exoPlayer.isPlaying
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "تشغيل / إيقاف",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Default.Forward10, "تقديم ١٠ ثوان", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                    }
                }

                // Bottom Progress Bar
                if (!showEpisodesDrawer && !showSubtitleDrawer && !isSpeedUp) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTimeRange(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Text(formatTimeRange(totalDuration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(
                            value = if (isDraggingSlider) dragPosition else if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { percent ->
                                isDraggingSlider = true
                                dragPosition = percent
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                val newPos = (dragPosition * totalDuration).toLong()
                                exoPlayer.seekTo(newPos)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Subtitle Drawer Overlay
                AnimatedVisibility(
                    visible = showSubtitleDrawer,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(360.dp),
                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الترجمة والمظهر", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                                IconButton(onClick = { showSubtitleDrawer = false }) {
                                    Icon(Icons.Default.Close, "إغلاق")
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            // Controls for offset
                            if (parsedSubtitles.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("إعدادات الترجمة الحالية", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Y Offset Control
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("موضع الترجمة", style = MaterialTheme.typography.bodyMedium)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = { subtitleYOffset -= 1f }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.ArrowUpward, "أعلى")
                                                }
                                                Text("${(-subtitleYOffset).toInt()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                IconButton(onClick = { subtitleYOffset += 1f }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.ArrowDownward, "أسفل")
                                                }
                                            }
                                        }

                                        // Time Offset Control
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("تزامن الوقت", style = MaterialTheme.typography.bodyMedium)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = { subtitleTimeOffsetMs += 100L }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.Add, "تأخير")
                                                }
                                                Text("${subtitleTimeOffsetMs / 1000f}s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                IconButton(onClick = { subtitleTimeOffsetMs -= 100L }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.Remove, "تقديم")
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Fetch Subtitles logic
                            val scope = rememberCoroutineScope()
                            Button(
                                onClick = {
                                    scope.launch {
                                        isDownloadingSub = true
                                        val season = if (isTv) activeId.substringAfter("-s").substringBefore("-e").toIntOrNull() ?: 1 else 0
                                        val episode = if (isTv) activeId.substringAfter("-e").toIntOrNull() ?: 1 else 0
                                        searchSubsList = com.example.ui.viewmodel.SubtitleHelper.fetchSubtitles(parentTmdbId, isTv, season, episode, activeTitle)
                                        isDownloadingSub = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isDownloadingSub
                            ) {
                                if (isDownloadingSub) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("البحث عن ترجمات")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Subtitle List
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                searchSubsList?.let { subs ->
                                    if (subs.isEmpty()) {
                                        item {
                                            Text("لا توجد ترجمات متاحة.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                    } else {
                                        items(subs) { sub ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(sub.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(sub.lang, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    IconButton(onClick = {
                                                        scope.launch {
                                                            isDownloadingSub = true
                                                            val extracted = com.example.ui.viewmodel.SubtitleHelper.downloadAndExtractSubtitle(context, sub.url, activeId)
                                                            if (extracted != null) {
                                                                parsedSubtitles = com.example.ui.viewmodel.SubtitleParser.parseBlock(extracted)
                                                                showSubtitleDrawer = false
                                                            }
                                                            isDownloadingSub = false
                                                        }
                                                    }) {
                                                        Icon(Icons.Default.Download, "تحميل وتفعيل", tint = MaterialTheme.colorScheme.primary)
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

                // Episodes Drawer Overlay
                AnimatedVisibility(
                    visible = showEpisodesDrawer,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(340.dp),
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("الحلقات المحمّلة", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                                IconButton(onClick = { showEpisodesDrawer = false }) {
                                    Icon(Icons.Default.Close, "إغلاق")
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            if (seriesEpisodes.isEmpty()) {
                                Text("لا توجد حلقات محملة أخرى.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            } else {
                                // Group by season
                                val groupedBySeason = seriesEpisodes.groupBy { it.season }.toSortedMap()
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    groupedBySeason.forEach { (seasonNum, episodes) ->
                                        item {
                                            Text(
                                                text = "الموسم $seasonNum",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                        }
                                        items(episodes.sortedBy { it.episode }) { ep ->
                                            val isPlayingThis = ep.id == activeId
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isPlayingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                    .clickable {
                                                        activeId = ep.id
                                                        activeTitle = ep.title
                                                        activeLocalFilePath = ep.localFilePath
                                                        showEpisodesDrawer = false
                                                    }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isPlayingThis) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isPlayingThis) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                                    } else {
                                                        Text("${ep.episode}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = ep.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal),
                                                    color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
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
            }
        }
    }
}
}

private fun formatTimeRange(millis: Long): String {
    val totalSeconds = millis / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    val h = m / 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m % 60, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
