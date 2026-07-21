package com.example.ui.screens

import android.app.Application
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import com.example.data.local.DownloadEntity
import com.example.data.local.SeasonMetaEntity
import com.example.ui.theme.PalettePrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PaletteMutedRed
import com.example.utils.isLatinText
import com.example.ui.components.CircularSelectionIndicator
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.SubtitleHelper
import com.example.ui.viewmodel.RequestState
import java.io.File

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: MovieViewModel,
    onNavigateToPlayer: (String, String, String) -> Unit,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    
    // Active segment tab: 0 for Series, 1 for Movies. 
    // In RTL, 0 is on the right side (Series) and is active by default.
    var activeSegmentTab by remember { mutableIntStateOf(0) }

    // Grouping series downloads by family name/id
    val tvShowDownloads = downloads.filter { it.mediaType == "tv" }
    val playlistGroups = tvShowDownloads.groupBy { it.mediaId }
    val seriesPlaylists = playlistGroups.filter { it.value.isNotEmpty() }

    // Movies are kept individual
    val individualDownloads = downloads.filter { it.mediaType == "movie" }

    // Active bottom sheet series tracking
    // (Series open as a full NavHost page now — see PlaylistFolderCard.onClick)

    // Force Arabic Layout Direction RTL Globally on pages
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // iOS Styled Custom Premium Header with RTL alignment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "التحميلات غير المتصلة",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowCircleDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Elegant iOS Segment Bar layout with custom touch feedback ripples
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "المسلسلات (${seriesPlaylists.size})",
                        "الأفلام (${individualDownloads.size})",
                        "محلي"
                    )
                    tabs.forEachIndexed { index, title ->
                        val isSelected = activeSegmentTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { activeSegmentTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // RENDER SELECTED SECTION
                when (activeSegmentTab) {
                    0 -> {
                        // SERIES PLAYLISTS
                        if (seriesPlaylists.isEmpty()) {
                            EmptyDownloadsView(message = "لا تملك مسلسلات منزلة بعد. قم بتحميل حلقات مسلسل لتنظيمها وعرضها هنا.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(seriesPlaylists.keys.toList()) { mediaId ->
                                    val playlistEpisodes = playlistGroups[mediaId] ?: emptyList()
                                    val parentTitle = playlistEpisodes.firstOrNull()?.title?.substringBefore(" - ") ?: "مسلسل"
                                    // posterPath is now always the series poster (stored at download time)
                                    val posterPath = playlistEpisodes.firstOrNull()?.posterPath ?: ""
                                    val completedCount = playlistEpisodes.count { it.status == "completed" }
                                    val downloadingCount = playlistEpisodes.size - completedCount
                                    
                                    PlaylistFolderCard(
                                        seriesTitle = parentTitle,
                                        posterPath = posterPath,
                                        completedCount = completedCount,
                                        downloadingCount = downloadingCount,
                                        onClick = {
                                            navController.navigate("series_downloads/$mediaId")
                                        }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // MOVIE FILES
                        if (individualDownloads.isEmpty()) {
                            EmptyDownloadsView(message = "لا توجد أفلام أو تنزيلات فردية")
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(individualDownloads, key = { it.id }) { item ->
                                    DownloadItemRow(
                                        item = item,
                                        viewModel = viewModel,
                                        onPlayClick = { path ->
                                            if (path != null) {
                                                onNavigateToPlayer(item.id, item.title, path)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // LOCAL FILES from device storage
                        LocalFilesTab(
                            context = context,
                            onNavigateToPlayer = onNavigateToPlayer
                        )
                    }
                }

                // Files Storage Directory Location Notice Card (only in non-local tabs)
                if (activeSegmentTab != 2) {
                    val exactPath = remember(context) {
                        File(context.filesDir, "downloads").absolutePath
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "مسار حفظ الملفات على الجهاز:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = exactPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
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

// Reusable header: circular back button (trailing/RTL-forward side) + title pill with two-tier text (leading side).
// Parameterized so the same visual pattern can be reused on other pages later.
@Composable
fun PillHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPillClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back button (right side in RTL) — circular, white background
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "رجوع",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        // Title pill (left side in RTL) — inline two-tier text (bold title + smaller/lighter subtitle beside it)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .then(if (onPillClick != null) Modifier.clickable { onPillClick.invoke() } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    textDirection = TextDirection.Ltr,
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (isLatinText(title)) JetBrainsMonoFontFamily else null,
                                    textDirection = if (isLatinText(title)) TextDirection.Ltr else TextDirection.Rtl
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
            }
        }
    }
}

// FULL PAGE: per-series downloaded-episodes viewer + season switcher (replaces ModalBottomSheet)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SeriesDetailPage(
    seriesId: String,
    viewModel: MovieViewModel,
    onNavigateToPlayer: (String, String, String) -> Unit,
    onBack: () -> Unit,
    onPillClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    val downloadedEpisodes = remember(downloads, seriesId) {
        downloads.filter { it.mediaType == "tv" && it.mediaId == seriesId }
    }
    val seriesTitle = downloadedEpisodes.firstOrNull()?.title?.substringBefore(" - ") ?: "مسلسل"
    val posterPath = downloadedEpisodes.firstOrNull()?.posterPath ?: ""
    // Total combined file size of all the series' downloaded episodes (for the header subtitle)
    val totalDownloadedBytes = downloadedEpisodes.sumOf { it.totalBytes }
    val totalSizeText = formatBytes(totalDownloadedBytes)

    // Per-session caches so switching seasons back and forth doesn't re-read file metadata from disk
    val durationCache = remember { mutableMapOf<String, Long>() }
    val sizeCache = remember { mutableMapOf<String, String>() }

    val seasonPrefs = context.getSharedPreferences("series_season_prefs", Context.MODE_PRIVATE)
    var selectedSeasonNumber by remember { mutableIntStateOf(seasonPrefs.getInt("season_$seriesId", 1)) }

    val mainPrefs = context.getSharedPreferences("watchera_prefs", Context.MODE_PRIVATE)
    var hasFetchedSubtitleOnce by remember { mutableStateOf(mainPrefs.getBoolean("has_fetched_subtitle_once", false)) }

    var showDownloadNewSheet by remember { mutableStateOf(false) }
    var selectedForContextMenu by remember { mutableStateOf<String?>(null) }
    // The row that was most recently the context-menu target. On close it is held at full opacity
    // (snap, no dip) so the shared dimAlpha can safely tween back to 1 on BOTH open and close
    // without flashing the just-deselected row.
    var lastContextMenuTarget by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var seasonMenuSeason by remember { mutableStateOf<Int?>(null) }
    var showSeasonDeleteConfirm by remember { mutableStateOf<Int?>(null) }

    // Shared dimming alpha: header title, season chips and download button fade together during context-menu mode.
    // Tween on BOTH open and close for a smooth feel; the just-deselected (last-target) row is held at full
    // opacity via its own independent branch (see CompactEpisodeRow), eliminating the dip/flash on close.
    val dimAlpha by animateFloatAsState(
        targetValue = if (selectedForContextMenu != null) 0.35f else 1f,
        animationSpec = if (selectedForContextMenu != null) tween(250) else tween(180)
    )

    // Offline season-metadata fallback (cached from prior successful network fetches)
    var seasonMetaList by remember { mutableStateOf<List<SeasonMetaEntity>>(emptyList()) }
    LaunchedEffect(seriesId) {
        val parsedId = seriesId.toIntOrNull() ?: 0
        if (parsedId > 0) {
            runCatching { seasonMetaList = viewModel.getSeasonMeta(parsedId) }
        }
    }

    // Load TMDB Series Details to fetch seasons list and correct total episodes count from server
    LaunchedEffect(seriesId) {
        val parsedId = seriesId.toIntOrNull() ?: 0
        if (parsedId > 0) {
            viewModel.fetchTvDetails(parsedId)
        }
    }

    val tvDetailsMap by viewModel.tvDetails.collectAsState()
    val parsedSeriesIntId = seriesId.toIntOrNull() ?: 0
    val tvDetailsState = tvDetailsMap[parsedSeriesIntId]

    // Extracted seasons list: network authoritative; else Room season_meta fallback; else downloaded-only
    val seasons = remember(tvDetailsState, seasonMetaList) {
        if (tvDetailsState is RequestState.Success) {
            tvDetailsState.data.seasons?.filter { it.seasonNumber > 0 } ?: emptyList()
        } else if (seasonMetaList.isNotEmpty()) {
            seasonMetaList
                .filter { it.tmdbId == parsedSeriesIntId }
                .map { meta ->
                    com.example.data.remote.TmdbSeason(
                        id = meta.seasonNumber,
                        seasonNumber = meta.seasonNumber,
                        episodeCount = meta.episodeCount,
                        name = meta.name,
                        posterPath = null
                    )
                }
        } else {
            // Fallback using downloaded episodes content
            val downloadedSeasons = downloadedEpisodes.map { it.season }.distinct().sorted()
            downloadedSeasons.map { sNo ->
                com.example.data.remote.TmdbSeason(
                    id = sNo,
                    seasonNumber = sNo,
                    episodeCount = downloadedEpisodes.filter { it.season == sNo }.size,
                    name = "الموسم $sNo",
                    posterPath = null
                )
            }
        }
    }

    // Keep selected season number constrained
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && seasons.none { it.seasonNumber == selectedSeasonNumber }) {
            selectedSeasonNumber = seasons.first().seasonNumber
        }
    }

    // Auto-exit selection mode when the last selected item is deselected
    LaunchedEffect(selectedIds) {
        if (selectionMode && selectedIds.isEmpty()) {
            selectionMode = false
        }
    }

    // Run season loader from TMDB to dynamically retrieve remaining episodes checklist
    LaunchedEffect(seriesId, selectedSeasonNumber) {
        val tvId = seriesId.toIntOrNull() ?: 0
        if (tvId > 0) {
            viewModel.fetchSeasonDetails(tvId, selectedSeasonNumber)
        }
    }

    val seasonDetailsStateMap by viewModel.seasonDetails.collectAsState()
    val seasonDetailState = seasonDetailsStateMap["$seriesId-$selectedSeasonNumber"]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PillHeader(
            title = seriesTitle,
            subtitle = totalSizeText,
            onBack = onBack,
            onPillClick = onPillClick,
            modifier = Modifier.alpha(dimAlpha)
        )

        // Active Tab bar seasons list with statistics: e.g. "الموسم 1 (1/7)"
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(dimAlpha)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(seasons) { s ->
                val isSelected = selectedSeasonNumber == s.seasonNumber
                val downloadedCount = downloadedEpisodes.count { it.season == s.seasonNumber }

                // Total count mapping
                var totalCountText = s.episodeCount?.toString() ?: "0"
                if (isSelected && seasonDetailState is RequestState.Success) {
                    totalCountText = (seasonDetailState.data.episodes?.size ?: s.episodeCount ?: 0).toString()
                }

                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    selectedSeasonNumber = s.seasonNumber
                                    seasonPrefs.edit().putInt("season_$seriesId", s.seasonNumber).apply()
                                },
                                onLongClick = { seasonMenuSeason = s.seasonNumber }
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "الموسم ${s.seasonNumber}",
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "($downloadedCount/$totalCountText)",
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = seasonMenuSeason == s.seasonNumber,
                        onDismissRequest = { seasonMenuSeason = null },
                        containerColor = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shadowElevation = 8.dp,
                        tonalElevation = 0.dp
                    ) {
                        DropdownMenuItem(
                            text = { Text("تحديد متعدد", fontWeight = FontWeight.Bold, color = PalettePrimary) },
                            onClick = {
                                seasonMenuSeason = null
                                selectionMode = true
                                selectedIds = downloadedEpisodes.filter { it.season == s.seasonNumber }.map { it.id }.toSet()
                            },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PalettePrimary, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف جميع حلقات الموسم", color = PaletteMutedRed) },
                            onClick = {
                                seasonMenuSeason = null
                                showSeasonDeleteConfirm = s.seasonNumber
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PaletteMutedRed, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }

        // Listing current downloaded or downloading episodes in season — slide+fade between seasons
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        AnimatedContent(
            targetState = selectedSeasonNumber,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                val forward = targetState > initialState
                val enterOffset: (Int) -> Int = { full ->
                    if (forward) { if (isRtl) -full else full } else { if (isRtl) full else -full }
                }
                val exitOffset: (Int) -> Int = { full ->
                    if (forward) { if (isRtl) full else -full } else { if (isRtl) -full else full }
                }
                slideInHorizontally(animationSpec = tween(250), initialOffsetX = enterOffset) + fadeIn(animationSpec = tween(250)) togetherWith
                    slideOutHorizontally(animationSpec = tween(250), targetOffsetX = exitOffset) + fadeOut(animationSpec = tween(250))
            },
            label = "seasonList"
        ) { season ->
            val listForSeason = downloadedEpisodes
                .filter { it.season == season }
                .sortedBy { it.episode }
            if (listForSeason.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.CloudQueue, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "لا توجد حلقات منزلة في هذا الموسم حالياً", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!hasFetchedSubtitleOnce) {
                        item {
                            Text(
                                text = "اجباري: اضغط مطولا على الحلقة > تحميل الترجمة.\nيمكنك عدم فعل ذلك، لأنه عند الدخول إلى الحلقة لمشاهدتها يتم جلب الترجمة تلقائيا في حالة وجود نت (غالبا يا غبي بتدخل الحلقة لما يكون مفيش نت، فاعمل الخطوة الأولى الإجبارية)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }
                    items(listForSeason, key = { it.id }) { item ->
                        CompactEpisodeRow(
                            item = item,
                            viewModel = viewModel,
                            onPlayClick = { path ->
                                if (path != null) {
                                    onNavigateToPlayer(item.id, item.title, path)
                                }
                            },
                            isSelected = item.id in selectedIds,
                            isSelectionMode = selectionMode,
                            dimAlpha = dimAlpha,
                            isContextMenuTarget = selectedForContextMenu == item.id,
                            menuOpen = selectedForContextMenu != null,
                            lastTargetId = lastContextMenuTarget,
                            onSubtitleFetched = { hasFetchedSubtitleOnce = true },
                            durationCache = durationCache,
                            sizeCache = sizeCache,
                            onLongClick = {
                                selectedForContextMenu = item.id
                                lastContextMenuTarget = item.id
                            },
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                            },
                            onDismissContextMenu = { selectedForContextMenu = null },
                            onEnterMultiSelect = {
                                selectedForContextMenu = null
                                selectionMode = true
                                selectedIds = setOf(item.id)
                            }
                        )
                    }
                }
            }
        }

        // Batch action bar for multi-select mode
        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${selectedIds.size} مختارة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Text("إلغاء التحديد", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { showBatchDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = selectedIds.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("حذف", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Button to trigger download new episodes sheet at the bottom block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(dimAlpha)
                .padding(20.dp)
        ) {
            Button(
                onClick = { showDownloadNewSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Text(
                        text = "تنزيل باقي حلقات المسلسل",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Secondary bottom sheet (70% Height)
        if (showDownloadNewSheet) {
            val appContext = context.applicationContext as android.app.Application
            val movieBoxViewModel: com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel(factory = com.example.ui.viewmodel.ViewModelFactory(appContext))

            val episodeStillPaths = remember(downloads) {
                downloads.filter { !it.stillPath.isNullOrEmpty() }
                    .associate { (it.season to it.episode) to it.stillPath!! }
            }

            com.example.ui.components.moviebox.MovieBoxDownloadSheet(
                movieTitle = seriesTitle,
                movieYear = null,
                mediaType = "tv",
                viewModel = movieBoxViewModel,
                onDismissRequest = { showDownloadNewSheet = false },
                onTryOtherMethod = { showDownloadNewSheet = false },
                episodeStillPaths = episodeStillPaths,
                alreadyDownloaded = { season, episode, quality ->
                    downloads.any {
                        it.mediaId == seriesId && it.mediaType == "tv" &&
                            it.season == season && it.episode == episode &&
                            it.quality == quality && it.status == "completed"
                    }
                },
                onDownloadClick = { url, quality, s, ep, still ->
                    viewModel.requestDownload(
                        mediaId = seriesId,
                        title = seriesTitle,
                        posterPath = posterPath,
                        stillPath = still,
                        mediaType = "tv",
                        season = s,
                        episode = ep,
                        quality = quality,
                        customUrl = url
                    )
                    showDownloadNewSheet = false
                }
            )
        }

        // Batch delete confirmation dialog
        if (showBatchDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                title = { Text("حذف ${selectedIds.size} عنصر؟") },
                text = { Text("سيتم حذف الحلقات المختارة بشكل دائم من التخزين.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedIds.forEach { id -> viewModel.deleteDownload(id) }
                            showBatchDeleteConfirm = false
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("حذف الكل") }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("إلغاء") }
                }
            )
        }

        // Season delete confirmation dialog (long-press season pill → "حذف جميع حلقات الموسم")
        if (showSeasonDeleteConfirm != null) {
            val seasonNo = showSeasonDeleteConfirm!!
            val count = downloadedEpisodes.count { it.season == seasonNo }
            AlertDialog(
                onDismissRequest = { showSeasonDeleteConfirm = null },
                title = { Text("حذف جميع حلقات الموسم $seasonNo؟") },
                text = { Text("سيتم حذف $count حلقة بشكل دائم من التخزين.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            downloadedEpisodes.filter { it.season == seasonNo }.forEach { viewModel.deleteDownload(it.id) }
                            showSeasonDeleteConfirm = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("حذف الكل") }
                },
                dismissButton = {
                    TextButton(onClick = { showSeasonDeleteConfirm = null }) { Text("إلغاء") }
                }
            )
        }
    }
}



// FOLDER CARD REPRESENTATION FOR EPISODES
@Composable
fun PlaylistFolderCard(
    seriesTitle: String,
    posterPath: String,
    completedCount: Int,
    downloadingCount: Int,
    onClick: () -> Unit
) {
    val posterUrl = if (posterPath.startsWith("http")) posterPath else "https://image.tmdb.org/t/p/w185$posterPath"
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seriesTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = if (isLatinText(seriesTitle)) JetBrainsMonoFontFamily else null),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (downloadingCount > 0) {
                    Text(
                        text = "تم تحميل $completedCount حلقات — $downloadingCount حلقات قيد التحميل",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "تم تحميل $completedCount حلقات من المسلسل",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun EmptyDownloadsView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "المجلد فارغ",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadItemRow(
    item: DownloadEntity,
    viewModel: MovieViewModel,
    onPlayClick: (String?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isCompleted = item.status == "completed"
    val isPaused = item.status == "paused"
    val posterUrl = if (item.posterPath.startsWith("http")) item.posterPath else "https://image.tmdb.org/t/p/w185${item.posterPath}"
    val episodeStillUrl = if (item.stillPath.isNotEmpty()) "https://image.tmdb.org/t/p/w300${item.stillPath}" else null
    var showContextMenu by remember { mutableStateOf(false) }

    // Custom press effect (replaces default ripple) — subtle scale + alpha on touch-down
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.92f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, animationSpec = tween(150))

    // File size is read off the main thread (File.length() is still I/O) and shown once available
    var fileSizeText by remember(item.id) { mutableStateOf("...") }
    LaunchedEffect(item.id) {
        fileSizeText = withContext(Dispatchers.IO) {
            runCatching { formatBytes(File(item.localFilePath).length()) }.getOrDefault("...")
        }
    }

    val partialFilePath = java.io.File(context.filesDir, "downloads/${item.id}.mp4").absolutePath

    val subtitleDownloads by viewModel.subtitleDownloads.collectAsState(initial = emptyList())
    val downloadScope = rememberCoroutineScope()
    var isDownloadingSubtitle by remember { mutableStateOf(false) }
    val hasSubtitle = remember(item.id, subtitleDownloads) {
        subtitleDownloads.any { sub ->
            if (item.mediaType == "tv")
                sub.tmdbId == item.mediaId && sub.season == item.season && sub.episode == item.episode
            else
                sub.tmdbId == item.mediaId
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .alpha(pressAlpha)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (isCompleted) {
                        onPlayClick(item.localFilePath)
                    } else if (isPaused) {
                        viewModel.resumeDownload(item.id)
                    } else {
                        viewModel.pauseDownload(item.id)
                    }
                },
                onLongClick = { showContextMenu = true }
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Mini Poster (use episode still if available for landscape 16:9)
        val thumbUrl = if (item.mediaType == "tv" && episodeStillUrl != null) episodeStillUrl else posterUrl
        Box(
            modifier = Modifier
                .size(if (item.mediaType == "tv" && episodeStillUrl != null) androidx.compose.ui.unit.DpSize(60.dp, 34.dp) else androidx.compose.ui.unit.DpSize(60.dp, 86.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Details Block
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (item.mediaType == "tv" && item.episode > 0) {
                // For TV episodes: show episode number, not series name (already in header)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الحلقة ${item.episode}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    if (item.season > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "الموسم ${item.season}",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.quality,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            } else {
                // For movies: show the movie title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = if (isLatinText(item.title)) JetBrainsMonoFontFamily else null),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "الفيلم السينمائي",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.quality,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            if (!isCompleted) {
                val formattedDownloaded = formatBytes(item.downloadedBytes)
                val formattedTotal = formatBytes(item.totalBytes)
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val progressText = if (item.mediaType == "tv" || item.totalBytes == item.downloadedBytes) {
                        formattedDownloaded
                    } else {
                        "$formattedDownloaded / $formattedTotal"
                    }
                    Text(
                        text = progressText,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = item.downloadSpeed,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = "${item.progress}%",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Last watched time (real from SharedPreferences)
                val prefs = context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE)
                val lastPos = prefs.getLong("pos_${item.id}", 0L)
                val hasProgress = lastPos > 0
                
                // Get video duration from MediaMetadataRetriever (cached via File length estimate)
                val durationSecs = try {
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        retriever.release()
                        durStr?.toLongOrNull()?.let { it / 1000 } ?: 0L
                    } else 0L
                } catch (_: Exception) { 0L }
                
                if (hasProgress && durationSecs > 0) {
                    val posSecs = lastPos / 1000
                    val progress = (posSecs.toFloat() / durationSecs.toFloat()).coerceIn(0f, 1f)
                    val posMins = posSecs / 60
                    val posSecsRem = posSecs % 60
                    val durMins = durationSecs / 60
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    // Gradient watch-progress bar (Cyan → Green)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Cyan, Color.Green)
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$posMins:${String.format("%02d", posSecsRem)} / $durMins دقيقة",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                } else if (hasProgress) {
                    val posSecs = lastPos / 1000
                    val posMins = posSecs / 60
                    val posSecsRem = posSecs % 60
                    Text(
                        text = "آخر مشاهدة: $posMins:${String.format("%02d", posSecsRem)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "جاهز للمشاهدة بدون اتصال ($fileSizeText)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (isDownloadingSubtitle) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "يتم جلب الترجمة..",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (hasSubtitle) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = "ترجمة",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        }

        // Long-press context menu (replaces the removed 3-dots ModalBottomSheet)
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp
        ) {
            if (!isCompleted) {
                DropdownMenuItem(
                    text = { Text("مشاهدة ما تم تحميله") },
                    onClick = {
                        showContextMenu = false
                        if (java.io.File(partialFilePath).exists()) {
                            onPlayClick(partialFilePath)
                        } else {
                            android.widget.Toast.makeText(context, "الملف غير جاهز بعد", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                DropdownMenuItem(
                    text = { Text(if (isPaused) "استئناف التحميل" else "إيقاف التحميل") },
                    onClick = {
                        showContextMenu = false
                        if (isPaused) viewModel.resumeDownload(item.id)
                        else viewModel.pauseDownload(item.id)
                    },
                    leadingIcon = { Icon(if (isPaused) Icons.Default.FileDownload else Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            if (isCompleted) {
                DropdownMenuItem(
                    text = { Text("حفظ الفيديو في المعرض") },
                    onClick = {
                        showContextMenu = false
                        try {
                            val destDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
                            if (!destDir.exists()) destDir.mkdirs()
                            val safeTitle = item.title.replace("/", "_").replace("\\", "_")
                            val destFile = java.io.File(destDir, "$safeTitle.mp4")
                            java.io.File(item.localFilePath).copyTo(destFile, overwrite = true)
                            android.widget.Toast.makeText(context, "تم حفظ الفيديو للمعرض (${destFile.absolutePath})", android.widget.Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "خطأ أثناء الحفظ: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            DropdownMenuItem(
                text = { Text("تحميل الترجمة") },
                onClick = {
                    showContextMenu = false
                    isDownloadingSubtitle = true
                    context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("has_fetched_subtitle_once", true).apply()
                    downloadScope.launch {
                        val tmdbId = if (item.mediaType == "tv") item.mediaId.substringBefore("-s") else item.mediaId
                        val file = SubtitleHelper.fetchAndSaveMovieBoxSubtitle(
                            context, tmdbId, item.mediaType == "tv", item.season, item.episode, item.title, item.id
                        )
                        isDownloadingSubtitle = false
                        android.widget.Toast.makeText(
                            context,
                            if (file != null) "✓ تم تحميل الترجمة العربية" else "لم يتم العثور على ترجمة عربية",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                leadingIcon = {
                    if (isDownloadingSubtitle) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("حذف الملف", color = PaletteMutedRed) },
                onClick = {
                    showContextMenu = false
                    viewModel.deleteDownload(item.id)
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PaletteMutedRed, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

// Compact row for bottom sheet episode list (no card background, larger thumb, gradient progress bar)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CompactEpisodeRow(
    item: DownloadEntity,
    viewModel: MovieViewModel,
    onPlayClick: (String?) -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    dimAlpha: Float = 1f,
    isContextMenuTarget: Boolean = false,
    menuOpen: Boolean = false,
    lastTargetId: String? = null,
    onLongClick: () -> Unit = {},
    onCheckedChange: (Boolean) -> Unit = {},
    onDismissContextMenu: () -> Unit = {},
    onEnterMultiSelect: () -> Unit = {},
    onSubtitleFetched: () -> Unit = {},
    durationCache: MutableMap<String, Long> = mutableMapOf(),
    sizeCache: MutableMap<String, String> = mutableMapOf()
) {
    val context = LocalContext.current
    val isCompleted = item.status == "completed"
    val isPaused = item.status == "paused"
    val isDownloading = !isCompleted && !isPaused && item.status != "queued"

    // Duration + size are loaded OFF the main thread (MediaMetadataRetriever / file stat are blocking I/O)
    // and cached per-session so switching seasons back and forth doesn't re-read the file from disk.
    var durationSecs by remember(item.id) { mutableStateOf(durationCache[item.id] ?: -1L) }
    var fileSizeText by remember(item.id) { mutableStateOf(sizeCache[item.id] ?: "...") }
    LaunchedEffect(item.id, isCompleted) {
        if (isCompleted && (durationSecs < 0 || fileSizeText == "...")) {
            val (secs, sizeStr) = withContext(Dispatchers.IO) {
                val file = File(item.localFilePath)
                val len = if (file.exists()) file.length() else 0L
                val dur = try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    durStr?.toLongOrNull()?.let { it / 1000 } ?: 0L
                } catch (_: Exception) { 0L }
                dur to formatBytes(len)
            }
            if (durationSecs < 0) { durationCache[item.id] = secs; durationSecs = secs }
            if (fileSizeText == "...") { sizeCache[item.id] = sizeStr; fileSizeText = sizeStr }
        }
    }
    val subtitleDownloads by viewModel.subtitleDownloads.collectAsState(initial = emptyList())
    val compactScope = rememberCoroutineScope()
    var isDownloadingSubtitle by remember { mutableStateOf(false) }
    val hasSubtitle = remember(item.id, subtitleDownloads) {
        subtitleDownloads.any { sub ->
            if (item.mediaType == "tv")
                sub.tmdbId == item.mediaId && sub.season == item.season && sub.episode == item.episode
            else
                sub.tmdbId == item.mediaId
        }
    }

    // Pulse animation for active downloading thumbnail overlay
    val infiniteTransition = rememberInfiniteTransition(label = "downloadPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    // Use stillPath for episode thumbnail, fall back to posterPath
    val thumbUrl = if (item.stillPath.isNotEmpty()) {
        if (item.stillPath.startsWith("http")) item.stillPath else "https://image.tmdb.org/t/p/w300${item.stillPath}"
    } else {
        if (item.posterPath.startsWith("http")) item.posterPath else "https://image.tmdb.org/t/p/w300${item.posterPath}"
    }

    // Custom press effect (replaces default ripple) — subtle scale + alpha on touch-down
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.92f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (isContextMenuTarget) 1f else if (pressed) 0.98f else 1f, animationSpec = tween(150))

    // Watch progress (durationSecs / fileSizeText are loaded async + cached above)
    val prefs = context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE)
    val lastPos = prefs.getLong("pos_${item.id}", 0L)
    val progress = if (isCompleted && durationSecs > 0 && lastPos > 0) {
        (lastPos.toFloat() / 1000f / durationSecs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .alpha(
                pressAlpha * (
                    if (isContextMenuTarget) 1f
                    else if (!menuOpen && lastTargetId == item.id) 1f
                    else dimAlpha
                )
            )
    ) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            if (isSelectionMode) {
                                onCheckedChange(!isSelected)
                            } else {
                                if (isCompleted) onPlayClick(item.localFilePath)
                                else if (isPaused) viewModel.resumeDownload(item.id)
                                else if (!isCompleted) viewModel.pauseDownload(item.id)
                            }
                        },
                        onLongClick = {
                            if (isSelectionMode) {
                                onCheckedChange(!isSelected)
                            } else {
                                onLongClick()
                            }
                        }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular selection indicator (replaces Checkbox) — animates in/out with selection mode
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = scaleIn(spring()) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    CircularSelectionIndicator(
                        isSelected = isSelected,
                        onClick = { onCheckedChange(!isSelected) }
                    )
                }

                // Thumbnail with gradient progress bar at bottom
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 50.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (!isCompleted) Modifier.alpha(0.55f) else Modifier)
                ) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Pulse overlay during active download
                    if (isDownloading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(pulseAlpha)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        )
                    }
                    // Gradient progress bar at bottom of thumbnail
                    if (isCompleted && progress > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(5.dp)
                        ) {
                            // Track background
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                            )
                            // Active progress with gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color.Cyan, Color.Green)
                                        )
                                    )
                            )
                        }
                    }
                }

                // Details
                Column(
                    modifier = Modifier.weight(1f)
                        .then(if (!isCompleted) Modifier.alpha(0.55f) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Episode number + quality
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "الحلقة ${item.episode}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.quality,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        }
                        if (isDownloadingSubtitle) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                        } else if (hasSubtitle) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "ترجمة",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (isCompleted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (durationSecs > 0 && lastPos > 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val posSecs = lastPos / 1000
                                    val durMins = durationSecs / 60
                                    val durSecs = durationSecs % 60
                                    // Watched time (green) / total time (faded)
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)) {
                                                append("${posSecs / 60}:${String.format("%02d", posSecs % 60)}")
                                            }
                                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontWeight = FontWeight.Medium, fontFamily = JetBrainsMonoFontFamily)) {
                                                append("/$durMins:${String.format("%02d", durSecs)}")
                                            }
                                        },
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "—",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                    )
                                }
                            }
                            if (fileSizeText != "...") {
                                Text(
                                    text = fileSizeText,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            }
                        }
                    } else {
                        // Download progress — NO transparency
                        val formattedDownloaded = formatBytes(item.downloadedBytes)
                        val formattedTotal = formatBytes(item.totalBytes)
                        Text(
                            text = if (item.totalBytes == item.downloadedBytes) formattedDownloaded else "$formattedDownloaded / $formattedTotal",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontFamily = JetBrainsMonoFontFamily
                        )
                        LinearProgressIndicator(
                            progress = { item.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                // Play/Pause button — hidden when completed (whole card is clickable)
                if (!isCompleted) {
                    IconButton(
                        onClick = {
                            if (isPaused) viewModel.resumeDownload(item.id)
                            else viewModel.pauseDownload(item.id)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "استئناف" else "إيقاف مؤقت",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }

        // DropdownMenu anchored to this row when it is the long-press target
        DropdownMenu(
            expanded = isContextMenuTarget,
            onDismissRequest = onDismissContextMenu,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp
        ) {
            if (!isCompleted) {
                DropdownMenuItem(
                    text = { Text("مشاهدة ما تم تحميله") },
                    onClick = {
                        onDismissContextMenu()
                        val partialPath = File(context.filesDir, "downloads/${item.id}.mp4").absolutePath
                        if (File(partialPath).exists()) {
                            onPlayClick(partialPath)
                        } else {
                            android.widget.Toast.makeText(context, "الملف غير جاهز بعد", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                DropdownMenuItem(
                    text = { Text(if (isPaused) "استئناف التحميل" else "إيقاف التحميل") },
                    onClick = {
                        onDismissContextMenu()
                        if (isPaused) viewModel.resumeDownload(item.id)
                        else viewModel.pauseDownload(item.id)
                    },
                    leadingIcon = { Icon(if (isPaused) Icons.Default.FileDownload else Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            if (isCompleted) {
                DropdownMenuItem(
                    text = { Text("حفظ الفيديو في المعرض") },
                    onClick = {
                        onDismissContextMenu()
                        try {
                            val destDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
                            if (!destDir.exists()) destDir.mkdirs()
                            val safeTitle = item.title.replace("/", "_").replace("\\", "_")
                            val destFile = File(destDir, "$safeTitle.mp4")
                            File(item.localFilePath).copyTo(destFile, overwrite = true)
                            android.widget.Toast.makeText(context, "تم حفظ الفيديو للمعرض", android.widget.Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "خطأ أثناء الحفظ: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            DropdownMenuItem(
                text = { Text("تحميل الترجمة") },
                onClick = {
                    onDismissContextMenu()
                    isDownloadingSubtitle = true
                    onSubtitleFetched()
                    context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("has_fetched_subtitle_once", true).apply()
                    compactScope.launch {
                        val tmdbId = if (item.mediaType == "tv") item.mediaId.substringBefore("-s") else item.mediaId
                        val file = SubtitleHelper.fetchAndSaveMovieBoxSubtitle(
                            context, tmdbId, item.mediaType == "tv", item.season, item.episode, item.title, item.id
                        )
                        isDownloadingSubtitle = false
                        android.widget.Toast.makeText(
                            context,
                            if (file != null) "✓ تم تحميل الترجمة العربية" else "لم يتم العثور على ترجمة عربية",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                leadingIcon = {
                    if (isDownloadingSubtitle) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("حذف الملف", color = PaletteMutedRed) },
                onClick = {
                    onDismissContextMenu()
                    viewModel.deleteDownload(item.id)
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PaletteMutedRed, modifier = Modifier.size(18.dp)) }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("تحديد متعدد", fontWeight = FontWeight.Bold, color = PalettePrimary) },
                onClick = {
                    onEnterMultiSelect()
                },
                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PalettePrimary, modifier = Modifier.size(18.dp)) }
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFilesTab(
    context: android.content.Context,
    onNavigateToPlayer: (String, String, String) -> Unit
) {
    // Scan /storage/emulated/0/Movies/ for video files + user-picked files
    val prefs = context.getSharedPreferences("local_videos", android.content.Context.MODE_PRIVATE)
    var localFiles by remember { mutableStateOf<List<com.example.data.local.LocalVideoFile>>(emptyList()) }
    var selectedVideoForPlaylist by remember { mutableStateOf<String?>(null) }
    var playlists by remember { mutableStateOf<List<com.example.data.local.LocalPlaylist>>(emptyList()) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var currentPlaylistName by remember { mutableStateOf("") }

    // Load user-picked files from prefs
    fun loadUserPickedFiles(): List<String> {
        val json = prefs.getString("picked_files", "[]") ?: "[]"
        return try {
            com.example.data.local.UserPickedFileList.fromJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    fun saveUserPickedFiles(files: List<String>) {
        prefs.edit().putString("picked_files", com.example.data.local.UserPickedFileList.toJson(files)).apply()
    }
    fun loadPlaylists(): List<com.example.data.local.LocalPlaylist> {
        val json = prefs.getString("playlists", "[]") ?: "[]"
        return try {
            com.example.data.local.LocalPlaylistList.fromJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    fun savePlaylists(pl: List<com.example.data.local.LocalPlaylist>) {
        prefs.edit().putString("playlists", com.example.data.local.LocalPlaylistList.toJson(pl)).apply()
    }

    // Scan Movies directory on first composition
    LaunchedEffect(Unit) {
        playlists = loadPlaylists()
        val userPicked = loadUserPickedFiles()
        val files = mutableListOf<com.example.data.local.LocalVideoFile>()
        // Scan /storage/emulated/0/Movies/
        try {
            val moviesDir = java.io.File("/storage/emulated/0/Movies/")
            if (moviesDir.exists() && moviesDir.isDirectory) {
                val videoExts = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp")
                moviesDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.contains(".") && file.extension.lowercase() in videoExts) {
                        files.add(com.example.data.local.LocalVideoFile(
                            id = file.absolutePath,
                            name = file.nameWithoutExtension,
                            filePath = file.absolutePath,
                            size = file.length()
                        ))
                    }
                }
            }
        } catch (_: Exception) {}
        // Add user-picked files
        userPicked.forEach { path ->
            if (path.startsWith("content://")) {
                // content:// URI from file picker — query display name from content resolver
                val displayName = try {
                    val cursor = context.contentResolver.query(android.net.Uri.parse(path), null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) it.getString(idx) ?: "فيديو محلي" else "فيديو محلي"
                        } else "فيديو محلي"
                    } ?: "فيديو محلي"
                } catch (_: Exception) { "فيديو محلي" }
                files.add(com.example.data.local.LocalVideoFile(
                    id = path,
                    name = displayName,
                    filePath = path,
                    size = 0L
                ))
            } else {
                val f = java.io.File(path)
                if (f.exists()) {
                    files.add(com.example.data.local.LocalVideoFile(
                        id = f.absolutePath,
                        name = f.nameWithoutExtension,
                        filePath = f.absolutePath,
                        size = f.length()
                    ))
                }
            }
        }
        localFiles = files.distinctBy { it.id }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // Copy the selected file URI info
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        val name = if (nameIdx >= 0) it.getString(nameIdx) else "فيديو"
                        val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0L
                        // Save as a reference — we'll use content:// URI directly
                        val filePath = uri.toString()
                        val userPicked = loadUserPickedFiles()
                        if (filePath !in userPicked) {
                            saveUserPickedFiles(userPicked + filePath)
                            // Add to local files list
                            localFiles = localFiles + com.example.data.local.LocalVideoFile(
                                id = filePath,
                                name = name,
                                filePath = filePath,
                                size = size
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun removeFile(id: String) {
        localFiles = localFiles.filter { it.id != id }
        val userPicked = loadUserPickedFiles().filter { it != id }
        saveUserPickedFiles(userPicked)
        // Remove from playlists too
        var updatedPlaylists = playlists.map { pl ->
            pl.copy(videoIds = pl.videoIds.filter { it != id })
        }
        updatedPlaylists = updatedPlaylists.filter { it.videoIds.isNotEmpty() }
        savePlaylists(updatedPlaylists)
        playlists = updatedPlaylists
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header with add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "فيديوهات الجهاز (${localFiles.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            IconButton(onClick = {
                filePickerLauncher.launch(arrayOf("video/*"))
            }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة فيديو", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Playlists section
        if (playlists.isNotEmpty()) {
            Text(
                text = "قوائم التشغيل",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LazyColumn(
                modifier = Modifier.height((playlists.size * 60).dp.coerceAtMost(180.dp)),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(playlists, key = { it.id }) { pl ->
                    val playlistVideos = pl.videoIds.mapNotNull { id -> localFiles.find { it.id == id } }
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Navigate to playlist — just show videos in this playlist
                                // Show videos in playlist
                            },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(pl.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${playlistVideos.size} فيديو", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Video list
        if (localFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا توجد فيديوهات. اضغط + لإضافة فيديو",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(localFiles, key = { it.id }) { video ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigateToPlayer(video.id, video.name, video.filePath)
                            },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Thumbnail placeholder
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatBytes(video.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Add to playlist button
                            IconButton(onClick = {
                                selectedVideoForPlaylist = video.id
                                showCreatePlaylistDialog = true
                            }) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = "إضافة لقائمة تشغيل", tint = MaterialTheme.colorScheme.secondary)
                            }
                            // Remove button
                            IconButton(onClick = { removeFile(video.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "إزالة", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // Create/Add to playlist dialog
    if (showCreatePlaylistDialog && selectedVideoForPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("إضافة لقائمة تشغيل") },
            text = {
                Column {
                    Text("اختر قائمة تشغيل موجودة أو أنشئ واحدة جديدة:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentPlaylistName,
                        onValueChange = { currentPlaylistName = it },
                        label = { Text("اسم قائمة جديدة") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (playlists.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("أو اختر من القوائم الموجودة:", style = MaterialTheme.typography.labelMedium)
                        playlists.forEach { pl ->
                            TextButton(
                                onClick = {
                                    val updatedPlaylists = playlists.map {
                                        if (it.id == pl.id && selectedVideoForPlaylist !in it.videoIds) {
                                            it.copy(videoIds = it.videoIds + selectedVideoForPlaylist!!)
                                        } else it
                                    }
                                    savePlaylists(updatedPlaylists)
                                    playlists = updatedPlaylists
                                    showCreatePlaylistDialog = false
                                }
                            ) {
                                Text(pl.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (currentPlaylistName.isNotBlank()) {
                    TextButton(onClick = {
                        val newPlaylist = com.example.data.local.LocalPlaylist(
                            id = java.util.UUID.randomUUID().toString(),
                            name = currentPlaylistName,
                            videoIds = listOf(selectedVideoForPlaylist!!)
                        )
                        val updatedPlaylists = playlists + newPlaylist
                        savePlaylists(updatedPlaylists)
                        playlists = updatedPlaylists
                        currentPlaylistName = ""
                        showCreatePlaylistDialog = false
                    }) { Text("إنشاء") }
                }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("إلغاء") } }
        )
    }

}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0.0MB"
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return String.format(java.util.Locale.US, "%.1fGB", gb)
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1fMB", mb)
}
