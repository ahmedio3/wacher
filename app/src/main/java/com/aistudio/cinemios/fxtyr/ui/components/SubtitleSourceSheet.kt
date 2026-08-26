package com.aistudio.cinemios.fxtyr.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.ui.screens.SectionHeader
import com.aistudio.cinemios.fxtyr.ui.screens.SourceSubtitleCard
import com.aistudio.cinemios.fxtyr.ui.viewmodel.SubtitleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Content-only subtitle source browser.
 * Shows 3 pages (MovieBox, Subdl, OpenSubtitles) via AnimatedContent.
 * Does NOT assume any container — works inside a drawer OR a ModalBottomSheet.
 *
 * @param customDownload Optional download override. When provided, replaces default standalone
 *   download. Used by OfflinePlayerScreen which needs downloadAndExtractSubtitle for player.
 * @param onSubtitleLoaded Called after download succeeds with the local file.
 *   matchedEpisode = 0 means episode could not be determined (show "غير محدد").
 */
@Composable
fun SubtitleSourceSheet(
    tmdbId: String,
    isTv: Boolean,
    season: Int = 0,
    episode: Int = 0,
    titleFallback: String = "",
    initialPage: Int = 0,
    context: Context = LocalContext.current,
    onNavigateBack: () -> Unit = {},
    onSubtitleLoaded: (file: File, language: String, languageCode: String, source: String, name: String, matchedEpisode: Int, batchId: String) -> Unit = { _, _, _, _, _, _, _ -> },
    onBatchComplete: (batchId: String, count: Int, releaseName: String) -> Unit = { _, _, _ -> },
    customDownload: (suspend (downloadUrl: String) -> List<Pair<File, Int>>)? = null
) {
    // All state is internal — completely isolated per instance
    var subtitlePage by remember { mutableIntStateOf(1) }
    var searchSubsList by remember { mutableStateOf<List<SubtitleHelper.SubtitleItem>?>(null) }
    var subdlSources by remember { mutableStateOf<List<SubtitleHelper.SubtitleItem>?>(null) }
    var openSubSources by remember { mutableStateOf<List<SubtitleHelper.SubtitleItem>?>(null) }
    var currentlyDownloadingUrl by remember { mutableStateOf<String?>(null) }
    var currentlyDownloadingSourceUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingSources by remember { mutableStateOf(false) }
    var currentSubPage by remember { mutableIntStateOf(initialPage) }

    // Update subtitlePage reactively
    subtitlePage = currentSubPage

    // Default download: standalone to standalone_subtitles/
    val defaultDownload: suspend (String) -> List<Pair<File, Int>> = { url ->
        SubtitleHelper.downloadSubtitleStandalone(context, url, UUID.randomUUID().toString())
    }
    val downloadFn = customDownload ?: defaultDownload

    Column(modifier = Modifier.fillMaxSize()) {
    // Header with navigation
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (subtitlePage > 0) {
            IconButton(onClick = { currentSubPage = 0 }) {
                Icon(Icons.Default.ArrowBack, "رجوع")
            }
        }
        Text(
            text = when (subtitlePage) {
                0 -> "اختيار مصدر الترجمة"
                1 -> "بحث MovieBox"
                2 -> "Subdl"
                3 -> "OpenSubtitles"
                else -> "الترجمة"
            },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.Default.Close, "إغلاق")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Page content
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
            // ===== PAGE 0: Source Picker =====
            0 -> {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "اختر مصدر الترجمة",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // MovieBox
                    OutlinedButton(
                        onClick = { currentSubPage = 1 },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("بحث MovieBox", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subdl
                    OutlinedButton(
                        onClick = { currentSubPage = 2 },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF4A90D9))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Subdl", fontWeight = FontWeight.Bold, color = Color(0xFF4A90D9))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // OpenSubtitles
                    OutlinedButton(
                        onClick = { currentSubPage = 3 },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF7CB342))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OpenSubtitles", fontWeight = FontWeight.Bold, color = Color(0xFF7CB342))
                    }
                }
            }

            // ===== PAGE 1: MovieBox Search Results =====
            1 -> {
                val movieScope = rememberCoroutineScope()

                // Auto-fetch on entry
                LaunchedEffect(Unit) {
                    currentlyDownloadingUrl = "__loading__"
                    searchSubsList = withContext(Dispatchers.IO) {
                        SubtitleHelper.fetchSubtitles(tmdbId, isTv, season, episode, titleFallback)
                    }
                    currentlyDownloadingUrl = null
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Action bar: search button
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
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
                            modifier = Modifier.weight(1f)
                        ) {
                            if (currentlyDownloadingUrl != null) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("بحث", fontSize = 12.sp)
                            }
                        }
                        Text("بحث MovieBox", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Results list
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        searchSubsList?.let { subs ->
                            if (subs.isEmpty()) {
                                item { Text("لا توجد ترجمات متاحة.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
                            } else {
                                items(subs) { sub ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(sub.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(sub.lang, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = {
                                                movieScope.launch {
                                                    currentlyDownloadingUrl = sub.url
                                                    val batchId = UUID.randomUUID().toString()
                                                    val files = downloadFn(sub.url)
                                                    var savedCount = 0
                                                    for ((file, matchedEp) in files) {
                                                        onSubtitleLoaded(file, sub.lang, sub.langCode, sub.source, sub.name, matchedEp, batchId)
                                                        savedCount++
                                                    }
                                                    if (savedCount > 0) onBatchComplete(batchId, savedCount, sub.name)
                                                    currentlyDownloadingUrl = null
                                                }
                                            }) {
                                                if (currentlyDownloadingUrl == sub.url) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.Download, "تحميل وتفعيل", tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (searchSubsList == null) {
                            item { Text("جاري البحث...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            // ===== PAGE 2: Subdl Results =====
            2 -> {
                LaunchedEffect(Unit) {
                    isLoadingSources = true
                    subdlSources = null
                    subdlSources = withContext(Dispatchers.IO) {
                        SubtitleHelper.fetchSubdlSubtitles(tmdbId, isTv, season, episode)
                    }
                    isLoadingSources = false
                }

                val sourceScope = rememberCoroutineScope()
                Column(modifier = Modifier.fillMaxSize()) {
                    if (subdlSources != null) {
                        OutlinedButton(
                            onClick = {
                                sourceScope.launch {
                                    isLoadingSources = true
                                    subdlSources = null
                                    subdlSources = withContext(Dispatchers.IO) {
                                        SubtitleHelper.fetchSubdlSubtitles(tmdbId, isTv, season, episode)
                                    }
                                    isLoadingSources = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoadingSources
                        ) {
                            if (isLoadingSources) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إعادة البحث", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (subdlSources == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("جاري تحميل ترجمات Subdl...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            subdlSources?.let { subs ->
                                if (subs.isNotEmpty()) {
                                    item { SectionHeader(title = "Subdl", count = subs.size, color = Color(0xFF4A90D9)) }
                                    items(subs) { sub ->
                                        SourceSubtitleCard(item = sub, isDownloading = currentlyDownloadingSourceUrl == sub.url, onDownload = {
                                            sourceScope.launch {
                                                currentlyDownloadingSourceUrl = sub.url
                                                val batchId = UUID.randomUUID().toString()
                                                val files = downloadFn(sub.url)
                                                var savedCount = 0
                                                for ((file, matchedEp) in files) {
                                                    onSubtitleLoaded(file, sub.lang, sub.langCode, sub.source, sub.name, matchedEp, batchId)
                                                    savedCount++
                                                }
                                                if (savedCount > 0) onBatchComplete(batchId, savedCount, sub.name)
                                                currentlyDownloadingSourceUrl = null
                                            }
                                        })
                                    }
                                }
                            }

                            item {
                                if ((subdlSources?.isEmpty() ?: true) && subdlSources != null) {
                                    Text("لا توجد ترجمات متاحة من Subdl.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ===== PAGE 3: OpenSubtitles Results =====
            3 -> {
                LaunchedEffect(Unit) {
                    isLoadingSources = true
                    openSubSources = null
                    openSubSources = withContext(Dispatchers.IO) {
                        SubtitleHelper.fetchOpenSubtitles(tmdbId, isTv, season, episode)
                    }
                    isLoadingSources = false
                }

                val sourceScope = rememberCoroutineScope()
                Column(modifier = Modifier.fillMaxSize()) {
                    if (openSubSources != null) {
                        OutlinedButton(
                            onClick = {
                                sourceScope.launch {
                                    isLoadingSources = true
                                    openSubSources = null
                                    openSubSources = withContext(Dispatchers.IO) {
                                        SubtitleHelper.fetchOpenSubtitles(tmdbId, isTv, season, episode)
                                    }
                                    isLoadingSources = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoadingSources
                        ) {
                            if (isLoadingSources) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إعادة البحث", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (openSubSources == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("جاري تحميل ترجمات OpenSubtitles...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            openSubSources?.let { subs ->
                                if (subs.isNotEmpty()) {
                                    item { SectionHeader(title = "OpenSubtitles", count = subs.size, color = Color(0xFF7CB342)) }
                                    items(subs) { sub ->
                                        SourceSubtitleCard(item = sub, isDownloading = currentlyDownloadingSourceUrl == sub.url, onDownload = {
                                            sourceScope.launch {
                                                currentlyDownloadingSourceUrl = sub.url
                                                val batchId = UUID.randomUUID().toString()
                                                val downloadUrl = if (sub.fileId != null) SubtitleHelper.getOpenSubtitleDownloadUrl(sub.fileId) ?: sub.url else sub.url
                                                if (downloadUrl.isNotEmpty()) {
                                                    val files = downloadFn(downloadUrl)
                                                    var savedCount = 0
                                                    for ((file, matchedEp) in files) {
                                                        onSubtitleLoaded(file, sub.lang, sub.langCode, sub.source, sub.name, matchedEp, batchId)
                                                        savedCount++
                                                    }
                                                    if (savedCount > 0) onBatchComplete(batchId, savedCount, sub.name)
                                                }
                                                currentlyDownloadingSourceUrl = null
                                            }
                                        })
                                    }
                                }
                            }

                            item {
                                if ((openSubSources?.isEmpty() ?: true) && openSubSources != null) {
                                    Text("لا توجد ترجمات متاحة من OpenSubtitles.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp))
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
