package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.ui.components.bouncyOverscroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.SubtitleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaId: String,
    title: String,
    localFilePath: String,
    viewModel: MovieViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }

    val activeId = mediaId
    val isTv = activeId.contains("-s")
    val baseId = if (isTv) activeId.substringBefore("-s") else activeId
    val startSeason = if (isTv) activeId.substringAfter("-s").substringBefore("-e").toIntOrNull() ?: 1 else 0
    val startEpisode = if (isTv) activeId.substringAfter("-e").toIntOrNull() ?: 1 else 0

    val downloadsList by viewModel.downloads.collectAsState(initial = emptyList())
    val activeDownloadedFile = remember(activeId, downloadsList) {
        downloadsList.find { it.id == activeId && it.status == "completed" }?.localFilePath ?: ""
    }
    val isLocalActive = activeDownloadedFile.isNotEmpty() && File(activeDownloadedFile).exists()

    var resolvedUrl by remember { mutableStateOf("") }
    var isLoadingUrl by remember { mutableStateOf(true) }
    var activeSubtitles by remember { mutableStateOf<List<SubtitleHelper.SubtitleItem>>(emptyList()) }

    // Log WATCHED activity once when the player screen is entered
    LaunchedEffect(Unit) {
        if (title.isNotBlank()) {
            viewModel.logActivity("WATCHED", title)
        }
    }

    // --- Resolve URL + Subtitles (unchanged logic) ---
    LaunchedEffect(activeId, activeDownloadedFile) {
        if (isLocalActive) {
            resolvedUrl = activeDownloadedFile
            val localSubFileVtt = File(context.filesDir, "downloads/$activeId.vtt")
            val localSubFileSrt = File(context.filesDir, "downloads/$activeId.srt")
            if (localSubFileVtt.exists() || localSubFileSrt.exists()) {
                val existsFile = if (localSubFileVtt.exists()) localSubFileVtt else localSubFileSrt
                activeSubtitles = listOf(SubtitleHelper.SubtitleItem(name = "العربية (محلي)", url = existsFile.absolutePath, lang = "ar", langCode = "ar"))
            } else {
                activeSubtitles = emptyList()
            }
            isLoadingUrl = false
        } else {
            isLoadingUrl = true
            try {
                val isTmdb = baseId.length < 10 && baseId.toLongOrNull() != null
                val subjectId: String
                if (isTmdb) {
                    val searchTypeFallback = if (isTv) "series" else "movie"
                    val searchRes = viewModel.movieBoxRepository.search(query = title.split("-").first().trim().replace("\\s*\\([^)]*\\)\\s*".toRegex(), ""))
                    val results = searchRes.getOrNull() ?: emptyList()
                    val match = results.firstOrNull { it.type == searchTypeFallback }
                    if (match == null) throw Exception("Not found on MovieBox API")
                    subjectId = match.subjectId
                } else {
                    subjectId = baseId
                }

                val linksRes = viewModel.movieBoxRepository.getDownloadLinks(subjectId)
                var videoList = linksRes.getOrNull() ?: emptyList()
                if (isTv) {
                    videoList = videoList.filter { it.season == startSeason && it.episode == startEpisode }
                }

                val topQuality = videoList.maxByOrNull { it.resolution } ?: videoList.firstOrNull()
                if (topQuality != null) {
                    resolvedUrl = topQuality.url
                    val movieBoxSubs = mutableListOf<SubtitleHelper.SubtitleItem>()
                    if (topQuality.hasArabicSubtitle && topQuality.arabicSubtitleUrl != null) {
                        movieBoxSubs.add(SubtitleHelper.SubtitleItem("العربية (MovieBox)", topQuality.arabicSubtitleUrl, "ar", "ar"))
                    } else if (topQuality.allSubtitles.isNotEmpty()) {
                        val arSub = topQuality.allSubtitles.find { it.languageCode.equals("ar", true) || it.languageName.contains("Arabic", true) }
                        if (arSub != null) {
                            movieBoxSubs.add(SubtitleHelper.SubtitleItem("العربية (MovieBox)", arSub.url, "ar", "ar"))
                        }
                        topQuality.allSubtitles.forEach { s ->
                            movieBoxSubs.add(SubtitleHelper.SubtitleItem(s.languageName + " (MovieBox)", s.url, s.languageCode, s.languageCode))
                        }
                    } else if (topQuality.resourceId.isNotEmpty()) {
                        val subRes = viewModel.movieBoxRepository.getSubtitles(subjectId, topQuality.resourceId).getOrNull()
                        if (subRes != null) {
                            if (subRes.hasArabic && subRes.arabicSubtitle != null) {
                                movieBoxSubs.add(SubtitleHelper.SubtitleItem("العربية (MovieBox)", subRes.arabicSubtitle.url, "ar", "ar"))
                            }
                            subRes.allSubtitles.forEach { s ->
                                movieBoxSubs.add(SubtitleHelper.SubtitleItem(s.languageName + " (MovieBox)", s.url, s.languageCode, s.languageCode))
                            }
                        }
                    }
                    activeSubtitles = movieBoxSubs.distinctBy { it.url }
                } else {
                    throw Exception("No stream links found")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingUrl = false
            }
        }
    }

    var selectedSubtitle by remember { mutableStateOf<SubtitleHelper.SubtitleItem?>(null) }
    LaunchedEffect(activeSubtitles) {
        if (selectedSubtitle == null && activeSubtitles.isNotEmpty()) {
            selectedSubtitle = activeSubtitles.firstOrNull { it.lang.contains("AR", true) || it.name.contains("العربية", true) }
                ?: activeSubtitles.firstOrNull()
        }
    }

    var cachedSubtitleLocalUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(selectedSubtitle) {
        val pickedSub = selectedSubtitle
        if (pickedSub != null) {
            withContext(Dispatchers.IO) {
                try {
                    if (pickedSub.url.startsWith("http")) {
                        val extracted = SubtitleHelper.downloadAndExtractSubtitle(context, pickedSub.url, activeId)
                        cachedSubtitleLocalUri = if (extracted != null) Uri.fromFile(extracted) else Uri.fromFile(File(pickedSub.url))
                    } else {
                        cachedSubtitleLocalUri = Uri.fromFile(File(pickedSub.url))
                    }
                } catch(e: Exception) { e.printStackTrace() }
            }
        } else {
            cachedSubtitleLocalUri = null
        }
    }

    // --- Fullscreen management ---
    LaunchedEffect(isFullscreen) {
        val activity = context as? Activity ?: return@LaunchedEffect
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            activity?.let {
                val ctrl = WindowCompat.getInsetsController(it.window, it.window.decorView)
                ctrl.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    BackHandler {
        if (isFullscreen) isFullscreen = false else onBack()
    }

    // --- ExoPlayer Setup ---
    val prefs = remember { context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE) }
    val savedPosition = remember { prefs.getLong("pos_$activeId", 0L) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            // Restore saved position when ready
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && savedPosition > 0) {
                        seekTo(savedPosition)
                        removeListener(this)
                    }
                }
            })
        }
    }

    // --- Position persistence (every 5s) ---
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val pos = exoPlayer.currentPosition
            if (pos > 0) {
                prefs.edit().putLong("pos_$activeId", pos).apply()
            }
        }
    }

    // --- Save position on dispose ---
    DisposableEffect(Unit) {
        onDispose {
            val pos = exoPlayer.currentPosition
            if (pos > 0) {
                prefs.edit().putLong("pos_$activeId", pos).apply()
            }
        }
    }

    // --- Media source ---
    LaunchedEffect(resolvedUrl, cachedSubtitleLocalUri) {
        if (resolvedUrl.isNotEmpty()) {
            val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0")
                .setAllowCrossProtocolRedirects(true)

            var mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(resolvedUrl))
            if (cachedSubtitleLocalUri != null) {
                val subConfig = MediaItem.SubtitleConfiguration.Builder(cachedSubtitleLocalUri!!)
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage("Arabic")
                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                    .build()
                mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
            }

            val mediaSource = if (resolvedUrl.contains(".m3u8")) {
                androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItemBuilder.build())
            } else {
                androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItemBuilder.build())
            }

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // --- Playback speed state ---
    var playbackSpeed by remember { mutableStateOf(1f) }
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // --- Volume state ---
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var showVolumeOverlay by remember { mutableStateOf(false) }

    // --- Fullscreen layout ---
    if (isFullscreen) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // ExoPlayer View with custom controller settings
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        controllerAutoShow = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        subtitleView?.setBottomPaddingFraction(0.02f)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // --- Gesture Overlay for Volume Swipe + Tap Skip ---
            GestureOverlay(
                exoPlayer = exoPlayer,
                audioManager = audioManager,
                maxVolume = maxVolume,
                currentVolume = currentVolume,
                onVolumeChange = { vol ->
                    currentVolume = vol
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                    showVolumeOverlay = true
                }
            )

            // Volume indicator overlay
            if (showVolumeOverlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(currentVolume * 100 / maxVolume)}%",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                LaunchedEffect(Unit) {
                    delay(1500)
                    showVolumeOverlay = false
                }
            }

            // Speed indicator
            var showSpeedMenu by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = { showSpeedMenu = true },
                    modifier = Modifier
                        .background(Color.Black.copy(0.5f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = "سرعة", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false }
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${speed}x",
                                    fontWeight = if (speed == playbackSpeed) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                playbackSpeed = speed
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }

            // Back button
            IconButton(
                onClick = { isFullscreen = false },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
        }
    } else {
        // --- Portrait mode ---
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "شاشة العرض", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Player area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (isLoadingUrl) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (resolvedUrl.isEmpty()) {
                        Text(
                            "عذراً، الحلقة المطلوبة غير متوفرة حالياً.",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                    controllerAutoShow = true
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    subtitleView?.setBottomPaddingFraction(0.02f)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Gesture overlay in portrait too
                        GestureOverlay(
                            exoPlayer = exoPlayer,
                            audioManager = audioManager,
                            maxVolume = maxVolume,
                            currentVolume = currentVolume,
                            onVolumeChange = { vol ->
                                currentVolume = vol
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                            }
                        )
                    }

                    if (!isLoadingUrl && resolvedUrl.isNotEmpty()) {
                        Button(
                            onClick = { isFullscreen = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f)
                            )
                        ) {
                            Text("ملء الشاشة")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Speed selector
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "السرعة: ${playbackSpeed}x",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LazyRow(
                        modifier = Modifier.bouncyOverscroll(isVertical = false),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        ) { _, speed ->
                            FilterChip(
                                selected = speed == playbackSpeed,
                                onClick = { playbackSpeed = speed },
                                label = { Text("${speed}x", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "يتم استخدام MovieBox API للبث المباشر المدمج بأعلى جودة.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                // Subtitle selector
                if (activeSubtitles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "اختر الترجمة المدمجة:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    LazyRow(
                        modifier = Modifier
                            .bouncyOverscroll(isVertical = false)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(activeSubtitles) { _, subItem ->
                            val isSelected = selectedSubtitle?.url == subItem.url
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectedSubtitle = subItem }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = subItem.name,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gesture overlay for volume swipe (right side) and tap-to-skip (left/right areas).
 * No overlap between gesture zones.
 */
@Composable
private fun GestureOverlay(
    exoPlayer: ExoPlayer,
    audioManager: AudioManager,
    maxVolume: Int,
    currentVolume: Int,
    onVolumeChange: (Int) -> Unit
) {
    var currentVol by remember { mutableIntStateOf(currentVolume) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Left 35% — double-tap to rewind 10s
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.35f)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                        }
                    )
                }
        )

        // Middle 30% — no gesture (for ExoPlayer controller interaction)
        Box(modifier = Modifier.fillMaxWidth(0.30f).fillMaxHeight().align(Alignment.Center))

        // Inner right 20% — double-tap to forward 10s
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.20f)
                .align(Alignment.CenterEnd)
                .padding(end = 0.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            exoPlayer.seekTo(exoPlayer.currentPosition + 10000)
                        }
                    )
                }
        )
    }

    // Volume swipe — rightmost 15%, outside the gesture Box to avoid overlap
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) },
                    onDragCancel = { currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }
                ) { change, dragAmount ->
                    // Only handle if touch is in the rightmost 15%
                    if (change.position.x > size.width * 0.85f) {
                        change.consume()
                        val newVol = (currentVol - (dragAmount / 15).roundToInt())
                            .coerceIn(0, maxVolume)
                        if (newVol != currentVol) {
                            currentVol = newVol
                            onVolumeChange(newVol)
                        }
                    }
                }
            }
    )
}
