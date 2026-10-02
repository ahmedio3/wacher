package com.aistudio.cinemios.fxtyr.ui.components.downloads

import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.data.local.DownloadEntity
import com.aistudio.cinemios.fxtyr.data.local.SeasonMetaEntity
import com.aistudio.cinemios.fxtyr.data.remote.TmdbSeason
import com.aistudio.cinemios.fxtyr.ui.components.DownloadedSubtitleBrowser
import com.aistudio.cinemios.fxtyr.ui.components.SubtitleBatchSheet
import com.aistudio.cinemios.fxtyr.ui.components.SubtitleSourceSheet
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteMutedRed
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import com.aistudio.cinemios.fxtyr.ui.viewmodel.RequestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    com.aistudio.cinemios.fxtyr.data.remote.TmdbSeason(
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
                com.aistudio.cinemios.fxtyr.data.remote.TmdbSeason(
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
                            text = { Text("تحديد متعدد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                seasonMenuSeason = null
                                selectionMode = true
                                selectedIds = downloadedEpisodes.filter { it.season == s.seasonNumber }.map { it.id }.toSet()
                            },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
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
                                text = "لازم تضغط مطوّلًا على الحلقة > بعدين تحميل الترجمة. أو عمتا الترجمة بتتحمل تلقائي لما تفتح الحلقة بس لازم نت يعني فحمل الترجمة أضمن يا غبي",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black,
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
            val movieBoxViewModel: com.aistudio.cinemios.fxtyr.data.remote.moviebox.viewmodel.MovieBoxViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel(factory = com.aistudio.cinemios.fxtyr.ui.viewmodel.ViewModelFactory(appContext))

            val episodeStillPaths = remember(downloads) {
                downloads.filter { !it.stillPath.isNullOrEmpty() }
                    .associate { (it.season to it.episode) to it.stillPath!! }
            }

            val movieBoxSubjectId = remember(downloadedEpisodes) {
                downloadedEpisodes.firstNotNullOfOrNull { dl ->
                    Regex("""/(?:dash|stream)/(\d+)""").find(dl.sourceUrl)?.groupValues?.get(1)
                        ?: if (dl.mediaId.length >= 10 && dl.mediaId.all { it.isDigit() }) dl.mediaId else null
                }
            }

            com.aistudio.cinemios.fxtyr.ui.components.moviebox.MovieBoxDownloadSheet(
                movieTitle = seriesTitle,
                movieYear = null,
                mediaType = "tv",
                viewModel = movieBoxViewModel,
                initialSubjectId = movieBoxSubjectId,
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
                onDownloadClick = { url, quality, s, ep, still, headers ->
                    viewModel.requestDownload(
                        mediaId = seriesId,
                        title = seriesTitle,
                        posterPath = posterPath,
                        stillPath = still,
                        mediaType = "tv",
                        season = s,
                        episode = ep,
                        quality = quality,
                        customUrl = url,
                        customHeaders = headers
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
