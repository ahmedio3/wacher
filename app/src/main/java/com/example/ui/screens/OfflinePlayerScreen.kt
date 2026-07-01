package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.MainActivity
import com.example.data.local.DownloadEntity
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.SubtitleHelper
import com.example.ui.viewmodel.SubtitleLine
import com.example.ui.viewmodel.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // --- Pause video when app goes to background ---
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Will be handled by exoPlayer reference below
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    // Volume & Brightness Controls
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val initialBrightness = activity?.window?.attributes?.screenBrightness?.let { if (it < 0f) 0.5f else it } ?: 0.5f
    var currentBrightness by remember { mutableFloatStateOf(initialBrightness) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }

    // Separate auto-hide timers for volume & brightness
    LaunchedEffect(showVolumeOverlay) {
        if (showVolumeOverlay) { delay(1500); showVolumeOverlay = false }
    }
    LaunchedEffect(showBrightnessOverlay) {
        if (showBrightnessOverlay) { delay(1500); showBrightnessOverlay = false }
    }

    // Overlays
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var showSubtitleDrawer by remember { mutableStateOf(false) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    // Subtitle Custom Variables
    var subtitleYOffset by remember { mutableFloatStateOf(prefs.getFloat("sub_y", -35f)) }
    var subtitleSize by remember { mutableFloatStateOf(prefs.getFloat("sub_size", 20f)) }
    var subtitleTimeOffsetMs by remember { mutableLongStateOf(0L) }
    var parsedSubtitles by remember { mutableStateOf<List<SubtitleLine>>(emptyList()) }
    var activeSubtitleText by remember { mutableStateOf("") }
    var searchSubsList by remember { mutableStateOf<List<SubtitleHelper.SubtitleItem>?>(null) }
    var isDownloadingSub by remember { mutableStateOf(false) }
    var isSubtitleHidden by remember { mutableStateOf(false) }

    // Subtitle status info text
    val subtitleStatusText = remember(parsedSubtitles, activeId, context) {
        val episodeSubFile = File(context.filesDir, "downloads/$activeId.srt")
        val episodeVttFile = File(context.filesDir, "downloads/$activeId.vtt")
        val episodeExists = episodeSubFile.exists() || episodeVttFile.exists()

        // Check if full series subtitle folder exists
        val baseIdOnly = activeId.substringBefore("-s")
        val seriesFolder = File(context.filesDir, "downloads")
        val seriesSubFiles = seriesFolder.listFiles()?.filter {
            it.name.startsWith(baseIdOnly) && (it.name.endsWith(".srt") || it.name.endsWith(".vtt"))
        } ?: emptyList()
        val seriesSubCount = seriesSubFiles.size

        buildString {
            if (parsedSubtitles.isNotEmpty() && episodeExists) {
                append("✓ ترجمة هذه الحلقة محملة")
                if (seriesSubCount > 1) {
                    append(" (+$seriesSubCount ترجمات للمسلسل)")
                }
            } else if (seriesSubCount > 0) {
                append("✓ تم تحميل $seriesSubCount ترجمة للمسلسل (ليس لهذه الحلقة)")
            } else {
                append("لم يتم تحميل ترجمة بعد")
            }
        }
    }

    // Update active subtitle
    LaunchedEffect(currentPosition, subtitleTimeOffsetMs, parsedSubtitles) {
        val effectiveTime = currentPosition + subtitleTimeOffsetMs
        val currentSub = parsedSubtitles.find { effectiveTime in it.startTime..it.endTime }
        activeSubtitleText = currentSub?.text ?: ""
    }

    // Save subtitle position & size when changed
    LaunchedEffect(subtitleYOffset) {
        prefs.edit().putFloat("sub_y", subtitleYOffset).apply()
    }
    LaunchedEffect(subtitleSize) {
        prefs.edit().putFloat("sub_size", subtitleSize).apply()
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying, showEpisodesDrawer, showSubtitleDrawer, isDraggingSlider) {
        if (showControls && isPlaying && !showEpisodesDrawer && !showSubtitleDrawer && !isDraggingSlider) {
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

    // --- Battery & Clock state ---
    var batteryLevel by remember { mutableIntStateOf(0) }
    var currentTimeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            // Battery
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, intentFilter)
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            batteryLevel = (level * 100 / scale)

            // Time
            currentTimeText = timeFormat.format(Date())

            delay(30_000) // Update every 30s
        }
    }

    // Setup ExoPlayer
    val exoPlayer = remember {
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractorsFactory)
        ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build().apply {
            playWhenReady = true
        }
    }

    // --- Pause on app background via lifecycle ---
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (exoPlayer.isPlaying) exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                    parsedSubtitles = SubtitleParser.parseBlock(srtFile)
                } else if (vttFile.exists()) {
                    parsedSubtitles = SubtitleParser.parseBlock(vttFile)
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

                // Auto-download Arabic subtitle if not already present
                if (parsedSubtitles.isEmpty()) {
                    launch(Dispatchers.IO) {
                        try {
                            val tmdbIdStr = if (isTv) activeId.substringBefore("-s") else activeId
                            val seasonSub = if (isTv) activeId.substringAfter("-s").substringBefore("-e").toIntOrNull() ?: 1 else 0
                            val episodeSub = if (isTv) activeId.substringAfter("-e").toIntOrNull() ?: 1 else 0
                            val subs = SubtitleHelper.fetchSubtitles(
                                tmdbIdStr, isTv, seasonSub, episodeSub, activeTitle.substringBefore(" - ")
                            )
                            val arSub = subs.firstOrNull { it.lang.contains("AR", ignoreCase = true) } ?: subs.firstOrNull()
                            if (arSub != null) {
                                val extracted = SubtitleHelper.downloadAndExtractSubtitle(context, arSub.url, activeId)
                                if (extracted != null) {
                                    parsedSubtitles = SubtitleParser.parseBlock(extracted)
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }

                // Update position periodically with higher frequency for smooth subtitles
                while (true) {
                    currentPosition = exoPlayer.currentPosition
                    val dur = exoPlayer.duration
                    if (dur > 0) totalDuration = dur
                    isPlaying = exoPlayer.isPlaying
                    // Save position periodically (every 5 seconds)
                    if (currentPosition > 0 && currentPosition % 5000 < 50) {
                        prefs.edit().putLong("pos_$activeId", currentPosition).apply()
                    }
                    delay(50)
                }
            }
        }
    }

    // Sync playback speed
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
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

        // Gesture zone: Tap to toggle controls + double-tap to seek
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(showSubtitleDrawer, showEpisodesDrawer) {
                    detectTapGestures(
                        onTap = {
                            if (!wasLongPress) {
                                showControls = !showControls
                            }
                            wasLongPress = false
                        },
                        onDoubleTap = { offset ->
                            if (!showSubtitleDrawer && !showEpisodesDrawer) {
                                val width = this.size.width
                                if (offset.x > width / 2) {
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                } else {
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                }
                            }
                        }
                    )
                }
                .pointerInput(isDraggingSlider, showSubtitleDrawer, showEpisodesDrawer) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Skip if slider is being dragged or drawer is open
                        if (isDraggingSlider || showSubtitleDrawer || showEpisodesDrawer) {
                            down.consume()
                            return@awaitEachGesture
                        }

                        var activated = false

                        // Launch timer coroutine for long-press 2x activation
                        val timerJob = launch {
                            delay(400) // Activate after 400ms hold
                            activated = true
                            wasLongPress = true
                            exoPlayer.setPlaybackSpeed(2f)
                            isSpeedUp = true
                            showControls = false
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.changes.all { !it.pressed }) break
                                // Once activated, keep 2x active while finger moves
                            }
                        } finally {
                            timerJob.cancel()
                            if (activated || isSpeedUp) {
                                exoPlayer.setPlaybackSpeed(1f)
                                isSpeedUp = false
                                wasLongPress = false
                            }
                        }
                    }
                }
        )

        // Volume & Brightness Overlay Indicators
        if (showVolumeOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .width(48.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                val volFraction = currentVolume.toFloat() / maxVolume.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(volFraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "${(volFraction * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        if (showBrightnessOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
                    .width(48.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(currentBrightness.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                )
                Text(
                    text = "${(currentBrightness * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Gesture zones for Volume (right) & Brightness (left)
        var volumeGestureZoneHeight by remember { mutableFloatStateOf(1f) }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.35f)
                .align(Alignment.CenterEnd)
                .onSizeChanged { volumeGestureZoneHeight = it.height.coerceAtLeast(1).toFloat() }
                .pointerInput(volumeGestureZoneHeight, showSubtitleDrawer, showEpisodesDrawer) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        // Don't adjust volume when drawers are open
                        if (showSubtitleDrawer || showEpisodesDrawer) return@detectVerticalDragGestures
                        val delta = -dragAmount / volumeGestureZoneHeight
                        val newVol = (currentVolume + delta * maxVolume).toInt().coerceIn(0, maxVolume)
                        if (newVol != currentVolume) {
                            currentVolume = newVol
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            showVolumeOverlay = true
                        }
                    }
                }
        )
        var brightnessGestureZoneHeight by remember { mutableFloatStateOf(1f) }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.35f)
                .align(Alignment.CenterStart)
                .onSizeChanged { brightnessGestureZoneHeight = it.height.coerceAtLeast(1).toFloat() }
                .pointerInput(brightnessGestureZoneHeight, showSubtitleDrawer, showEpisodesDrawer) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (showSubtitleDrawer || showEpisodesDrawer) return@detectVerticalDragGestures
                        val delta = -dragAmount / brightnessGestureZoneHeight
                        val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                        if (newBrightness != currentBrightness) {
                            currentBrightness = newBrightness
                            activity?.window?.let { win ->
                                val lp = win.attributes
                                lp.screenBrightness = newBrightness
                                win.attributes = lp
                            }
                            showBrightnessOverlay = true
                        }
                    }
                }
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
        if (!isSubtitleHidden && activeSubtitleText.isNotEmpty()) {
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
                        textAlign = TextAlign.Center,
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
                            // Home button (exit to home screen, keep app alive)
                            IconButton(
                                onClick = {
                                    exoPlayer.pause()
                                    activity?.moveTaskToBack(true)
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Icon(Icons.Default.Home, contentDescription = "الخروج إلى الشاشة الرئيسية", tint = Color.White)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Active Downloads button
                            val activeDlCount = downloadsList.count { it.status == "downloading" || it.status == "queued" }
                            if (activeDlCount > 0) {
                                var showActiveDlSheet by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { showActiveDlSheet = true },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                                ) {
                                    Icon(Icons.Default.ArrowCircleDown, contentDescription = "التحميلات النشطة", tint = MaterialTheme.colorScheme.tertiary)
                                }

                                if (showActiveDlSheet) {
                                    ModalBottomSheet(
                                        onDismissRequest = { showActiveDlSheet = false },
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("التحميلات النشطة", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
                                            val activeDls = downloadsList.filter { it.status == "downloading" || it.status == "queued" || it.status == "paused" }
                                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(activeDls) { dl ->
                                                    DownloadItemRow(item = dl, viewModel = viewModel, onPlayClick = {})
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))
                            }

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

                // Bottom Progress Bar + Controls Row + Battery/Clock
                if (!showEpisodesDrawer && !showSubtitleDrawer && !isSpeedUp) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 24.dp)
                    ) {
                        // Time indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTimeRange(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Text(formatTimeRange(totalDuration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        // Slider
                        Box(modifier = Modifier.fillMaxWidth()) {
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
                            if (isDraggingSlider) {
                                val previewPos = (dragPosition * totalDuration).toLong()
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-8).dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                ) {
                                    Text(
                                        text = formatTimeRange(previewPos),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bottom row: controls (left) + battery & clock (right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left side: play/pause + next episode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // Play/Pause
                                IconButton(
                                    onClick = {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        isPlaying = exoPlayer.isPlaying
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "تشغيل / إيقاف",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // Next episode (only for TV series)
                                if (isTv && seriesEpisodes.isNotEmpty()) {
                                    val currentEpisodeNum = activeId.substringAfter("-e").toIntOrNull() ?: 0
                                    val nextEp = seriesEpisodes
                                        .filter { it.episode > currentEpisodeNum && it.season == (activeId.substringAfter("-s").substringBefore("-e").toIntOrNull() ?: 1) }
                                        .minByOrNull { it.episode }
                                    if (nextEp != null) {
                                        IconButton(
                                            onClick = {
                                                prefs.edit().putLong("pos_$activeId", exoPlayer.currentPosition).apply()
                                                activeId = nextEp.id
                                                activeTitle = nextEp.title
                                                activeLocalFilePath = nextEp.localFilePath
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SkipNext,
                                                contentDescription = "الحلقة التالية",
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Right side: battery + clock
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Battery
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when {
                                            batteryLevel > 80 -> Icons.Default.BatteryFull
                                            batteryLevel > 50 -> Icons.Default.BatteryStd
                                            batteryLevel > 20 -> Icons.Default.Battery3Bar
                                            else -> Icons.Default.BatteryAlert
                                        },
                                        contentDescription = "البطارية",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$batteryLevel%",
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                                // Clock
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = currentTimeText,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== SUBTITLE DRAWER =====
                AnimatedVisibility(
                    visible = showSubtitleDrawer,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(360.dp)
                            .pointerInput(Unit) {
                                // Consume all touch events so they don't pass to video
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        ev.changes.forEach { it.consume() }
                                        if (ev.changes.all { !it.pressed }) break
                                    }
                                }
                            },
                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            // Title + close button inline (no gap)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "الترجمة",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { showSubtitleDrawer = false }) {
                                    Icon(Icons.Default.Close, "إغلاق")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Subtitle status note
                            Text(
                                text = subtitleStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Controls for offset (only when subtitles loaded)
                            if (parsedSubtitles.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        // Subtitle Hide Toggle
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                if (isSubtitleHidden) "الترجمة مخفية" else "إخفاء الترجمة",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            IconButton(onClick = { isSubtitleHidden = !isSubtitleHidden }) {
                                                Icon(
                                                    if (isSubtitleHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (isSubtitleHidden) "إظهار الترجمة" else "إخفاء الترجمة"
                                                )
                                            }
                                        }

                                        // Subtitle Position (directly under hide toggle)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("موضع الترجمة", style = MaterialTheme.typography.bodyMedium)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = { subtitleYOffset -= 10f }, modifier = Modifier.size(36.dp)) {
                                                    Text("-10", fontSize = 10.sp)
                                                }
                                                IconButton(onClick = { subtitleYOffset -= 1f }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.ArrowUpward, "أعلى")
                                                }
                                                Text("${(-subtitleYOffset).toInt()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                                IconButton(onClick = { subtitleYOffset += 1f }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.ArrowDownward, "أسفل")
                                                }
                                                IconButton(onClick = { subtitleYOffset += 10f }, modifier = Modifier.size(36.dp)) {
                                                    Text("+10", fontSize = 10.sp)
                                                }
                                            }
                                        }

                                        // Time Sync
                                        Spacer(modifier = Modifier.height(4.dp))
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
                                                Text("${subtitleTimeOffsetMs / 1000f}s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(50.dp), textAlign = TextAlign.Center)
                                                IconButton(onClick = { subtitleTimeOffsetMs -= 100L }, modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.Remove, "تقديم")
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Search + Full Series Download in one Row (half each)
                            val scope = rememberCoroutineScope()
                            if (parsedSubtitles.isNotEmpty() || true) { // Always show buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Search button - half width
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isDownloadingSub = true
                                                val season = if (isTv) activeId.substringAfter("-s").substringBefore("-e").toIntOrNull() ?: 1 else 0
                                                val episode = if (isTv) activeId.substringAfter("-e").toIntOrNull() ?: 1 else 0
                                                searchSubsList = SubtitleHelper.fetchSubtitles(parentTmdbId, isTv, season, episode, activeTitle)
                                                isDownloadingSub = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isDownloadingSub
                                    ) {
                                        if (isDownloadingSub) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("بحث", fontSize = 13.sp)
                                        }
                                    }

                                    // Download series subtitles button - half width
                                    if (isTv) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    isDownloadingSub = true
                                                    val allEpisodes = downloadsList.filter { it.mediaId == parentTmdbId && it.status == "completed" }
                                                    for (ep in allEpisodes) {
                                                        try {
                                                            val subs = SubtitleHelper.fetchSubtitles(
                                                                parentTmdbId, true, ep.season, ep.episode, activeTitle.substringBefore(" - ")
                                                            )
                                                            val arSub = subs.firstOrNull { it.lang.contains("AR", ignoreCase = true) } ?: subs.firstOrNull()
                                                            if (arSub != null) {
                                                                SubtitleHelper.downloadAndExtractSubtitle(context, arSub.url, ep.id)
                                                            }
                                                        } catch (_: Exception) { }
                                                    }
                                                    isDownloadingSub = false
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = !isDownloadingSub
                                        ) {
                                            if (isDownloadingSub) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("تحميل المسلسل", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Subtitle Search Results List
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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
                                                            val extracted = SubtitleHelper.downloadAndExtractSubtitle(context, sub.url, activeId)
                                                            if (extracted != null) {
                                                                parsedSubtitles = SubtitleParser.parseBlock(extracted)
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

                // ===== EPISODES DRAWER =====
                AnimatedVisibility(
                    visible = showEpisodesDrawer,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(340.dp)
                            .pointerInput(Unit) {
                                // Consume all touch events so they don't pass to video
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        ev.changes.forEach { it.consume() }
                                        if (ev.changes.all { !it.pressed }) break
                                    }
                                }
                            },
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    ) {
                        val episodesListState = rememberLazyListState()
                        val groupedBySeason = remember(seriesEpisodes) {
                            seriesEpisodes.groupBy { it.season }.toSortedMap()
                        }
                        // Find current episode index for auto-scroll
                        val currentEpIndex = remember(seriesEpisodes, activeId) {
                            val sortedEps = seriesEpisodes.sortedBy { it.episode }
                            sortedEps.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
                        }

                        LaunchedEffect(showEpisodesDrawer, currentEpIndex) {
                            if (showEpisodesDrawer && currentEpIndex > 0) {
                                delay(200)
                                episodesListState.animateScrollToItem(currentEpIndex)
                            }
                        }

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
                                LazyColumn(
                                    state = episodesListState,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Ascending order episodes
                                    val sortedEps = remember(seriesEpisodes) {
                                        seriesEpisodes.sortedBy { it.episode }
                                    }
                                    items(sortedEps, key = { it.id }) { ep ->
                                        val isPlayingThis = ep.id == activeId
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isPlayingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .clickable {
                                                    prefs.edit().putLong("pos_$activeId", exoPlayer.currentPosition).apply()
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
                                                text = "الحلقة ${ep.episode}",
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
