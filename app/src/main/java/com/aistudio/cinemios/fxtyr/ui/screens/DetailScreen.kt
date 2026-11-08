package com.aistudio.cinemios.fxtyr.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.aistudio.cinemios.fxtyr.ui.components.rememberPressState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.data.remote.*
import com.aistudio.cinemios.fxtyr.ui.components.IosIcons
import com.aistudio.cinemios.fxtyr.ui.components.ShowShareSheet
import com.aistudio.cinemios.fxtyr.ui.components.shareShow
import com.aistudio.cinemios.fxtyr.ui.components.detail.*
import com.aistudio.cinemios.fxtyr.ui.components.VideoPlayerView
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import com.aistudio.cinemios.fxtyr.ui.viewmodel.RequestState
import com.aistudio.cinemios.fxtyr.ui.components.StatusPickerSheet
import com.aistudio.cinemios.fxtyr.ui.components.SubtitleSourceSheet
import com.aistudio.cinemios.fxtyr.ui.components.moviebox.MovieBoxDownloadSheet
import com.aistudio.cinemios.fxtyr.data.remote.moviebox.viewmodel.MovieBoxViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.cinemios.fxtyr.ui.viewmodel.ViewModelFactory

data class PendingWatchlist(
    val id: String,
    val title: String,
    val posterPath: String,
    val mediaType: String,
    val rating: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    mediaId: Int,
    mediaType: String,
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onNavigateToPlayer: (String, String, String) -> Unit,
    onNavigateToDetails: (Int, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Loaded states
    val movieDetailsMap by viewModel.movieDetails.collectAsState()
    val tvDetailsMap by viewModel.tvDetails.collectAsState()
    val seasonDetailsMap by viewModel.seasonDetails.collectAsState()
    val downloadsList by viewModel.downloads.collectAsState(initial = emptyList())
    val movieCertificationsMap by viewModel.movieCertifications.collectAsState()
    val tvContentRatingsMap by viewModel.tvContentRatings.collectAsState()
    val movieSimilarMap by viewModel.movieSimilar.collectAsState()
    val movieRecommendationsMap by viewModel.movieRecommendations.collectAsState()
    val tvSimilarMap by viewModel.tvSimilar.collectAsState()
    val tvRecommendationsMap by viewModel.tvRecommendations.collectAsState()

    // Subtitle sheet state
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var subtitleSheetTmdbId by remember { mutableStateOf("") }
    var subtitleSheetIsTv by remember { mutableStateOf(false) }
    var subtitleSheetSeason by remember { mutableIntStateOf(0) }
    var subtitleSheetEpisode by remember { mutableIntStateOf(0) }
    var subtitleSheetTitle by remember { mutableStateOf("") }
    var subtitleSheetPoster by remember { mutableStateOf("") }

    // Status picker sheet state
    var showStatusPicker by remember { mutableStateOf(false) }
    var pendingWatchlist by remember { mutableStateOf<PendingWatchlist?>(null) }

    // Share sheet state
    var showShareSheet by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf<PendingShare?>(null) }

    // Selected season is hoisted so the floating subtitle action targets the same season
    val loadedTvDetails = (tvDetailsMap[mediaId] as? RequestState.Success)?.data
    var selectedTvSeason by remember(mediaId, loadedTvDetails?.id) {
        mutableIntStateOf(
            loadedTvDetails?.seasons?.firstOrNull { it.seasonNumber > 0 }?.seasonNumber ?: 1
        )
    }

    // Quality chooser states
    var showMovieBoxSheet by remember { mutableStateOf(false) }
    var pendingDownloadId by remember { mutableStateOf("") }
    var pendingDownloadTitle by remember { mutableStateOf("") }
    var pendingDownloadPoster by remember { mutableStateOf("") }
    var pendingDownloadStillPath by remember { mutableStateOf("") }
    var pendingDownloadMediaType by remember { mutableStateOf("") }
    var pendingDownloadSeason by remember { mutableIntStateOf(0) }
    var pendingDownloadEpisode by remember { mutableIntStateOf(0) }
    var pendingDownloadYear by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current.applicationContext as android.app.Application
    val shareContext = LocalContext.current
    val movieBoxViewModel: MovieBoxViewModel = viewModel(factory = ViewModelFactory(context))

    // Init fetch
    LaunchedEffect(mediaId) {
        if (mediaType == "movie") {
            viewModel.fetchMovieDetails(mediaId)
            viewModel.fetchMovieCertification(mediaId)
            viewModel.fetchMovieSimilar(mediaId)
            viewModel.fetchMovieRecommendations(mediaId)
        } else {
            viewModel.fetchTvDetails(mediaId)
            viewModel.fetchTvContentRating(mediaId)
            viewModel.fetchTvSimilar(mediaId)
            viewModel.fetchTvRecommendations(mediaId)
        }
    }

    // Log OPENED activity once when the detail title becomes available
    val detailTitle = remember(mediaType, movieDetailsMap[mediaId], tvDetailsMap[mediaId]) {
        if (mediaType == "movie") {
            val state = movieDetailsMap[mediaId]
            if (state is RequestState.Success) state.data.title ?: "" else ""
        } else {
            val state = tvDetailsMap[mediaId]
            if (state is RequestState.Success) state.data.name ?: "" else ""
        }
    }
    LaunchedEffect(detailTitle) {
        if (detailTitle.isNotBlank()) {
            viewModel.logActivity("OPENED", detailTitle)
        }
    }

    // Single source of truth for the floating capsule actions
    val watchlistTarget: PendingWatchlist? = remember(
        mediaType,
        movieDetailsMap[mediaId],
        tvDetailsMap[mediaId]
    ) {
        if (mediaType == "movie") {
            val state = movieDetailsMap[mediaId]
            if (state is RequestState.Success) {
                val movie = state.data
                PendingWatchlist(
                    id = movie.id.toString(),
                    title = movie.title ?: "",
                    posterPath = movie.posterPath ?: "",
                    mediaType = "movie",
                    rating = movie.voteAverage ?: 0.0
                )
            } else null
        } else {
            val state = tvDetailsMap[mediaId]
            if (state is RequestState.Success) {
                val tv = state.data
                PendingWatchlist(
                    id = tv.id.toString(),
                    title = tv.name ?: "",
                    posterPath = tv.posterPath ?: "",
                    mediaType = "tv",
                    rating = tv.voteAverage ?: 0.0
                )
            } else null
        }
    }

    val shareTarget: PendingShare? = watchlistTarget?.let {
        PendingShare(title = it.title, id = it.id, mediaType = it.mediaType)
    }

    val favoriteFlow = remember(mediaId) { viewModel.isItemInWatchlist(mediaId.toString()) }
    val isFavorited by favoriteFlow.collectAsState(initial = false)

    val openSubtitleSheet: () -> Unit = {
        if (mediaType == "movie") {
            val state = movieDetailsMap[mediaId]
            if (state is RequestState.Success) {
                val movie = state.data
                subtitleSheetTmdbId = movie.id.toString()
                subtitleSheetIsTv = false
                subtitleSheetSeason = 0
                subtitleSheetEpisode = 0
                subtitleSheetTitle = movie.title ?: ""
                subtitleSheetPoster = movie.posterPath ?: ""
                showSubtitleSheet = true
            }
        } else {
            val state = tvDetailsMap[mediaId]
            if (state is RequestState.Success) {
                val tv = state.data
                subtitleSheetTmdbId = tv.id.toString()
                subtitleSheetIsTv = true
                subtitleSheetSeason = selectedTvSeason
                subtitleSheetEpisode = 1
                subtitleSheetTitle = tv.name ?: ""
                subtitleSheetPoster = tv.posterPath ?: ""
                showSubtitleSheet = true
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = { },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Floating back button — same refined border as the seePlus top bar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .zIndex(1f)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Floating action capsule: share / subtitles / favorite
                if (watchlistTarget != null && shareTarget != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .zIndex(1f)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DetailCapsuleAction(
                            icon = IosIcons.Share,
                            contentDescription = "مشاركة العرض",
                            tint = MaterialTheme.colorScheme.onBackground,
                            onClick = {
                                shareTarget?.let {
                                    pendingShare = it
                                    showShareSheet = true
                                }
                            }
                        )
                        DetailCapsuleAction(
                            icon = Icons.Default.Subtitles,
                            contentDescription = "تحميل ترجمة",
                            tint = MaterialTheme.colorScheme.onBackground,
                            onClick = openSubtitleSheet
                        )
                        DetailCapsuleAction(
                            icon = if (isFavorited) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "قائمة المشاهدة",
                            tint = if (isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            onClick = {
                                watchlistTarget?.let {
                                    viewModel.toggleWatchlist(
                                        id = it.id,
                                        title = it.title,
                                        posterPath = it.posterPath,
                                        mediaType = it.mediaType,
                                        rating = it.rating
                                    )
                                }
                            },
                            onLongClick = {
                                watchlistTarget?.let {
                                    pendingWatchlist = it
                                    showStatusPicker = true
                                }
                            }
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = 44.dp)
                ) {
                    if (mediaType == "movie") {
                        val state = movieDetailsMap[mediaId] ?: RequestState.Loading
                        when (state) {
                            is RequestState.Success -> {
                                val movie = state.data
                                val activeLocalDownload = downloadsList.find { it.id == movie.id.toString() && it.status == "completed" }
                                val activeLocalFilePath = activeLocalDownload?.localFilePath ?: ""

                                    val movieCertState = movieCertificationsMap[movie.id]
                                    val movieCertification = if (movieCertState is RequestState.Success) movieCertState.data else null
                                    val movieSimState = movieSimilarMap[movie.id]
                                    val movieSimilarList = if (movieSimState is RequestState.Success) movieSimState.data else emptyList()
                                    val movieRecState = movieRecommendationsMap[movie.id]
                                    val movieRecommendationsList = if (movieRecState is RequestState.Success) movieRecState.data else emptyList()

                                    MovieDetailContent(
                                        movie = movie,
                                        certification = movieCertification,
                                        similarShows = movieSimilarList,
                                        recommendations = movieRecommendationsList,
                                        viewModel = viewModel,
                                        isPlayerPlaying = false,
                                        onPlayClick = {
                                            onNavigateToPlayer(movie.id.toString(), movie.title ?: "فيلم", activeLocalFilePath)
                                        },
                                        onDownloadClick = { id, title, poster, type ->
                                            pendingDownloadId = id
                                            pendingDownloadTitle = title
                                            pendingDownloadPoster = poster
                                            pendingDownloadMediaType = type
                                            pendingDownloadSeason = 0
                                            pendingDownloadEpisode = 0
                                            pendingDownloadYear = movie.releaseDate?.take(4)?.toIntOrNull()
                                            showMovieBoxSheet = true
                                        },
                                        onNavigateToDetails = { id, type -> onNavigateToDetails(id, type) }
                                    )
                            }
                            is RequestState.Loading -> DetailSkeleton()
                            is RequestState.Error -> ErrorContent(state.message)
                            else -> {}
                        }
                    } else {
                        val state = tvDetailsMap[mediaId] ?: RequestState.Loading
                        when (state) {
                            is RequestState.Success -> {
                                val tv = state.data
                                    val tvCertState = tvContentRatingsMap[tv.id]
                                    val tvCertification = if (tvCertState is RequestState.Success) tvCertState.data else null
                                    val tvSimState = tvSimilarMap[tv.id]
                                    val tvSimilarList = if (tvSimState is RequestState.Success) tvSimState.data else emptyList()
                                    val tvRecState = tvRecommendationsMap[tv.id]
                                    val tvRecommendationsList = if (tvRecState is RequestState.Success) tvRecState.data else emptyList()

                                    TvDetailContent(
                                        tv = tv,
                                        certification = tvCertification,
                                        similarShows = tvSimilarList,
                                        recommendations = tvRecommendationsList,
                                        viewModel = viewModel,
                                        seasonDetailsMap = seasonDetailsMap,
                                        selectedSeasonNumber = selectedTvSeason,
                                        onSeasonSelected = { selectedTvSeason = it },
                                        isPlayerPlaying = false,
                                        onPlayEpisode = { s, e ->
                                            val activeId = "${tv.id}-s$s-e$e"
                                            val activeLocalDownload = downloadsList.find { it.id == activeId && it.status == "completed" }
                                            val activeLocalFilePath = activeLocalDownload?.localFilePath ?: ""
                                            val episodeTitle = "${tv.name ?: "مسلسل"} - الموسم $s الحلقة $e"
                                            onNavigateToPlayer(activeId, episodeTitle, activeLocalFilePath)
                                        },
                                        onDownloadEpisode = { id, title, poster, type, stillPath, s, ep ->
                                            pendingDownloadId = id
                                            pendingDownloadTitle = title
                                            pendingDownloadPoster = poster
                                            pendingDownloadStillPath = stillPath
                                            pendingDownloadMediaType = type
                                            pendingDownloadSeason = s
                                            pendingDownloadEpisode = ep
                                            pendingDownloadYear = tv.firstAirDate?.take(4)?.toIntOrNull()
                                            showMovieBoxSheet = true
                                        },
                                        onDownloadFullSeries = {
                                            pendingDownloadId = tv.id.toString()
                                            pendingDownloadTitle = tv.name ?: ""
                                            pendingDownloadPoster = tv.posterPath ?: ""
                                            pendingDownloadMediaType = "tv"
                                            pendingDownloadSeason = 0
                                            pendingDownloadEpisode = 0
                                            pendingDownloadYear = tv.firstAirDate?.take(4)?.toIntOrNull()
                                            showMovieBoxSheet = true
                                        },
                                        onSubtitleDownloadClick = { id, title, poster, season, episode ->
                                            subtitleSheetTmdbId = id
                                            subtitleSheetIsTv = true
                                            subtitleSheetSeason = season
                                            subtitleSheetEpisode = episode
                                            subtitleSheetTitle = title
                                            subtitleSheetPoster = poster
                                            showSubtitleSheet = true
                                        },
                                        onNavigateToDetails = { id, type -> onNavigateToDetails(id, type) }
                                    )
                            }
                            is RequestState.Loading -> DetailSkeleton()
                            is RequestState.Error -> ErrorContent(state.message)
                            else -> {}
                        }
                    }
                }
            }
        }

        // CUSTOM iOS PREMIUM QUALITY SELECTION BOTTOM SHEET / CARD DIALOG
        if (showMovieBoxSheet) {
            val episodeStillPaths = remember(seasonDetailsMap) {
                val map = mutableMapOf<Pair<Int, Int>, String>()
                seasonDetailsMap.values.forEach { st ->
                    if (st is RequestState.Success) {
                        val seasonNum = st.data.seasonNumber
                        st.data.episodes?.forEach { ep ->
                            if (!ep.stillPath.isNullOrEmpty()) {
                                map[seasonNum to ep.episodeNumber] = ep.stillPath!!
                            }
                        }
                    }
                }
                map
            }
            MovieBoxDownloadSheet(
                movieTitle = if (pendingDownloadMediaType == "movie") pendingDownloadTitle else pendingDownloadTitle.split(" - ").firstOrNull()?.trim() ?: pendingDownloadTitle,
                movieYear = pendingDownloadYear,
                mediaType = pendingDownloadMediaType,
                seasonInfo = if (pendingDownloadSeason > 0) pendingDownloadSeason else null,
                episodeInfo = if (pendingDownloadEpisode > 0) pendingDownloadEpisode else null,
                viewModel = movieBoxViewModel,
                onDismissRequest = { showMovieBoxSheet = false },
                onTryOtherMethod = { showMovieBoxSheet = false },
                episodeStillPaths = episodeStillPaths,
                onDownloadClick = { url, quality, s, ep, still, headers ->
                    viewModel.requestDownload(
                        mediaId = pendingDownloadId,
                        title = pendingDownloadTitle,
                        posterPath = pendingDownloadPoster,
                        stillPath = still,
                        mediaType = pendingDownloadMediaType,
                        season = if (pendingDownloadMediaType == "tv") s else 0,
                        episode = if (pendingDownloadMediaType == "tv") ep else 0,
                        quality = quality,
                        customUrl = url,
                        customHeaders = headers
                    )
                    showMovieBoxSheet = false
                }
            )
        }

        // STATUS PICKER BOTTOM SHEET (long-press on bookmark)
        if (showStatusPicker && pendingWatchlist != null) {
            val p = pendingWatchlist!!
            val defaultStatus by viewModel.defaultWatchStatus.collectAsState()
            StatusPickerSheet(
                currentDefault = defaultStatus,
                onDismiss = { showStatusPicker = false; pendingWatchlist = null },
                onStatusSelected = { status ->
                    viewModel.saveToWatchlistWithStatus(p.id, p.title, p.posterPath, p.mediaType, p.rating, status)
                    showStatusPicker = false
                    pendingWatchlist = null
                    Toast.makeText(context, "تم الحفظ بـ \"$status\"", Toast.LENGTH_SHORT).show()
                },
                onSetAsDefault = { status ->
                    viewModel.setDefaultWatchStatus(status)
                }
            )
        }

        // SUBTITLE DOWNLOAD BOTTOM SHEET
        if (showSubtitleSheet) {
            val sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { it != SheetValue.Hidden }  // Block swipe-to-dismiss; close only via X button
            )
            ModalBottomSheet(
                onDismissRequest = { },
                sheetState = sheetState,
                dragHandle = null,  // Remove default drag handle to avoid scroll conflicts
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    SubtitleSourceSheet(
                        tmdbId = subtitleSheetTmdbId,
                        isTv = subtitleSheetIsTv,
                        season = subtitleSheetSeason,
                        episode = subtitleSheetEpisode,
                        titleFallback = subtitleSheetTitle,
                        onNavigateBack = { showSubtitleSheet = false },
                        onSubtitleLoaded = { file, language, langCode, source, name, matchedEpisode, batchId ->
                            viewModel.saveSubtitleDownload(
                                tmdbId = subtitleSheetTmdbId,
                                title = subtitleSheetTitle,
                                posterPath = subtitleSheetPoster,
                                language = language,
                                languageCode = langCode,
                                source = source,
                                localFilePath = file.absolutePath,
                                mediaType = if (subtitleSheetIsTv) "tv" else "movie",
                                season = subtitleSheetSeason,
                                episode = if (matchedEpisode > 0) matchedEpisode else subtitleSheetEpisode,
                                fileName = name,
                                batchId = batchId,
                                releaseName = name
                            )
                            // Keep sheet open so user can download more subtitles
                        },
                        onBatchComplete = { batchId, count, releaseName ->
                            val msg = if (count > 1) "تم تحميل $count ترجمة ($releaseName)"
                                      else "تم تحميل الترجمة ($releaseName)"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // SHARE SHEET (same feature used by long-press on show cards)
        pendingShare?.let { target ->
            ShowShareSheet(
                visible = showShareSheet,
                title = target.title,
                id = target.id,
                mediaType = target.mediaType,
                onDismiss = { showShareSheet = false },
                onNativeShare = {
                    shareShow(shareContext, target.title, target.id, target.mediaType)
                    showShareSheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailCapsuleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

