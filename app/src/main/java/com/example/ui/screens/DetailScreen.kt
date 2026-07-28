package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.ui.components.bouncyOverscroll
import com.example.ui.components.rememberPressState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.remote.*
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.utils.isLatinText
import com.example.ui.components.VideoPlayerView
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.RequestState
import com.example.ui.components.StatusPickerSheet
import com.example.ui.components.SubtitleSourceSheet
import com.example.ui.components.moviebox.MovieBoxDownloadSheet
import com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.ViewModelFactory

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

    // Share state
    var showNativeShare by remember { mutableStateOf(false) }

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

    val appBarTitle = remember(mediaType, mediaId, movieDetailsMap[mediaId], tvDetailsMap[mediaId]) {
        if (mediaType == "movie") {
            val state = movieDetailsMap[mediaId]
            if (state is RequestState.Success) state.data.title ?: "تفاصيل الفيلم" else "تفاصيل الفيلم"
        } else {
            val state = tvDetailsMap[mediaId]
            if (state is RequestState.Success) state.data.name ?: "تفاصيل المسلسل" else "تفاصيل المسلسل"
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
                // Immersive floating header overlay (back button + title pill) drawn above the backdrop
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .zIndex(1f)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = appBarTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (isLatinText(appBarTitle)) JetBrainsMonoFontFamily else null,
                                    textDirection = if (isLatinText(appBarTitle)) TextDirection.Ltr else TextDirection.Rtl
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .bouncyOverscroll()
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
                                        onSubtitleDownloadClick = { id, title, poster ->
                                            subtitleSheetTmdbId = id
                                            subtitleSheetIsTv = false
                                            subtitleSheetSeason = 0
                                            subtitleSheetEpisode = 0
                                            subtitleSheetTitle = title
                                            subtitleSheetPoster = poster
                                            showSubtitleSheet = true
                                        },
                                        onLongPressWatchlist = {
                                            pendingWatchlist = PendingWatchlist(
                                                id = movie.id.toString(),
                                                title = movie.title ?: "",
                                                posterPath = movie.posterPath ?: "",
                                                mediaType = "movie",
                                                rating = movie.voteAverage ?: 0.0
                                            )
                                            showStatusPicker = true
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
                                        onLongPressWatchlist = {
                                            pendingWatchlist = PendingWatchlist(
                                                id = tv.id.toString(),
                                                title = tv.name ?: "",
                                                posterPath = tv.posterPath ?: "",
                                                mediaType = "tv",
                                                rating = tv.voteAverage ?: 0.0
                                            )
                                            showStatusPicker = true
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
                onDownloadClick = { url, quality, s, ep, still ->
                    viewModel.requestDownload(
                        mediaId = pendingDownloadId,
                        title = pendingDownloadTitle,
                        posterPath = pendingDownloadPoster,
                        stillPath = still,
                        mediaType = pendingDownloadMediaType,
                        season = if (pendingDownloadMediaType == "tv") s else 0,
                        episode = if (pendingDownloadMediaType == "tv") ep else 0,
                        quality = quality,
                        customUrl = url
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
                                batchId = batchId
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
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MovieDetailContent(
    movie: TmdbMovieDetails,
    certification: String? = null,
    similarShows: List<TmdbMediaItem> = emptyList(),
    recommendations: List<TmdbMediaItem> = emptyList(),
    viewModel: MovieViewModel,
    isPlayerPlaying: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: (String, String, String, String) -> Unit,
    onSubtitleDownloadClick: (String, String, String) -> Unit = { _, _, _ -> },
    onLongPressWatchlist: () -> Unit = {},
    onNavigateToDetails: (Int, String) -> Unit = { _, _ -> }
) {
    val backupUrl = "https://image.tmdb.org/t/p/w780${movie.backdropPath ?: movie.posterPath}"
    val posterUrl = "https://image.tmdb.org/t/p/w342${movie.posterPath}"
    val isFavorited by viewModel.isItemInWatchlist(movie.id.toString()).collectAsState(initial = false)

    Column {
        if (!isPlayerPlaying) {
            // Double layered Poster Card with high backdrop glare (Hidden during inline playback)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(264.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = backupUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                            )
                        )
                )

                // Centered Overlay Poster
                AsyncImage(
                    model = posterUrl,
                    contentDescription = movie.title,
                    modifier = Modifier
                        .width(110.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                        .align(Alignment.BottomCenter),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Titles and Meta row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = movie.title ?: "فيلم سينمائي",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (isLatinText(movie.title ?: "")) JetBrainsMonoFontFamily else null,
                    textDirection = if (isLatinText(movie.title ?: "")) TextDirection.Ltr else TextDirection.Unspecified
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Badges row: Year | Rating | Duration | Certification
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = movie.releaseDate?.take(4) ?: "مجهول",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (isLatinText(movie.releaseDate?.take(4) ?: "")) JetBrainsMonoFontFamily else null,
                        textDirection = if (isLatinText(movie.releaseDate?.take(4) ?: "")) TextDirection.Ltr else TextDirection.Unspecified
                    )
                )
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format("%.1f", movie.voteAverage ?: 0.0),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            textDirection = TextDirection.Ltr
                        )
                    )
                }
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
                Text(
                    text = "${movie.runtime ?: 120} دقيقة",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (certification != null) {
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                            Text(
                                text = certification,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
                            )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Genre chips
            FlowGenresRow(genres = movie.genres ?: emptyList())

            Spacer(modifier = Modifier.height(20.dp))

            // iOS Play and Download Button Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Direct Play Button (Main iOS fill)
                Button(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                        Text("مشاهدة الآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                // Add Watchlist Action Circle (tap = save/delete, long-press = status picker)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(
                            onClick = {
                                viewModel.toggleWatchlist(
                                    id = movie.id.toString(),
                                    title = movie.title ?: "",
                                    posterPath = movie.posterPath ?: "",
                                    mediaType = "movie",
                                    rating = movie.voteAverage ?: 0.0
                                )
                            },
                            onLongClick = onLongPressWatchlist
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorited) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "قائمة المشاهدة",
                        tint = if (isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }

                // Download Simulated Action Circle
                IconButton(
                    onClick = {
                        onDownloadClick(
                            movie.id.toString(),
                            movie.title ?: "",
                            movie.posterPath ?: "",
                            "movie"
                        )
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowCircleDown,
                        contentDescription = "تحميل المشاهدة أوفلاين",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Subtitle download action circle
                IconButton(
                    onClick = {
                        onSubtitleDownloadClick(
                            movie.id.toString(),
                            movie.title ?: "",
                            movie.posterPath ?: ""
                        )
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "تحميل ترجمة",
                        tint = Color.Black
                    )
                }

                // Share action circle
                IconButton(
                    onClick = { showNativeShare = true },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "مشاركة",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Overviews block - expandable
            var overviewExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "قصة الفيلم",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Right
                )
                if ((movie.overview?.length ?: 0) > 150) {
                    TextButton(onClick = { overviewExpanded = !overviewExpanded }) {
                        Text(if (overviewExpanded) "أقل" else "المزيد", fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            val displayOverview = if (movie.overview.isNullOrEmpty()) "لا يتوفر نص القصة باللغة العربية حالياً." else movie.overview
            val finalOverview = if (!overviewExpanded && (displayOverview.length > 150)) displayOverview.take(150) + "..." else displayOverview
            Text(
                text = finalOverview,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    fontFamily = if (isLatinText(displayOverview)) JetBrainsMonoFontFamily else null,
                    textDirection = if (isLatinText(displayOverview)) TextDirection.Ltr else TextDirection.Unspecified
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = if (isLatinText(displayOverview)) TextAlign.Left else TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Cast row
            val castList = movie.credits?.cast ?: emptyList()
            if (castList.isNotEmpty()) {
                Text(
                    text = "طاقم العمل",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    modifier = Modifier.bouncyOverscroll(isVertical = false).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    items(castList.take(10)) { cast ->
                        CastCompactCircleCard(cast)
                    }
                }
            }

            // Similar Shows
            if (similarShows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "عروض مشابهة",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                ShowsHorizontalRow(shows = similarShows, onItemClick = onNavigateToDetails)
            }

            // Recommendations
            if (recommendations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "قد يعجبك أيضاً",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                ShowsHorizontalRow(shows = recommendations, onItemClick = onNavigateToDetails)
            }

            // Spacer for bottom area
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Share dialog
    if (showNativeShare) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, "شاهد ${movie.title ?: "هذا الفيلم"} على ووتشيرا!\nhttps://watchera.com/show/movie/${movie.id}")
        }
        androidx.compose.ui.platform.LocalContext.current.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة"))
        showNativeShare = false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TvDetailContent(
    tv: TmdbTvDetails,
    certification: String? = null,
    similarShows: List<TmdbMediaItem> = emptyList(),
    recommendations: List<TmdbMediaItem> = emptyList(),
    viewModel: MovieViewModel,
    seasonDetailsMap: Map<String, RequestState<TmdbSeasonDetails>>,
    isPlayerPlaying: Boolean,
    onPlayEpisode: (Int, Int) -> Unit,
    onDownloadEpisode: (String, String, String, String, String, Int, Int) -> Unit,
    onDownloadFullSeries: () -> Unit = {},
    onSubtitleDownloadClick: (String, String, String, Int, Int) -> Unit = { _, _, _, _, _ -> },
    onLongPressWatchlist: () -> Unit = {},
    onNavigateToDetails: (Int, String) -> Unit = { _, _ -> }
) {
    val backupUrl = "https://image.tmdb.org/t/p/w780${tv.backdropPath ?: tv.posterPath}"
    val posterUrl = "https://image.tmdb.org/t/p/w342${tv.posterPath}"
    val isFavorited by viewModel.isItemInWatchlist(tv.id.toString()).collectAsState(initial = false)

    // Season switcher selector states
    val validSeasons = tv.seasons?.filter { it.seasonNumber > 0 } ?: emptyList()
    var selectedSeasonNumber by remember { mutableIntStateOf(if (validSeasons.isNotEmpty()) validSeasons[0].seasonNumber else 1) }

    LaunchedEffect(selectedSeasonNumber) {
        viewModel.fetchSeasonDetails(tv.id, selectedSeasonNumber)
    }

    Column {
        if (!isPlayerPlaying) {
            // Double layered Backdrop Card (Hidden during inline playback)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(264.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = backupUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                            )
                        )
                )

                // Centered Overlay Poster
                AsyncImage(
                    model = posterUrl,
                    contentDescription = tv.name,
                    modifier = Modifier
                        .width(110.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                        .align(Alignment.BottomCenter),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Titles and Meta
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = tv.name ?: "مسلسل درامي",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (isLatinText(tv.name ?: "")) JetBrainsMonoFontFamily else null,
                    textDirection = if (isLatinText(tv.name ?: "")) TextDirection.Ltr else TextDirection.Unspecified
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Badges row: Year | Rating | Seasons | Certification
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = tv.firstAirDate?.take(4) ?: "مجهول",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (isLatinText(tv.firstAirDate?.take(4) ?: "")) JetBrainsMonoFontFamily else null,
                        textDirection = if (isLatinText(tv.firstAirDate?.take(4) ?: "")) TextDirection.Ltr else TextDirection.Unspecified
                    )
                )
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format("%.1f", tv.voteAverage ?: 0.0),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            textDirection = TextDirection.Ltr
                        )
                    )
                }
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
                Text(
                    text = "${tv.seasons?.size ?: 1} مواسم",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (certification != null) {
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                            Text(
                                text = certification,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
                            )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            FlowGenresRow(genres = tv.genres ?: emptyList())

            Spacer(modifier = Modifier.height(20.dp))

            // Action bar: Download series / Bookmark / Subtitles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Download full series button (main action, weight 1)
                Button(
                    onClick = onDownloadFullSeries,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            "تحميل المسلسل كاملًا",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Bookmark icon button (with long-press for status picker)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(
                            onClick = {
                                viewModel.toggleWatchlist(
                                    id = tv.id.toString(),
                                    title = tv.name ?: "",
                                    posterPath = tv.posterPath ?: "",
                                    mediaType = "tv",
                                    rating = tv.voteAverage ?: 0.0
                                )
                            },
                            onLongClick = onLongPressWatchlist
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorited) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "قائمة المشاهدة",
                        tint = if (isFavorited) Color.White else MaterialTheme.colorScheme.onBackground
                    )
                }

                // Subtitle download icon button
                IconButton(
                    onClick = {
                        onSubtitleDownloadClick(
                            tv.id.toString(),
                            tv.name ?: "",
                            tv.posterPath ?: "",
                            selectedSeasonNumber,
                            1
                        )
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "تحميل ترجمة",
                        tint = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Overviews bloc
            Text(
                text = "قصة المسلسل",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (tv.overview.isNullOrEmpty()) "لا يتوفر نص القصة باللغة العربية لهذا المسلسل حالياً." else tv.overview,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    fontFamily = if (isLatinText(tv.overview ?: "")) JetBrainsMonoFontFamily else null,
                    textDirection = if (isLatinText(tv.overview ?: "")) TextDirection.Ltr else TextDirection.Unspecified
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = if (isLatinText(tv.overview ?: "")) TextAlign.Left else TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SEASONS SELECTOR AND EPISODES LISTING
            if (validSeasons.isNotEmpty()) {
                Text(
                    text = "الحلقات والمواسم",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Season Selector Horizontal Row
                LazyRow(
                    modifier = Modifier
                        .bouncyOverscroll(isVertical = false)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                ) {
                    items(validSeasons) { s ->
                        val selected = selectedSeasonNumber == s.seasonNumber
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedSeasonNumber = s.seasonNumber }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "الموسم ${s.seasonNumber}",
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Episodes list under chosen Season key
                val seasonKey = "${tv.id}-$selectedSeasonNumber"
                val seasonState = seasonDetailsMap[seasonKey] ?: RequestState.Loading

                when (seasonState) {
                    is RequestState.Success -> {
                        val episodes = seasonState.data.episodes ?: emptyList()
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            episodes.forEach { episode ->
                                EpisodeRowCard(
                                    episode = episode,
                                    onPlay = { onPlayEpisode(selectedSeasonNumber, episode.episodeNumber) },
                                    onDownload = {
                                        onDownloadEpisode(
                                            tv.id.toString(),
                                            tv.name ?: "",
                                            tv.posterPath ?: "",
                                            "tv",
                                            episode.stillPath ?: "",
                                            selectedSeasonNumber,
                                            episode.episodeNumber
                                        )
                                    },
                                    onDownloadSubtitle = {
                                        onSubtitleDownloadClick(
                                            tv.id.toString(),
                                            tv.name ?: "${tv.id}",
                                            tv.posterPath ?: "",
                                            selectedSeasonNumber,
                                            episode.episodeNumber
                                        )
                                    }
                                )
                            }
                        }
                    }
                    is RequestState.Loading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)))
                                )
                            }
                        }
                    }
                    is RequestState.Error -> {
                        Text(
                            text = "فشل تحميل الحلقات لهذا الموسم.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRowCard(
    episode: TmdbEpisode,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDownloadSubtitle: () -> Unit = {}
) {
    val backdropUrl = "https://image.tmdb.org/t/p/w300${episode.stillPath}"

    // Custom press effect (replaces default ripple) — subtle scale + alpha on touch-down
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.91f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (pressed) 0.92f else 1f, animationSpec = tween(150))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .alpha(pressAlpha)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
                onLongClick = onDownloadSubtitle
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Image
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            if (!episode.stillPath.isNullOrEmpty()) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Details Block
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "الحلقة ${episode.episodeNumber}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = episode.name ?: "بدون عنوان",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (isLatinText(episode.name ?: "")) JetBrainsMonoFontFamily else null,
                    textDirection = if (isLatinText(episode.name ?: "")) TextDirection.Ltr else TextDirection.Unspecified
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Quick action button for download (subtitle moved to long-press)
        Button(
            onClick = onDownload,
            modifier = Modifier
                .height(36.dp)
                .width(72.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(imageVector = Icons.Default.ArrowCircleDown, contentDescription = "تحميل حلقة أوفلاين", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun FlowGenresRow(genres: List<TmdbGenre>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .bouncyOverscroll(isVertical = false),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        genres.forEach { genre ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = genre.name ?: "",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontFamily = if (isLatinText(genre.name ?: "")) JetBrainsMonoFontFamily else null,
                        textDirection = if (isLatinText(genre.name ?: "")) TextDirection.Ltr else TextDirection.Unspecified
                    )
                )
            }
        }
    }
}

@Composable
fun CastCompactCircleCard(cast: TmdbCast) {
    val faceUrl = "https://image.tmdb.org/t/p/w185${cast.profilePath}"

    Column(
        modifier = Modifier.width(66.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!cast.profilePath.isNullOrEmpty()) {
                AsyncImage(
                    model = faceUrl,
                    contentDescription = cast.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = cast.name ?: "ممثل",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DetailSkeleton() {
    Column(modifier = Modifier.padding(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .height(24.dp)
                .fillMaxWidth(0.5f)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)))
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .height(14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .height(14.dp)
                .fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)))
        )
    }
}

@Composable
fun ErrorContent(msg: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "تعذر تعبئة التفاصيل: $msg", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}

@Composable
fun ShowsHorizontalRow(
    shows: List<TmdbMediaItem>,
    onItemClick: (Int, String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .bouncyOverscroll(isVertical = false)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
    ) {
        items(shows.take(20)) { show ->
            ShowsHorizontalCard(show = show, onClick = onItemClick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShowsHorizontalCard(
    show: TmdbMediaItem,
    onClick: (Int, String) -> Unit
) {
    val posterUrl = "https://image.tmdb.org/t/p/w185${show.posterPath}"
    val title = show.title ?: show.name ?: "غير معروف"
    val type = show.mediaType ?: "movie"
    val (interactionSource, pressed) = rememberPressState()
    val pressScale by animateFloatAsState(if (pressed) 0.95f else 1f, animationSpec = tween(150))

    Column(
        modifier = Modifier
            .width(120.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(show.id, type) }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.71f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!show.posterPath.isNullOrEmpty()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            val voteAvg = show.voteAverage
            if (voteAvg != null && voteAvg > 0) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(8.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", show.voteAverage),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

