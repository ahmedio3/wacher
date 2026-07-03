package com.example.ui.screens

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.DownloadEntity
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.RequestState
import java.io.File

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: MovieViewModel,
    onNavigateToPlayer: (String, String, String) -> Unit,
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
    var selectedSeriesIdForSheet by remember { mutableStateOf<String?>(null) }

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
                    .padding(bottom = 80.dp)
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
                                    // Use series poster (episode with empty stillPath has posterPath = series poster)
                                    val posterPath = playlistEpisodes.firstOrNull { it.stillPath.isEmpty() }?.posterPath
                                        ?: playlistEpisodes.firstOrNull()?.posterPath ?: ""
                                    
                                    PlaylistFolderCard(
                                        seriesTitle = parentTitle,
                                        posterPath = posterPath,
                                        episodesCount = playlistEpisodes.size,
                                        onClick = {
                                            selectedSeriesIdForSheet = mediaId
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

            // 100% HEIGHT MODAL BOTTOM SHEET FOR SERIES EPISODES LIST & SEASONS SWITCHER
            if (!selectedSeriesIdForSheet.isNullOrEmpty()) {
                val mediaId = selectedSeriesIdForSheet!!
                val playlistEpisodes = playlistGroups[mediaId] ?: emptyList()
                val parentTitle = playlistEpisodes.firstOrNull()?.title?.substringBefore(" - ") ?: "مسلسل"
                val posterPath = playlistEpisodes.firstOrNull()?.posterPath ?: ""

                SeriesDetailBottomSheet(
                    seriesId = mediaId,
                    seriesTitle = parentTitle,
                    posterPath = posterPath,
                    downloadedEpisodes = playlistEpisodes,
                    viewModel = viewModel,
                    onDismiss = { selectedSeriesIdForSheet = null },
                    onNavigateToPlayer = onNavigateToPlayer
                )
            }
        }
    }
}

// FULL SCREEN EPISODES SELECTOR MODAL BOTTOM SHEET (100% Height)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailBottomSheet(
    seriesId: String,
    seriesTitle: String,
    posterPath: String,
    downloadedEpisodes: List<DownloadEntity>,
    viewModel: MovieViewModel,
    onDismiss: () -> Unit,
    onNavigateToPlayer: (String, String, String) -> Unit
) {
    var selectedSeasonNumber by remember { mutableIntStateOf(1) }
    var showDownloadNewSheet by remember { mutableStateOf(false) }

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

    // Extracted seasons list
    val seasons = remember(tvDetailsState) {
        if (tvDetailsState is RequestState.Success) {
            tvDetailsState.data.seasons?.filter { it.seasonNumber > 0 } ?: emptyList()
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

    // Run season loader from TMDB to dynamically retrieve remaining episodes checklist
    LaunchedEffect(seriesId, selectedSeasonNumber) {
        val tvId = seriesId.toIntOrNull() ?: 0
        if (tvId > 0) {
            viewModel.fetchSeasonDetails(tvId, selectedSeasonNumber)
        }
    }

    val seasonDetailsStateMap by viewModel.seasonDetails.collectAsState()
    val seasonDetailState = seasonDetailsStateMap["$seriesId-$selectedSeasonNumber"]

    // Sub-calculated downloaded vs total in current season (sorted by episode ascending)
    val currentSeasonEpisodesList = downloadedEpisodes
        .filter { it.season == selectedSeasonNumber }
        .sortedBy { it.episode }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxHeight(1.0f) // 100% full screen height limit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Header Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                }
                Text(
                    text = seriesTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // Active Tab bar seasons list with statistics: e.g. "الموسم 1 (1/7)"
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
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

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { selectedSeasonNumber = s.seasonNumber }
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
                }
            }

            // Listing current downloaded or downloading episodes in season
            if (currentSeasonEpisodesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(currentSeasonEpisodesList, key = { it.id }) { item ->
                        CompactEpisodeRow(
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

            // Button to trigger download new episodes sheet at the bottom block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
        }

        // Secondary bottom sheet (70% Height)
        if (showDownloadNewSheet) {
            val context = LocalContext.current.applicationContext as android.app.Application
            val movieBoxViewModel: com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = com.example.ui.viewmodel.ViewModelFactory(context))

            com.example.ui.components.moviebox.MovieBoxDownloadSheet(
                movieTitle = seriesTitle,
                movieYear = null,
                mediaType = "tv",
                viewModel = movieBoxViewModel,
                onDismissRequest = { showDownloadNewSheet = false },
                onTryOtherMethod = { showDownloadNewSheet = false },
                onDownloadClick = { url, quality, s, ep ->
                    viewModel.requestDownload(
                        mediaId = seriesId,
                        title = seriesTitle,
                        posterPath = posterPath,
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
    }
}



// FOLDER CARD REPRESENTATION FOR EPISODES
@Composable
fun PlaylistFolderCard(
    seriesTitle: String,
    posterPath: String,
    episodesCount: Int,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
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
                    model = "https://image.tmdb.org/t/p/w185$posterPath",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seriesTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "تم تحميل $episodesCount حلقات من المسلسل",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showMenuSheet by remember { mutableStateOf(false) }

    val partialFilePath = java.io.File(context.filesDir, "downloads/${item.id}.mp4").absolutePath

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable {
                if (isCompleted) {
                    onPlayClick(item.localFilePath)
                } else if (isPaused) {
                    viewModel.resumeDownload(item.id)
                } else {
                    viewModel.pauseDownload(item.id)
                }
            }
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
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
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
                    // Different style from the download progress bar (secondary color, thinner)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    )
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
                    val fileSizeText = runCatching {
                        formatBytes(File(item.localFilePath).length())
                    }.getOrDefault("...")
                    Text(
                        text = "جاهز للمشاهدة بدون اتصال ($fileSizeText)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Action Buttons pause or delete
        IconButton(
            onClick = { showMenuSheet = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "خيارات",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
    
    if (showMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("خيارات التحميل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.deleteDownload(item.id)
                            showMenuSheet = false
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                    Text("حذف الملف", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                if (isCompleted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
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
                                showMenuSheet = false
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.Share, "حفظ للمعرض", tint = MaterialTheme.colorScheme.primary)
                        Text("حفظ الفيديو (في المعرض)", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (java.io.File(partialFilePath).exists()) {
                                    onPlayClick(partialFilePath)
                                } else {
                                    android.widget.Toast.makeText(context, "الملف غير جاهز بعد", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showMenuSheet = false
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, "تشغيل ما تم تحميله", tint = MaterialTheme.colorScheme.secondary)
                        Text("مشاهدة الفيديو المكتمل (${item.progress}%)", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// Compact row for bottom sheet episode list (no card background, larger thumb, gradient progress bar)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactEpisodeRow(
    item: DownloadEntity,
    viewModel: MovieViewModel,
    onPlayClick: (String?) -> Unit
) {
    val context = LocalContext.current
    val isCompleted = item.status == "completed"
    val posterUrl = if (item.posterPath.startsWith("http")) item.posterPath else "https://image.tmdb.org/t/p/w300${item.posterPath}"
    var showMenuSheet by remember { mutableStateOf(false) }

    // Watch progress
    val prefs = context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE)
    val lastPos = prefs.getLong("pos_${item.id}", 0L)
    val durationSecs = remember(item.id, isCompleted) {
        if (!isCompleted) 0L else {
            try {
                val file = File(item.localFilePath)
                if (file.exists()) {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    durStr?.toLongOrNull()?.let { it / 1000 } ?: 0L
                } else 0L
            } catch (_: Exception) { 0L }
        }
    }
    val progress = if (isCompleted && durationSecs > 0 && lastPos > 0) {
        (lastPos.toFloat() / 1000f / durationSecs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isCompleted) onPlayClick(item.localFilePath)
                    else if (item.status == "paused") viewModel.resumeDownload(item.id)
                    else viewModel.pauseDownload(item.id)
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Thumbnail with gradient progress bar at bottom
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 50.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient progress bar at bottom
                if (isCompleted && progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Episode number only (no season tag)
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
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (isCompleted) {
                    // File size only (small, no checkmark, no "جاهز" text)
                    val fileSizeText = runCatching {
                        formatBytes(File(item.localFilePath).length())
                    }.getOrDefault("...")

                    // Progress time + file size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (durationSecs > 0 && lastPos > 0) {
                            val posSecs = lastPos / 1000
                            val durMins = durationSecs / 60
                            val durSecs = durationSecs % 60
                            Text(
                                text = "${posSecs / 60}:${String.format("%02d", posSecs % 60)} / $durMins:${String.format("%02d", durSecs)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = fileSizeText,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Download progress
                    val formattedDownloaded = formatBytes(item.downloadedBytes)
                    val formattedTotal = formatBytes(item.totalBytes)
                    Text(
                        text = if (item.totalBytes == item.downloadedBytes) formattedDownloaded else "$formattedDownloaded / $formattedTotal",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
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

            // Menu button
            IconButton(
                onClick = { showMenuSheet = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "خيارات",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // Thin divider between episodes
        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp
        )
    }

    if (showMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("خيارات التحميل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.deleteDownload(item.id)
                            showMenuSheet = false
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error)
                    Text("حذف الملف", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                if (isCompleted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
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
                                showMenuSheet = false
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.Share, "حفظ للمعرض", tint = MaterialTheme.colorScheme.primary)
                        Text("حفظ الفيديو (في المعرض)", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val partialFilePath = java.io.File(context.filesDir, "downloads/${item.id}.mp4").absolutePath
                                if (java.io.File(partialFilePath).exists()) {
                                    onPlayClick(partialFilePath)
                                } else {
                                    android.widget.Toast.makeText(context, "الملف غير جاهز بعد", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showMenuSheet = false
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, "تشغيل ما تم تحميله", tint = MaterialTheme.colorScheme.secondary)
                        Text("مشاهدة الفيديو المكتمل (${item.progress}%)", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
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

@Composable
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0.0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}
