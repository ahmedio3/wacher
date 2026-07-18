package com.example.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.example.ui.components.DownloadedSubtitleBrowser
import com.example.ui.components.SubtitleBatchCard
import com.example.ui.components.SubtitleDownloadViewType
import com.example.ui.components.SubtitleSourceSheet
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.SubtitleHelper
import com.example.ui.viewmodel.SubtitleLine
import com.example.ui.viewmodel.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.roundToInt

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which value the unified center overlay is currently displaying. */
private enum class AdjustMode { VOLUME, BRIGHTNESS }

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
    var volumeFraction by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()) }
    val initialBrightness = activity?.window?.attributes?.screenBrightness?.let { if (it < 0f) 0.5f else it } ?: 0.5f
    var currentBrightness by remember { mutableFloatStateOf(initialBrightness) }
    // Unified volume/brightness overlay state
    // mode == null means "overlay not shown"; AdjustMode.VOLUME/BRIGHTNESS picks icon + bar fill source
    var showAdjustOverlay by remember { mutableStateOf(false) }
    var adjustMode by remember { mutableStateOf<AdjustMode?>(null) }
    // overlayHideTrigger increments on every pointer-up of a DRAG_VOLUME/DRAG_BRIGHTNESS gesture.
    // The LaunchedEffect keyed on it auto-cancels the prior coroutine and starts a fresh 2s countdown.
    var overlayHideTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(overlayHideTrigger) {
        if (overlayHideTrigger > 0) {
            delay(2000)
            showAdjustOverlay = false
        }
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
    var isDownloadingSub by remember { mutableStateOf(false) }
    var isSubtitleHidden by remember { mutableStateOf(false) }
    
    // Subtitle sources page
    var subtitlePage by remember { mutableIntStateOf(0) }
    
    // Folder navigation state for in-drawer file browser (page 6)
    var folderNavStack by remember { mutableStateOf<List<Uri>>(emptyList()) }

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

    // Log WATCHED activity once when the player screen is entered
    LaunchedEffect(Unit) {
        if (title.isNotBlank()) {
            viewModel.logActivity("WATCHED", title)
        }
    }

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
            val isContentUri = activeLocalFilePath.startsWith("content://")
            val file = if (isContentUri) null else File(activeLocalFilePath)
            if (isContentUri || (file != null && file.exists())) {
                val mediaItemBuilder = if (isContentUri) {
                    MediaItem.Builder().setUri(Uri.parse(activeLocalFilePath))
                } else {
                    MediaItem.Builder().setUri(Uri.fromFile(file!!))
                }

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
                    // First try standalone_subtitles/ (from auto-fetcher)
                    val standaloneDir = File(context.filesDir, "standalone_subtitles")
                    val autoFiles = standaloneDir.listFiles()?.filter {
                        it.name.startsWith(activeId) && (it.name.endsWith(".srt") || it.name.endsWith(".vtt"))
                    }
                    val autoSub = autoFiles?.firstOrNull()
                    if (autoSub != null && autoSub.exists()) {
                        parsedSubtitles = SubtitleParser.parseBlock(autoSub)
                        val playerExt = if (autoSub.name.endsWith(".vtt")) ".vtt" else ".srt"
                        autoSub.copyTo(File(context.filesDir, "downloads/$activeId$playerExt"), overwrite = true)
                    } else {
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
                    isClickable = false
                    isFocusable = false
                    subtitleView?.visibility = android.view.View.GONE
                    setOnTouchListener { _, _ -> false }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Unified centered Volume/Brightness Overlay Pill
        // - Centered horizontally, positioned near the top of the video (top padding 32dp)
        // - Dark semi-transparent capsule with leading icon + horizontal level bar + percentage
        // - Visibility tied to showAdjustOverlay; auto-hide is driven by overlayHideTrigger (FIX 1)
        AnimatedVisibility(
            visible = showAdjustOverlay && adjustMode != null,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
        ) {
            val mode = adjustMode
            if (mode != null) {
                val fraction = when (mode) {
                    AdjustMode.VOLUME -> volumeFraction
                    AdjustMode.BRIGHTNESS -> currentBrightness
                }.coerceIn(0f, 1f)
                val icon = when (mode) {
                    AdjustMode.VOLUME -> when {
                        fraction <= 0f -> Icons.Default.VolumeOff
                        fraction < 0.5f -> Icons.Default.VolumeDown
                        else -> Icons.Default.VolumeUp
                    }
                    AdjustMode.BRIGHTNESS -> Icons.Default.WbSunny
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    // Horizontal rounded level bar — Box-based track + filled portion
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when (mode) {
                                        AdjustMode.VOLUME -> MaterialTheme.colorScheme.primary
                                        AdjustMode.BRIGHTNESS -> Color.White.copy(alpha = 0.9f)
                                    }
                                )
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${(fraction * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Unified gesture handler: awaitEachGesture for tap, double-tap, long-press, volume/brightness drag
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)

                        // TASK 5 — Drawer guard: if either drawer is open, ignore this gesture entirely
                        if (showSubtitleDrawer || showEpisodesDrawer) {
                            down.consume()
                            return@awaitEachGesture
                        }

                        val startX = down.position.x
                        val startY = down.position.y
                        val startNanos = System.nanoTime()
                        val isRightSide = startX > size.width / 2f
                        val pointerId = down.id
                        val doubleTapTimeoutMs = 300L
                        val longPressMs = 400L

                        var gestureKind = "DOWN" // DOWN | LONG_PRESS | DRAG_VOLUME | DRAG_BRIGHTNESS
                        var lastY = startY
                        var volumeCommitNanos = 0L

                        // ─── Primary pointer tracking loop ──────────────────────────
                        while (true) {
                            // FIX 1: In DOWN state, race a 50ms timeout against awaitPointerEvent
                            // so long-press fires even with a completely motionless finger
                            val event = if (gestureKind == "DOWN") {
                                withTimeoutOrNull(50L) { awaitPointerEvent(PointerEventPass.Main) }
                            } else {
                                awaitPointerEvent(PointerEventPass.Main)
                            }

                            if (event == null) {
                                // Timeout occurred (only possible in DOWN state — motionless finger)
                                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                                if (elapsedMs > longPressMs) {
                                    gestureKind = "LONG_PRESS"
                                    exoPlayer.setPlaybackSpeed(2f)
                                    isSpeedUp = true
                                    showControls = false
                                    wasLongPress = true
                                }
                                continue
                            }

                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || change.isConsumed) continue

                            val isUp = !change.pressed
                            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                            val dy = change.position.y - startY
                            val dx = change.position.x - startX
                            val totalDist = sqrt(dy * dy + dx * dx)
                            val touchSlop = viewConfiguration.touchSlop

                            when (gestureKind) {
                                "DOWN" -> {
                                    if (isUp) {
                                        gestureKind = "TAP1"
                                        break
                                    }

                                    // Long-press: finger stayed still for ~400ms
                                    if (elapsedMs > longPressMs && totalDist < touchSlop) {
                                        gestureKind = "LONG_PRESS"
                                        exoPlayer.setPlaybackSpeed(2f)
                                        isSpeedUp = true
                                        showControls = false
                                        wasLongPress = true
                                    }
                                    // Vertical drag: moved beyond slop, vertical dominates
                                    else if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                                        gestureKind = if (isRightSide) "DRAG_VOLUME" else "DRAG_BRIGHTNESS"
                                        if (isRightSide) {
                                            adjustMode = AdjustMode.VOLUME
                                            showAdjustOverlay = true
                                            // FIX 2: resync volumeFraction from live AudioManager on drag entry
                                            volumeFraction = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()
                                        } else {
                                            adjustMode = AdjustMode.BRIGHTNESS
                                            showAdjustOverlay = true
                                        }
                                        lastY = change.position.y
                                        change.consume()
                                    }
                                }

                                "LONG_PRESS" -> {
                                    if (isUp) {
                                        exoPlayer.setPlaybackSpeed(1f)
                                        isSpeedUp = false
                                        wasLongPress = false
                                        break
                                    }
                                    // All movement ignored while locked in long-press
                                }

                                "DRAG_VOLUME" -> {
                                    if (isUp) {
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeFraction * maxVolume).roundToInt().coerceIn(0, maxVolume), 0)
                                        overlayHideTrigger++
                                        break
                                    }
                                    val dragDy = change.position.y - lastY
                                    if (abs(dragDy) > 0f) {
                                        lastY = change.position.y
                                        // FIX 2: continuous Float accumulator, no per-frame truncation
                                        volumeFraction = (volumeFraction - dragDy / size.height).coerceIn(0f, 1f)
                                        val newVol = (volumeFraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                        currentVolume = newVol

                                        // Throttle actual audioManager calls to ~80ms
                                        val now = System.nanoTime()
                                        if (now - volumeCommitNanos > 80_000_000) {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                            volumeCommitNanos = now
                                        }
                                        change.consume()
                                    }
                                }

                                "DRAG_BRIGHTNESS" -> {
                                    if (isUp) {
                                        overlayHideTrigger++
                                        break
                                    }
                                    val dragDy = change.position.y - lastY
                                    if (abs(dragDy) > 0f) {
                                        lastY = change.position.y
                                        val delta = -dragDy / size.height
                                        val newB = (currentBrightness + delta).coerceIn(0f, 1f)
                                        currentBrightness = newB
                                        activity?.window?.let { w ->
                                            w.attributes = w.attributes.apply { screenBrightness = newB }
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        }

                        // ─── After first pointer up: first-tap candidate ───────────
                        if (gestureKind != "TAP1") return@awaitEachGesture

                        // Wait for a possible second tap within the double-tap window
                        val secondDown = withTimeoutOrNull(doubleTapTimeoutMs) {
                            awaitFirstDown(requireUnconsumed = true)
                        }

                        if (secondDown != null) {
                            // ── Second tap arrived — track it like the first ──
                            if (showSubtitleDrawer || showEpisodesDrawer) {
                                secondDown.consume()
                                return@awaitEachGesture
                            }

                            val secondId = secondDown.id
                            val secondStartX = secondDown.position.x
                            val secondStartY = secondDown.position.y
                            val secondStartNanos = System.nanoTime()
                            var secondKind = "DOWN"
                            var secondLastY = secondStartY
                            var secondVolumeCommitNanos = 0L

                            while (true) {
                                // FIX 1: same polling pattern for second-tap DOWN state
                                val secondEvent = if (secondKind == "DOWN") {
                                    withTimeoutOrNull(50L) { awaitPointerEvent(PointerEventPass.Main) }
                                } else {
                                    awaitPointerEvent(PointerEventPass.Main)
                                }

                                if (secondEvent == null) {
                                    val elapsed2 = (System.nanoTime() - secondStartNanos) / 1_000_000L
                                    if (elapsed2 > longPressMs) {
                                        secondKind = "LONG_PRESS"
                                        exoPlayer.setPlaybackSpeed(2f)
                                        isSpeedUp = true
                                        showControls = false
                                        wasLongPress = true
                                    }
                                    continue
                                }

                                val change = secondEvent.changes.firstOrNull { it.id == secondId }
                                if (change == null || change.isConsumed) continue

                                val isUp2 = !change.pressed
                                val elapsed2 = (System.nanoTime() - secondStartNanos) / 1_000_000L
                                val dy2 = change.position.y - secondStartY
                                val dx2 = change.position.x - secondStartX
                                val dist2 = sqrt(dy2 * dy2 + dx2 * dx2)
                                val ts = viewConfiguration.touchSlop

                                when (secondKind) {
                                    "DOWN" -> {
                                        if (isUp2) {
                                            // Clean second tap → fire double-tap seek
                                            val tapX = change.position.x
                                            if (tapX > size.width / 2f) {
                                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                            } else {
                                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                            }
                                            break
                                        }

                                        if (elapsed2 > longPressMs && dist2 < ts) {
                                            secondKind = "LONG_PRESS"
                                            exoPlayer.setPlaybackSpeed(2f)
                                            isSpeedUp = true
                                            showControls = false
                                            wasLongPress = true
                                        } else if (abs(dy2) > ts && abs(dy2) > abs(dx2)) {
                                            val isSecondRight = secondStartX > size.width / 2f
                                            secondKind = if (isSecondRight) "DRAG_VOLUME" else "DRAG_BRIGHTNESS"
                                            if (isSecondRight) {
                                                adjustMode = AdjustMode.VOLUME
                                                showAdjustOverlay = true
                                                // FIX 2: resync volumeFraction on second-tap drag entry
                                                volumeFraction = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()
                                            } else {
                                                adjustMode = AdjustMode.BRIGHTNESS
                                                showAdjustOverlay = true
                                            }
                                            secondLastY = change.position.y
                                            change.consume()
                                        }
                                    }

                                    "LONG_PRESS" -> {
                                        if (isUp2) {
                                            exoPlayer.setPlaybackSpeed(1f)
                                            isSpeedUp = false
                                            wasLongPress = false
                                            break
                                        }
                                    }

                                    "DRAG_VOLUME" -> {
                                        if (isUp2) {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeFraction * maxVolume).roundToInt().coerceIn(0, maxVolume), 0)
                                            overlayHideTrigger++
                                            break
                                        }
                                        val dragDy2 = change.position.y - secondLastY
                                        if (abs(dragDy2) > 0f) {
                                            secondLastY = change.position.y
                                            // FIX 2: continuous Float accumulator
                                            volumeFraction = (volumeFraction - dragDy2 / size.height).coerceIn(0f, 1f)
                                            val newVol2 = (volumeFraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                            currentVolume = newVol2
                                            val now = System.nanoTime()
                                            if (now - secondVolumeCommitNanos > 80_000_000) {
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol2, 0)
                                                secondVolumeCommitNanos = now
                                            }
                                            change.consume()
                                        }
                                    }

                                    "DRAG_BRIGHTNESS" -> {
                                        if (isUp2) {
                                            overlayHideTrigger++
                                            break
                                        }
                                        val dragDy2 = change.position.y - secondLastY
                                        if (abs(dragDy2) > 0f) {
                                            secondLastY = change.position.y
                                            val delta = -dragDy2 / size.height
                                            val newB2 = (currentBrightness + delta).coerceIn(0f, 1f)
                                            currentBrightness = newB2
                                            activity?.window?.let { w ->
                                                w.attributes = w.attributes.apply { screenBrightness = newB2 }
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        } else {
                            // ── Second tap timeout → single tap ──
                            showControls = !showControls
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
                            .padding(bottom = 8.dp)
                    ) {
                        // Time indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTimeRange(currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Text(formatTimeRange(totalDuration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        // Custom thin seekbar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        if (totalDuration > 0) {
                                            val percent = (offset.x / this.size.width).coerceIn(0f, 1f)
                                            exoPlayer.seekTo((percent * totalDuration).toLong())
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            isDraggingSlider = true
                                            if (totalDuration > 0) {
                                                dragPosition = (offset.x / this.size.width).coerceIn(0f, 1f)
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            if (totalDuration > 0) {
                                                dragPosition = (change.position.x / this.size.width).coerceIn(0f, 1f)
                                            }
                                            change.consume()
                                        },
                                        onDragEnd = {
                                            isDraggingSlider = false
                                            if (totalDuration > 0) {
                                                val newPos = (dragPosition * totalDuration).toLong()
                                                exoPlayer.seekTo(newPos)
                                            }
                                        },
                                        onDragCancel = {
                                            isDraggingSlider = false
                                        }
                                    )
                                }
                        ) {
                            val progress = if (isDraggingSlider) dragPosition else if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                            val trackColor = MaterialTheme.colorScheme.primary
                            val inactiveColor = Color.White.copy(alpha = 0.3f)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val trackHeight = 3.dp.toPx()
                                val thumbRadius = 6.dp.toPx()
                                val centerY = size.height / 2
                                // Inactive track
                                drawRoundRect(
                                    color = inactiveColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2),
                                    size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
                                )
                                // Active track
                                val activeWidth = size.width * progress
                                if (activeWidth > 0f) {
                                    drawRoundRect(
                                        color = trackColor,
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2),
                                        size = androidx.compose.ui.geometry.Size(activeWidth, trackHeight),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
                                    )
                                }
                                // Thumb dot
                                val thumbX = activeWidth.coerceAtMost(size.width - thumbRadius).coerceAtLeast(thumbRadius)
                                drawCircle(
                                    color = trackColor,
                                    radius = thumbRadius,
                                    center = androidx.compose.ui.geometry.Offset(thumbX, centerY)
                                )
                            }
                            // Time preview on drag
                            if (isDraggingSlider) {
                                val previewPos = (dragPosition * totalDuration).toLong()
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-6).dp),
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
                    Box(Modifier.fillMaxSize()) {
                        // Scrim: tapping outside the Card closes the drawer
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showSubtitleDrawer = false
                                }
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(360.dp),
                            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            // Dynamic header with back button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (subtitlePage != 0) {
                                    IconButton(onClick = {
                                        if (subtitlePage == 6 && folderNavStack.size > 1) {
                                            // Pop one folder level in the file browser
                                            folderNavStack = folderNavStack.dropLast(1)
                                        } else {
                                            subtitlePage = 0
                                            folderNavStack = emptyList()
                                        }
                                    }) {
                                        Icon(Icons.Default.ArrowBack, "رجوع")
                                    }
                                }
                                    Text(
                                        when (subtitlePage) {
                                            0 -> "الترجمة"
                                            1 -> "بحث MovieBox"
                                            2 -> "Subdl"
                                            3 -> "OpenSubtitles"
                                            4 -> "الترجمات المحملة"
                                            5 -> "إدارة الباتشات"
                                            6 -> "متصفح الملفات"
                                            else -> "الترجمة"
                                        },
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { showSubtitleDrawer = false }) {
                                    Icon(Icons.Default.Close, "إغلاق")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Animated page switching
                            AnimatedContent(
                                targetState = subtitlePage,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        (slideInHorizontally { it } + fadeIn(animationSpec = tween(350)))
                                            .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut(animationSpec = tween(250)))
                                    } else {
                                        (slideInHorizontally { -it } + fadeIn(animationSpec = tween(350)))
                                            .togetherWith(slideOutHorizontally { it / 3 } + fadeOut(animationSpec = tween(250)))
                                    }
                                },
                                label = "sub_page",
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) { page ->
                                when (page) {
                                    // ===== PAGE 0: Main Controls (scrollable) =====
                                    0 -> {
                                        val mainScrollState = rememberScrollState()
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(mainScrollState)
                                        ) {
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
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                            Text(if (isSubtitleHidden) "الترجمة مخفية" else "إخفاء الترجمة", style = MaterialTheme.typography.bodyMedium)
                                                            IconButton(onClick = { isSubtitleHidden = !isSubtitleHidden }) {
                                                                Icon(if (isSubtitleHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                            Text("موضع الترجمة", style = MaterialTheme.typography.bodyMedium)
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                IconButton(onClick = { subtitleYOffset -= 10f }, modifier = Modifier.size(36.dp)) { Text("-10", fontSize = 10.sp) }
                                                                IconButton(onClick = { subtitleYOffset -= 1f }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowUpward, "أعلى") }
                                                                Text("${(-subtitleYOffset).toInt()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                                                IconButton(onClick = { subtitleYOffset += 1f }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowDownward, "أسفل") }
                                                                IconButton(onClick = { subtitleYOffset += 10f }, modifier = Modifier.size(36.dp)) { Text("+10", fontSize = 10.sp) }
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                            Text("تزامن الوقت", style = MaterialTheme.typography.bodyMedium)
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                IconButton(onClick = { subtitleTimeOffsetMs += 100L }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, "تأخير") }
                                                                Text("${subtitleTimeOffsetMs / 1000f}s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(50.dp), textAlign = TextAlign.Center)
                                                                IconButton(onClick = { subtitleTimeOffsetMs -= 100L }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Remove, "تقديم") }
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                            }

                                            // Navigation buttons section
                                            val scope = rememberCoroutineScope()

                                            // Row: MovieBox search + Download series
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { subtitlePage = 1 },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("بحث MovieBox", fontSize = 11.sp, maxLines = 1)
                                                }
                                                if (isTv) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            scope.launch {
                                                                isDownloadingSub = true
                                                                val allEpisodes = downloadsList.filter { it.mediaId == parentTmdbId && it.status == "completed" }
                                                                for (ep in allEpisodes) {
                                                                    try {
                                                                        val subs = SubtitleHelper.fetchSubtitles(parentTmdbId, true, ep.season, ep.episode, activeTitle.substringBefore(" - "))
                                                                        val arSub = subs.firstOrNull { it.lang.contains("AR", ignoreCase = true) } ?: subs.firstOrNull()
                                                                        if (arSub != null) SubtitleHelper.downloadAndExtractSubtitle(context, arSub.url, ep.id)
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
                                                            Text("تحميل المسلسل", fontSize = 11.sp, maxLines = 1)
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Local file picker — opens in-drawer file explorer (page 6)
                                            OutlinedButton(
                                                onClick = { subtitlePage = 6 },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("اختيار ملف ترجمة من الجهاز", fontSize = 12.sp)
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Divider with "مصادر إضافية" label
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
                                                Text(
                                                    "مصادر إضافية",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
                                            }

                                            // Subdl button → page 2
                                            OutlinedButton(
                                                onClick = { subtitlePage = 2 },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF4A90D9))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Subdl", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A90D9))
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // OpenSubtitles button → page 3
                                            OutlinedButton(
                                                onClick = { subtitlePage = 3 },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF7CB342))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("OpenSubtitles", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7CB342))
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // ترجمات محملة button → page 4
                                            OutlinedButton(
                                                onClick = { subtitlePage = 4 },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("الترجمات المحملة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // إدارة الباتشات button → page 5
                                            OutlinedButton(
                                                onClick = { subtitlePage = 5 },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("إدارة الباتشات", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }

                                    // ===== PAGES 1, 2, 3: SubtitleSourceSheet (shared component) =====
                                    1, 2, 3 -> {
                                        val seasonNum = if (isTv) activeId.substringAfter("-s").substringBefore("-e").toIntOrNull() ?: 1 else 0
                                        val episodeNum = if (isTv) activeId.substringAfter("-e").toIntOrNull() ?: 1 else 0

                                        SubtitleSourceSheet(
                                            tmdbId = parentTmdbId,
                                            isTv = isTv,
                                            season = seasonNum,
                                            episode = episodeNum,
                                            titleFallback = activeTitle,
                                            initialPage = page,
                                            onNavigateBack = { subtitlePage = 0 },
                                            onSubtitleLoaded = { file, _, _, _, _, _, _ ->
                                                parsedSubtitles = SubtitleParser.parseBlock(file)
                                                showSubtitleDrawer = false
                                            },
                                            customDownload = { downloadUrl: String ->
                                                val file = SubtitleHelper.downloadAndExtractSubtitle(context, downloadUrl, activeId)
                                                if (file != null) listOf(Pair(file, 0)) else emptyList()
                                            }
                                        )
                                    }

                                    // ===== PAGE 4: Downloaded subtitle browser (selection mode) =====
                                    4 -> {
                                        val batchGroups by viewModel.subtitleBatchGroups.collectAsState(initial = emptyList())
                                        DownloadedSubtitleBrowser(
                                            batchGroups = batchGroups,
                                            viewType = SubtitleDownloadViewType.PLAYER_SELECTION,
                                            onExportFile = { item ->
                                                try {
                                                    val srcFile = File(item.localFilePath)
                                                    if (srcFile.exists()) {
                                                        val cacheFile = File(context.cacheDir, "exported_${item.id}.srt")
                                                        srcFile.copyTo(cacheFile, overwrite = true)
                                                        Toast.makeText(context, "تم نسخ الترجمة", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (_: Exception) { }
                                            },
                                            onDeleteFile = { item ->
                                                viewModel.deleteSubtitleDownload(item.id)
                                            },
                                            onSelectActive = { item ->
                                                val file = File(item.localFilePath)
                                                if (file.exists()) {
                                                    parsedSubtitles = SubtitleParser.parseBlock(file)
                                                    showSubtitleDrawer = false
                                                    subtitlePage = 0
                                                    Toast.makeText(context, "تم تفعيل ترجمة ${item.language}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            activeFilePath = null
                                        )
                                    }

                                    // ===== PAGE 5: Batch management overview =====
                                    5 -> {
                                        val batchGroups by viewModel.subtitleBatchGroups.collectAsState(initial = emptyList())
                                        val scrollState = rememberScrollState()
                                        Column(
                                            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (batchGroups.isEmpty()) {
                                                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                                    Text("لا توجد باتشات", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                                }
                                            } else {
                                                batchGroups.forEach { batch ->
                                                    SubtitleBatchCard(
                                                        batchGroup = batch,
                                                        expanded = false,
                                                        onToggle = { },
                                                        onExportAll = {
                                                            batch.items.forEach { item ->
                                                                try {
                                                                    val srcFile = File(item.localFilePath)
                                                                    if (srcFile.exists()) {
                                                                        val cacheFile = File(context.cacheDir, "exported_${item.id}.srt")
                                                                        srcFile.copyTo(cacheFile, overwrite = true)
                                                                    }
                                                                } catch (_: Exception) { }
                                                            }
                                                            Toast.makeText(context, "تم نسخ ${batch.items.size} ترجمة", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onDeleteAll = {
                                                            batch.items.forEach { viewModel.deleteSubtitleDownload(it.id) }
                                                        },
                                                        onExportOne = { index ->
                                                            val item = batch.items[index]
                                                            try {
                                                                val srcFile = File(item.localFilePath)
                                                                if (srcFile.exists()) {
                                                                    val cacheFile = File(context.cacheDir, "exported_${item.id}.srt")
                                                                    srcFile.copyTo(cacheFile, overwrite = true)
                                                                    Toast.makeText(context, "تم نسخ الترجمة", Toast.LENGTH_SHORT).show()
                                                                }
                                                            } catch (_: Exception) { }
                                                        },
                                                        onDeleteOne = { index ->
                                                            viewModel.deleteSubtitleDownload(batch.items[index].id)
                                                        },
                                                        onClickOne = { index ->
                                                            val item = batch.items[index]
                                                            val file = File(item.localFilePath)
                                                            if (file.exists()) {
                                                                parsedSubtitles = SubtitleParser.parseBlock(file)
                                                                showSubtitleDrawer = false
                                                                subtitlePage = 0
                                                            }
                                                        }
                                                    )
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = "المجموع: ${batchGroups.sumOf { it.items.size }} ترجمة",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                            }
                                        }
                                    }

                                    // ===== PAGE 6: In-drawer subtitle file browser (SAF-based) =====
                                    6 -> {
                                        val scope = rememberCoroutineScope()
                                        val treeUriString = remember {
                                            prefs.getString("subtitle_tree_uri", null)
                                        }
                                        var treeUri by remember { mutableStateOf(treeUriString?.let { Uri.parse(it) }) }

                                        // SAF tree-grant launcher (one-time setup)
                                        val treeLauncher = rememberLauncherForActivityResult(
                                            contract = ActivityResultContracts.OpenDocumentTree()
                                        ) { uri ->
                                            if (uri != null) {
                                                try {
                                                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                                                    prefs.edit().putString("subtitle_tree_uri", uri.toString()).apply()
                                                    treeUri = uri
                                                    folderNavStack = listOf(uri)
                                                } catch (_: Exception) { }
                                            }
                                        }

                                        // Launcher for back-up: pick any folder as an alternative
                                        val pickFolderLauncher = rememberLauncherForActivityResult(
                                            contract = ActivityResultContracts.OpenDocumentTree()
                                        ) { uri ->
                                            if (uri != null) {
                                                try {
                                                    val takeP = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                                    context.contentResolver.takePersistableUriPermission(uri, takeP)
                                                    prefs.edit().putString("subtitle_tree_uri", uri.toString()).apply()
                                                    treeUri = uri
                                                    folderNavStack = listOf(uri)
                                                } catch (_: Exception) { }
                                            }
                                        }

                                        if (treeUri == null) {
                                            // No tree granted yet — show grant prompt
                                            Column(
                                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(48.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.height(12.dp))
                                                Text(
                                                    "للوصول إلى ملفات الترجمة على جهازك، يرجى منح الإذن بالوصول إلى المجلد الجذر",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(Modifier.height(16.dp))
                                                OutlinedButton(
                                                    onClick = { treeLauncher.launch(null) },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("منح الوصول إلى المجلد")
                                                }
                                            }
                                        } else {
                                            // Determine current DocumentFile from folderNavStack
                                            val currentFolderUri = folderNavStack.lastOrNull() ?: treeUri
                                            val currentFolder = remember(currentFolderUri) {
                                                currentFolderUri?.let { DocumentFile.fromTreeUri(context, it) }
                                            }

                                            if (currentFolder == null || !currentFolder.exists()) {
                                                Text(
                                                    "تعذر الوصول إلى المجلد. الرجاء منح الوصول مرة أخرى.",
                                                    modifier = Modifier.padding(16.dp),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                OutlinedButton(
                                                    onClick = { pickFolderLauncher.launch(null) },
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                                ) {
                                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("إعادة منح الوصول")
                                                }
                                            } else {
                                                val entries = remember(currentFolderUri, folderNavStack) {
                                                    currentFolder.listFiles()
                                                        .filter { entry ->
                                                            entry.isDirectory ||
                                                                entry.name?.let { name ->
                                                                    val lower = name.lowercase()
                                                                    lower.endsWith(".srt") || lower.endsWith(".ass") || lower.endsWith(".ssa") || lower.endsWith(".vtt")
                                                                } == true
                                                        }
                                                        .sortedBy { entry ->
                                                            // Folders first (0), then files (1), sorted by name
                                                            val prefix = if (entry.isDirectory) "0_" else "1_"
                                                            prefix + (entry.name?.lowercase() ?: "")
                                                        }
                                                }

                                                LazyColumn(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    items(entries) { entry ->
                                                        val isDir = entry.isDirectory()
                                                        val fileName = entry.name ?: "(بدون اسم)"
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable(
                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                    indication = null,
                                                                    onClick = {
                                                                        if (isDir) {
                                                                            folderNavStack = folderNavStack + entry.uri
                                                                        }
                                                                    }
                                                                )
                                                                .padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isDir) Icons.Default.Folder else Icons.Default.Description,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(20.dp),
                                                                tint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(
                                                                text = fileName,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            if (!isDir) {
                                                                TextButton(
                                                                    onClick = {
                                                                        scope.launch {
                                                                            try {
                                                                                val originalName = entry.name ?: "subtitle.srt"
                                                                                val ext = originalName.substringAfterLast('.')
                                                                                val tempFile = File(context.cacheDir, "browser_sub.$ext")
                                                                                context.contentResolver.openInputStream(entry.uri)?.use { input ->
                                                                                    tempFile.outputStream().use { output -> input.copyTo(output) }
                                                                                }
                                                                                parsedSubtitles = SubtitleParser.parseBlock(tempFile)
                                                                                if (parsedSubtitles.isNotEmpty()) {
                                                                                    showSubtitleDrawer = false
                                                                                    subtitlePage = 0
                                                                                }
                                                                            } catch (_: Exception) { }
                                                                        }
                                                                    }
                                                                ) {
                                                                    Text("اختيار", fontSize = 11.sp)
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Empty state
                                                    if (entries.isEmpty()) {
                                                        item {
                                                            Box(
                                                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    "لا توجد ملفات ترجمة (.srt/.ass/.vtt) في هذا المجلد",
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                                    textAlign = TextAlign.Center
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

                // ===== EPISODES DRAWER =====
                AnimatedVisibility(
                    visible = showEpisodesDrawer,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        // Scrim: tapping outside the Card closes the drawer
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showEpisodesDrawer = false
                                }
                        )
                        Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(340.dp)
                            .align(Alignment.TopEnd),
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    ) {
                        val episodesListState = rememberLazyListState()
                        // Ascending order episodes
                        val sortedEps = remember(seriesEpisodes) {
                            seriesEpisodes.sortedBy { it.episode }
                        }
                        // Find current episode index for auto-scroll
                        val currentEpIndex = remember(seriesEpisodes, activeId) {
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

// ===== Subtitle Sources Helper Composables =====
@Composable
fun SectionHeader(title: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(
                "${count} ترجمة",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SourceSubtitleCard(
    item: SubtitleHelper.SubtitleItem,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when (item.source) {
                            "Subdl" -> Color(0xFF4A90D9).copy(alpha = 0.15f)
                            "OpenSubtitles" -> Color(0xFF7CB342).copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    item.source,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (item.source) {
                        "Subdl" -> Color(0xFF4A90D9)
                        "OpenSubtitles" -> Color(0xFF7CB342)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.lang,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                    if (item.downloadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "${item.downloadCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Download, "تحميل", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
