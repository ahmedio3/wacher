package com.aistudio.cinemios.fxtyr.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.cinemios.fxtyr.data.local.RecentViewedItem
import com.aistudio.cinemios.fxtyr.ui.components.SkeletonItem
import com.aistudio.cinemios.fxtyr.ui.components.home.*
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import com.aistudio.cinemios.fxtyr.ui.viewmodel.RequestState

data class PendingShare(val title: String, val id: String, val mediaType: String)

@Composable
fun HomeScreen(
    viewModel: MovieViewModel,
    onNavigateToDetails: (Int, String) -> Unit,
    onNavigateToMovieBoxDetails: (String, String, String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWatchlist: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val watchlistItems by viewModel.watchlist.collectAsState()
    val trendingMoviesState by viewModel.trendingMovies.collectAsState()
    val trendingTvState by viewModel.trendingTv.collectAsState()
    val context = LocalContext.current

    var showShareSheet by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf<PendingShare?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
    ) {
        // 1. Sleek Top Header
        HomeHeader()

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Featured Carousel (Top 5 trending movies)
        when (val trendingState = trendingMoviesState) {
            is RequestState.Success -> {
                val featured = trendingState.data.take(5)
                if (featured.isNotEmpty()) {
                    FeaturedCarousel(
                        items = featured,
                        onItemClick = { item ->
                            viewModel.recordRecentView(
                                RecentViewedItem(
                                    id = item.id.toString(),
                                    title = item.title ?: item.name ?: "",
                                    posterPath = item.posterPath ?: "",
                                    mediaType = "movie",
                                    year = item.releaseDate?.take(4) ?: "",
                                    rating = item.voteAverage ?: 0.0,
                                    isMovieBox = false
                                )
                            )
                            onNavigateToDetails(item.id, "movie")
                        }
                    )
                }
            }
            is RequestState.Loading -> {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SkeletonItem(width = 360.dp, height = 210.dp, cornerRadius = 24.dp)
                }
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Watchlist Section (قائمتي) — shown if not empty
        if (watchlistItems.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "قائمتي",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = onNavigateToWatchlist) {
                    Text("عرض الكل", style = MaterialTheme.typography.labelMedium)
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(watchlistItems, key = { it.id }) { item ->
                    WatchlistPosterCard(
                        modifier = Modifier.width(110.dp),
                        item = item,
                        onClick = {
                            viewModel.recordRecentView(
                                RecentViewedItem(
                                    id = item.id,
                                    title = item.title,
                                    posterPath = item.posterPath,
                                    mediaType = item.mediaType,
                                    rating = item.rating,
                                    isMovieBox = item.id.startsWith("mb_")
                                )
                            )
                            if (item.id.startsWith("mb_")) {
                                onNavigateToMovieBoxDetails(item.id.removePrefix("mb_"), item.mediaType, item.title, item.posterPath)
                            } else {
                                val type = if (item.mediaType == "tv") "tv" else "movie"
                                try { onNavigateToDetails(item.id.split("-")[0].toInt(), type) }
                                catch (_: Exception) { onNavigateToDetails(item.id.hashCode(), type) }
                            }
                        },
                        isInMyList = true,
                        onToggleMyList = { viewModel.toggleWatchlist(item.id, item.title, item.posterPath, item.mediaType, item.rating) },
                        onShare = { pendingShare = PendingShare(item.title, item.id, item.mediaType); showShareSheet = true }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 4. Trending Movies (الأفلام الرائجة)
        MediaCategoryCarousel(
            title = "الأفلام الرائجة هذا الأسبوع",
            icon = Icons.Default.Movie,
            state = trendingMoviesState,
            onItemClick = { item ->
                viewModel.recordRecentView(
                    RecentViewedItem(
                        id = item.id.toString(),
                        title = item.title ?: item.name ?: "",
                        posterPath = item.posterPath ?: "",
                        mediaType = "movie",
                        year = item.releaseDate?.take(4) ?: "",
                        rating = item.voteAverage ?: 0.0,
                        isMovieBox = false
                    )
                )
                onNavigateToDetails(item.id, "movie")
            },
            isInMyList = { watchlistItems.any { w -> w.id == it.id.toString() } },
            onToggleMyList = { item ->
                viewModel.toggleWatchlist(item.id.toString(), item.title ?: item.name ?: "", item.posterPath ?: "", item.mediaType ?: "movie", item.voteAverage ?: 0.0)
            },
            onShare = { item ->
                pendingShare = PendingShare(item.title ?: item.name ?: "", item.id.toString(), item.mediaType ?: "movie")
                showShareSheet = true
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Trending TV Shows (المسلسلات الرائجة)
        MediaCategoryCarousel(
            title = "المسلسلات الرائجة هذا الأسبوع",
            icon = Icons.Default.LiveTv,
            state = trendingTvState,
            onItemClick = { item ->
                viewModel.recordRecentView(
                    RecentViewedItem(
                        id = item.id.toString(),
                        title = item.title ?: item.name ?: "",
                        posterPath = item.posterPath ?: "",
                        mediaType = "tv",
                        year = item.firstAirDate?.take(4) ?: "",
                        rating = item.voteAverage ?: 0.0,
                        isMovieBox = false
                    )
                )
                onNavigateToDetails(item.id, "tv")
            },
            isInMyList = { watchlistItems.any { w -> w.id == it.id.toString() } },
            onToggleMyList = { item ->
                viewModel.toggleWatchlist(item.id.toString(), item.title ?: item.name ?: "", item.posterPath ?: "", item.mediaType ?: "movie", item.voteAverage ?: 0.0)
            },
            onShare = { item ->
                pendingShare = PendingShare(item.title ?: item.name ?: "", item.id.toString(), item.mediaType ?: "movie")
                showShareSheet = true
            }
        )

        Spacer(modifier = Modifier.height(100.dp))
    }

    pendingShare?.let {
        ShowShareSheet(
            visible = showShareSheet,
            title = it.title,
            id = it.id,
            mediaType = it.mediaType,
            onDismiss = { showShareSheet = false },
            onNativeShare = {
                shareShow(context, it.title, it.id, it.mediaType)
                showShareSheet = false
            }
        )
    }
}
