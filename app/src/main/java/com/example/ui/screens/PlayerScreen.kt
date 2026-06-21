package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.viewmodel.MovieViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

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
    val tmdbIdString = if (isTv) activeId.substringBefore("-s") else activeId
    val tmdbId = tmdbIdString.toIntOrNull() ?: 0
    val startSeason = if (isTv) activeId.substringAfter("-s").substringBefore("-e").toIntOrNull() ?: 1 else 0
    val startEpisode = if (isTv) activeId.substringAfter("-e").toIntOrNull() ?: 1 else 0

    val downloadsList by viewModel.downloads.collectAsState(initial = emptyList())
    val activeDownloadedFile = remember(activeId, downloadsList) {
        downloadsList.find { it.id == activeId && it.status == "completed" }?.localFilePath ?: ""
    }
    val isLocalActive = activeDownloadedFile.isNotEmpty() && File(activeDownloadedFile).exists()
    
    var resolvedUrl by remember { mutableStateOf("") }
    var isLoadingUrl by remember { mutableStateOf(true) }
    var activeSubtitles by remember { mutableStateOf<List<com.example.ui.viewmodel.SubtitleHelper.SubtitleItem>>(emptyList()) }

    LaunchedEffect(activeId, activeDownloadedFile) {
        if (isLocalActive) {
            resolvedUrl = activeDownloadedFile
            val localSubFileVtt = File(context.filesDir, "downloads/$activeId.vtt")
            val localSubFileSrt = File(context.filesDir, "downloads/$activeId.srt")
            if (localSubFileVtt.exists() || localSubFileSrt.exists()) {
                val existsFile = if (localSubFileVtt.exists()) localSubFileVtt else localSubFileSrt
                activeSubtitles = listOf(com.example.ui.viewmodel.SubtitleHelper.SubtitleItem(name = "العربية (محلي)", url = existsFile.absolutePath, lang = "ar", langCode = "ar"))
            } else {
                activeSubtitles = emptyList()
            }
            isLoadingUrl = false
        } else {
            isLoadingUrl = true
            try {
                // Determine if activeId is a pure integer (TMDB ID) or a large MovieBox subject_id
                val baseId = if (isTv) activeId.substringBefore("-s") else activeId
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
                    
                    // --- Handle Subtitles ---
                    val movieBoxSubs = mutableListOf<com.example.ui.viewmodel.SubtitleHelper.SubtitleItem>()
                    if (topQuality.hasArabicSubtitle && topQuality.arabicSubtitleUrl != null) {
                        movieBoxSubs.add(com.example.ui.viewmodel.SubtitleHelper.SubtitleItem("العربية (MovieBox)", topQuality.arabicSubtitleUrl, "ar", "ar"))
                    } else if (topQuality.allSubtitles.isNotEmpty()) {
                        val arSub = topQuality.allSubtitles.find { it.languageCode.equals("ar", true) || it.languageName.contains("Arabic", true) }
                        if (arSub != null) {
                            movieBoxSubs.add(com.example.ui.viewmodel.SubtitleHelper.SubtitleItem("العربية (MovieBox)", arSub.url, "ar", "ar"))
                        }
                        topQuality.allSubtitles.forEach { s ->
                            movieBoxSubs.add(com.example.ui.viewmodel.SubtitleHelper.SubtitleItem(s.languageName + " (MovieBox)", s.url, s.languageCode, s.languageCode))
                        }
                    } else {
                        // Attempt /get_subtitles API
                        if (topQuality.resourceId.isNotEmpty()) {
                            val subRes = viewModel.movieBoxRepository.getSubtitles(subjectId, topQuality.resourceId).getOrNull()
                            if (subRes != null) {
                                if (subRes.hasArabic && subRes.arabicSubtitle != null) {
                                    movieBoxSubs.add(com.example.ui.viewmodel.SubtitleHelper.SubtitleItem("العربية (MovieBox)", subRes.arabicSubtitle.url, "ar", "ar"))
                                }
                                subRes.allSubtitles.forEach { s ->
                                    movieBoxSubs.add(com.example.ui.viewmodel.SubtitleHelper.SubtitleItem(s.languageName + " (MovieBox)", s.url, s.languageCode, s.languageCode))
                                }
                            }
                        }
                    }
                    
                    val finalList = mutableListOf<com.example.ui.viewmodel.SubtitleHelper.SubtitleItem>()
                    finalList.addAll(movieBoxSubs.distinctBy { it.url })
                    
                    activeSubtitles = finalList
                    
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

    var selectedSubtitle by remember { mutableStateOf<com.example.ui.viewmodel.SubtitleHelper.SubtitleItem?>(null) }
    
    // Auto select first AR sub
    LaunchedEffect(activeSubtitles) {
        if (selectedSubtitle == null && activeSubtitles.isNotEmpty()) {
            selectedSubtitle = activeSubtitles.firstOrNull { it.lang.contains("AR", true) || it.name.contains("العربية", true) } ?: activeSubtitles.firstOrNull()
        }
    }

    // Download optimal subtitle to cache
    var cachedSubtitleLocalUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(selectedSubtitle) {
        val pickedSub = selectedSubtitle
        if (pickedSub != null) {
            withContext(Dispatchers.IO) {
                try {
                    if (pickedSub.url.startsWith("http")) {
                        val extracted = com.example.ui.viewmodel.SubtitleHelper.downloadAndExtractSubtitle(context, pickedSub.url, activeId)
                        if (extracted != null) {
                            cachedSubtitleLocalUri = Uri.fromFile(extracted)
                        } else {
                            // Local fallback if it was already local
                            cachedSubtitleLocalUri = Uri.fromFile(File(pickedSub.url))
                        }
                    } else {
                        // Already local file absolute path
                        cachedSubtitleLocalUri = Uri.fromFile(File(pickedSub.url))
                    }
                } catch(e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            cachedSubtitleLocalUri = null
        }
    }

    LaunchedEffect(isFullscreen) {
        val activity = context as? Activity ?: return@LaunchedEffect
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    BackHandler {
        if (isFullscreen) isFullscreen = false else onBack()
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

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
                androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItemBuilder.build())
            } else {
                androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItemBuilder.build())
            }
            
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    if (isFullscreen) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        subtitleView?.setBottomPaddingFraction(0.02f)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(onClick = { isFullscreen = false }, modifier = Modifier.padding(16.dp).align(Alignment.TopStart).background(Color.Black.copy(0.5f), CircleShape)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
    } else {
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
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                    if (isLoadingUrl) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                    } else if (resolvedUrl.isEmpty()) {
                        Text("عذراً، الحلقة المطلوبة غير متوفرة حالياً.", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    subtitleView?.setBottomPaddingFraction(0.02f)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (!isLoadingUrl && resolvedUrl.isNotEmpty()) {
                         Button(
                             onClick = { isFullscreen = true },
                             modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                             colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha=0.6f))
                         ) {
                             Text("ملء الشاشة")
                         }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "يتم استخدام MovieBox API للبث المباشر المدمج بأعلى جودة.", modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray)
                
                if (activeSubtitles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "اختر الترجمة المدمجة:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeSubtitles.size) { index ->
                            val subItem = activeSubtitles[index]
                            val isSelected = selectedSubtitle?.url == subItem.url
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedSubtitle = subItem }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = subItem.name,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
