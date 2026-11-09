package com.aistudio.cinemios.fxtyr.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.ui.screens.SectionHeader
import com.aistudio.cinemios.fxtyr.ui.screens.SourceSubtitleCard
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons
import com.aistudio.cinemios.fxtyr.ui.viewmodel.SubtitleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Modern iOS-styled subtitle source browser.
 * Shows 3 pages (MovieBox, Subdl, OpenSubtitles) via AnimatedContent.
 * Does NOT assume any container — works inside a drawer OR a ModalBottomSheet.
 */
@Composable
fun SubtitleSourceSheet(
    tmdbId: String,
    isTv: Boolean,
    season: Int = 0,
    episode: Int = 0,
    titleFallback: String = "",
    initialPage: Int = 0,
    videoFilePath: String = "",
    videoFileName: String = "",
    videoFps: Double? = null,
    context: Context = LocalContext.current,
    onNavigateBack: () -> Unit = {},
    onSubtitleLoaded: (file: File, language: String, languageCode: String, source: String, name: String, matchedEpisode: Int, batchId: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onBatchComplete: (batchId: String, count: Int, releaseName: String) -> Unit = { _, _, _ -> },
    customDownload: (suspend (downloadUrl: String, onError: (SubtitleHelper.SubtitleError) -> Unit) -> List<Pair<File, Int>>)? = null
) {
    // Internal isolated state
    var subtitlePage by remember { mutableIntStateOf(1) }
    var searchSubsList by remember { mutableStateOf<List<SubtitleHelper.SubtitleItem>?>(null) }
    var subdlSources by remember { mutableStateOf<List<SubtitleHelper.SubtitleItem>?>(null) }
    var openSubSources by remember { mutableStateOf<List<SubtitleHelper.SubtitleItem>?>(null) }
    var currentlyDownloadingUrl by remember { mutableStateOf<String?>(null) }
    var currentlyDownloadingSourceUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingSources by remember { mutableStateOf(false) }
    var currentSubPage by remember { mutableIntStateOf(initialPage) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var subdlError by remember { mutableStateOf<String?>(null) }
    var openSubError by remember { mutableStateOf<String?>(null) }

    subtitlePage = currentSubPage

    val defaultDownload: suspend (String, (SubtitleHelper.SubtitleError) -> Unit) -> List<Pair<File, Int>> = { url, onError ->
        SubtitleHelper.downloadSubtitleStandalone(context, url, UUID.randomUUID().toString(), onError)
    }
    val downloadFn = customDownload ?: defaultDownload

    val effectiveVideoFileName =
        videoFileName.ifBlank { videoFilePath.substringAfterLast('/') }

    suspend fun loadOpenSubtitles(
        onError: (SubtitleHelper.SubtitleError) -> Unit
    ): List<SubtitleHelper.SubtitleItem> {
        val fingerprint = withContext(Dispatchers.IO) {
            SubtitleHelper.buildVideoFingerprint(context, videoFilePath)
        }
        return withContext(Dispatchers.IO) {
            SubtitleHelper.fetchOpenSubtitles(
                tmdbId = tmdbId,
                isTv = isTv,
                season = season,
                episode = episode,
                videoFileName = fingerprint?.fileName ?: effectiveVideoFileName,
                videoFps = videoFps ?: fingerprint?.fps,
                movieHash = fingerprint?.movieHash,
                movieFileSize = fingerprint?.fileSize,
                onError = onError
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // iOS Bottom Sheet Drag Handle
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    .align(Alignment.CenterHorizontally)
            )

            // Header with navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (subtitlePage > 0) {
                    IconButton(
                        onClick = { currentSubPage = 0 },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = IosIcons.ChevronRight,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = when (subtitlePage) {
                        0 -> "اختيار مصدر الترجمة"
                        1 -> "بحث MovieBox"
                        2 -> "ترجمات Subdl"
                        3 -> "ترجمات OpenSubtitles"
                        else -> "الترجمة"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "إغلاق",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Error banner
            val activeError = downloadError
                ?: if (subtitlePage == 2) subdlError
                else if (subtitlePage == 3) openSubError
                else null

            activeError?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { downloadError = null; subdlError = null; openSubError = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(AppIcons.Close, "إغلاق", modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Animated page switcher
            AnimatedContent(
                targetState = subtitlePage,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn(animationSpec = tween(280)))
                            .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut(animationSpec = tween(200)))
                    } else {
                        (slideInHorizontally { -it } + fadeIn(animationSpec = tween(280)))
                            .togetherWith(slideOutHorizontally { it / 3 } + fadeOut(animationSpec = tween(200)))
                    }
                },
                label = "sub_page",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                when (page) {
                    // ===== PAGE 0: Source Picker (iOS Grouped Style) =====
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "اختر مزود الترجمة المفضل للبحث عن ملفات الترجمة المتوافقة:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )

                            // iOS Grouped Container
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Row 1: MovieBox
                                    SourceOptionRow(
                                        title = "MovieBox",
                                        subtitle = "بحث فوري ومباشر مع توافق تام للمحتوى",
                                        icon = AppIcons.MovieBox,
                                        iconTint = MaterialTheme.colorScheme.primary,
                                        iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        onClick = { currentSubPage = 1 }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 66.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        thickness = 0.7.dp
                                    )

                                    // Row 2: Subdl
                                    SourceOptionRow(
                                        title = "Subdl",
                                        subtitle = "مكتبة ضخمة للترجمات العربية بمختلف الجودات",
                                        icon = IosIcons.ClosedCaptions,
                                        iconTint = Color(0xFF4A90D9),
                                        iconBg = Color(0xFF4A90D9).copy(alpha = 0.12f),
                                        onClick = { currentSubPage = 2 }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 66.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        thickness = 0.7.dp
                                    )

                                    // Row 3: OpenSubtitles
                                    SourceOptionRow(
                                        title = "OpenSubtitles",
                                        subtitle = "مطابقة دقيقة بالهاش وحجم الفيديو ومعدل الإطارات",
                                        icon = IosIcons.ClosedCaptions,
                                        iconTint = Color(0xFF7CB342),
                                        iconBg = Color(0xFF7CB342).copy(alpha = 0.12f),
                                        onClick = { currentSubPage = 3 }
                                    )
                                }
                            }
                        }
                    }

                    // ===== PAGE 1: MovieBox Search Results =====
                    1 -> {
                        val movieScope = rememberCoroutineScope()

                        LaunchedEffect(Unit) {
                            currentlyDownloadingUrl = "__loading__"
                            searchSubsList = withContext(Dispatchers.IO) {
                                SubtitleHelper.fetchSubtitles(tmdbId, isTv, season, episode, titleFallback)
                            }
                            currentlyDownloadingUrl = null
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top action bar: sleek iOS refresh pill
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (searchSubsList != null) "${searchSubsList?.size ?: 0} ترجمة متاحة" else "جاري البحث...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )

                                TextButton(
                                    onClick = {
                                        movieScope.launch {
                                            currentlyDownloadingUrl = "__loading__"
                                            searchSubsList = withContext(Dispatchers.IO) {
                                                SubtitleHelper.fetchSubtitles(tmdbId, isTv, season, episode, titleFallback)
                                            }
                                            currentlyDownloadingUrl = null
                                        }
                                    },
                                    enabled = currentlyDownloadingUrl == null,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (currentlyDownloadingUrl == "__loading__") {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(IosIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إعادة البحث", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (searchSubsList == null) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("جاري البحث في MovieBox...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                    }
                                }
                            } else {
                                val subs = searchSubsList.orEmpty()
                                if (subs.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("لا توجد ترجمات متاحة من MovieBox لهذا المحتوى.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(subs) { sub ->
                                            SourceSubtitleCard(
                                                item = sub,
                                                isDownloading = currentlyDownloadingUrl == sub.url,
                                                onDownload = {
                                                    movieScope.launch {
                                                        currentlyDownloadingUrl = sub.url
                                                        downloadError = null
                                                        val batchId = UUID.randomUUID().toString()
                                                        var downloadFailure: String? = null
                                                        val files = downloadFn(sub.url) { error ->
                                                            downloadFailure = error.userMessage
                                                        }
                                                        var savedCount = 0
                                                        for ((file, matchedEp) in files) {
                                                            onSubtitleLoaded(file, sub.lang, sub.langCode, sub.source, sub.name, matchedEp, batchId)
                                                            savedCount++
                                                        }
                                                        if (savedCount > 0) {
                                                            onBatchComplete(batchId, savedCount, sub.name)
                                                        } else {
                                                            downloadError = downloadFailure ?: "تعذر تحميل الترجمة. قد يكون الملف غير مدعوم أو الرابط منتهيًا."
                                                        }
                                                        currentlyDownloadingUrl = null
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ===== PAGE 2: Subdl Results =====
                    2 -> {
                        LaunchedEffect(Unit) {
                            isLoadingSources = true
                            subdlSources = null
                            subdlError = null
                            var fetchError: String? = null
                            val result = withContext(Dispatchers.IO) {
                                SubtitleHelper.fetchSubdlSubtitles(
                                    tmdbId = tmdbId,
                                    isTv = isTv,
                                    season = season,
                                    episode = episode,
                                    videoFileName = effectiveVideoFileName,
                                    videoFps = videoFps,
                                    onError = { error -> fetchError = error.userMessage }
                                )
                            }
                            subdlSources = result
                            subdlError = fetchError
                            isLoadingSources = false
                        }

                        val sourceScope = rememberCoroutineScope()
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (subdlSources != null) "${subdlSources?.size ?: 0} ترجمة متاحة" else "جاري التحميل...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )

                                TextButton(
                                    onClick = {
                                        sourceScope.launch {
                                            isLoadingSources = true
                                            subdlSources = null
                                            subdlError = null
                                            var fetchError: String? = null
                                            val result = withContext(Dispatchers.IO) {
                                                SubtitleHelper.fetchSubdlSubtitles(
                                                    tmdbId = tmdbId,
                                                    isTv = isTv,
                                                    season = season,
                                                    episode = episode,
                                                    videoFileName = effectiveVideoFileName,
                                                    videoFps = videoFps,
                                                    onError = { error -> fetchError = error.userMessage }
                                                )
                                            }
                                            subdlSources = result
                                            subdlError = fetchError
                                            isLoadingSources = false
                                        }
                                    },
                                    enabled = !isLoadingSources,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isLoadingSources) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(IosIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إعادة البحث", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (subdlSources == null) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("جاري تحميل ترجمات Subdl...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                    }
                                }
                            } else {
                                val subs = subdlSources.orEmpty()
                                if (subs.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("لا توجد ترجمات متاحة من Subdl.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(subs) { sub ->
                                            SourceSubtitleCard(
                                                item = sub,
                                                isDownloading = currentlyDownloadingSourceUrl == sub.url,
                                                onDownload = {
                                                    sourceScope.launch {
                                                        currentlyDownloadingSourceUrl = sub.url
                                                        downloadError = null
                                                        val batchId = UUID.randomUUID().toString()
                                                        var downloadFailure: String? = null
                                                        val files = downloadFn(sub.url) { error ->
                                                            downloadFailure = error.userMessage
                                                        }
                                                        var savedCount = 0
                                                        for ((file, matchedEp) in files) {
                                                            onSubtitleLoaded(file, sub.lang, sub.langCode, sub.source, sub.name, matchedEp, batchId)
                                                            savedCount++
                                                        }
                                                        if (savedCount > 0) {
                                                            onBatchComplete(batchId, savedCount, sub.name)
                                                        } else {
                                                            downloadError = downloadFailure ?: "تعذر تحميل الترجمة. قد يكون الملف غير مدعوم أو الرابط منتهيًا."
                                                        }
                                                        currentlyDownloadingSourceUrl = null
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ===== PAGE 3: OpenSubtitles Results =====
                    3 -> {
                        LaunchedEffect(tmdbId, isTv, season, episode, videoFilePath) {
                            isLoadingSources = true
                            openSubSources = null
                            openSubError = null
                            var fetchError: String? = null
                            val result = loadOpenSubtitles { error ->
                                fetchError = error.userMessage
                            }
                            openSubSources = result
                            openSubError = fetchError
                            isLoadingSources = false
                        }

                        val sourceScope = rememberCoroutineScope()
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (openSubSources != null) "${openSubSources?.size ?: 0} ترجمة متاحة" else "جاري التحميل...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )

                                TextButton(
                                    onClick = {
                                        sourceScope.launch {
                                            isLoadingSources = true
                                            openSubSources = null
                                            openSubError = null
                                            var fetchError: String? = null
                                            val result = loadOpenSubtitles { error ->
                                                fetchError = error.userMessage
                                            }
                                            openSubSources = result
                                            openSubError = fetchError
                                            isLoadingSources = false
                                        }
                                    },
                                    enabled = !isLoadingSources,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isLoadingSources) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(IosIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إعادة البحث", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (openSubSources == null) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("جاري تحميل ترجمات OpenSubtitles...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                    }
                                }
                            } else {
                                val subs = openSubSources.orEmpty()
                                if (subs.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("لا توجد ترجمات متاحة من OpenSubtitles.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(subs) { sub ->
                                            val downloadKey = sub.fileId ?: sub.url
                                            SourceSubtitleCard(
                                                item = sub,
                                                isDownloading = currentlyDownloadingSourceUrl == downloadKey,
                                                onDownload = {
                                                    sourceScope.launch {
                                                        currentlyDownloadingSourceUrl = downloadKey
                                                        downloadError = null
                                                        val batchId = UUID.randomUUID().toString()
                                                        var downloadFailure: String? = null
                                                        val downloadUrl = if (sub.fileId != null) {
                                                            SubtitleHelper.getOpenSubtitleDownloadUrl(sub.fileId) { error ->
                                                                downloadFailure = error.userMessage
                                                            } ?: sub.url
                                                        } else {
                                                            sub.url
                                                        }
                                                        if (downloadUrl.isNotEmpty()) {
                                                            val files = downloadFn(downloadUrl) { error ->
                                                                downloadFailure = error.userMessage
                                                            }
                                                            var savedCount = 0
                                                            for ((file, matchedEp) in files) {
                                                                onSubtitleLoaded(file, sub.lang, sub.langCode, sub.source, sub.name, matchedEp, batchId)
                                                                savedCount++
                                                            }
                                                            if (savedCount > 0) {
                                                                onBatchComplete(batchId, savedCount, sub.name)
                                                            } else {
                                                                downloadError = downloadFailure ?: "تعذر تحميل الترجمة. قد يكون الملف غير مدعوم أو الرابط منتهيًا."
                                                            }
                                                        } else {
                                                            downloadError = downloadFailure ?: "تعذر الحصول على رابط التحميل من OpenSubtitles."
                                                        }
                                                        currentlyDownloadingSourceUrl = null
                                                    }
                                                }
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

@Composable
private fun SourceOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = IosIcons.ChevronLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
        )
    }
}
