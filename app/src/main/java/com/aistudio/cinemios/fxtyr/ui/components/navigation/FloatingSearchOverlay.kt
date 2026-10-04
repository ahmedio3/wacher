package com.aistudio.cinemios.fxtyr.ui.components.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.data.local.RecentViewedItem
import com.aistudio.cinemios.fxtyr.ui.components.ShowShareSheet
import com.aistudio.cinemios.fxtyr.ui.components.home.SearchGridCard
import com.aistudio.cinemios.fxtyr.ui.components.home.SearchGridSkeleton
import com.aistudio.cinemios.fxtyr.ui.components.home.TmdbPosterCard
import com.aistudio.cinemios.fxtyr.ui.components.shareShow
import com.aistudio.cinemios.fxtyr.ui.screens.PendingShare
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import com.aistudio.cinemios.fxtyr.ui.viewmodel.RequestState

@Composable
fun FloatingSearchOverlay(
    viewModel: MovieViewModel,
    onNavigateToDetails: (Int, String) -> Unit,
    onNavigateToMovieBoxDetails: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val cardBg = MaterialTheme.colorScheme.surface
    val chipBg = if (isDark) Color(0xFF1A1D24) else Color(0xFFFFFFFF)
    val chipBorder = if (isDark) Color(0xFF2A2E39) else Color(0xFFDFD9CF)

    val searchQuery by viewModel.searchQuery.collectAsState()
    val isMovieBoxSearch by viewModel.isMovieBoxSearchMode.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val recentViewedShows by viewModel.recentViewedShows.collectAsState()
    val watchlistItems by viewModel.watchlist.collectAsState()

    val searchResultsState by viewModel.searchResults.collectAsState()
    val movieBoxSearchResults by viewModel.movieBoxSearchResults.collectAsState()

    val context = LocalContext.current
    var showShareSheet by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf<PendingShare?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp) // Leave room for floating bottom dock
        ) {
            // Mode Header Indicator & Switch (Single Switch, labeled MB / TMDB)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isMovieBoxSearch) "البحث في سيرفرات MB" else "البحث في سيرفرات TMDB",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                }

                // Mode switch chip in header (MB / TMDB)
                Surface(
                    shape = CircleShape,
                    color = if (isMovieBoxSearch) MaterialTheme.colorScheme.primary else chipBg,
                    border = BorderStroke(1.dp, if (isMovieBoxSearch) Color.Transparent else chipBorder),
                    modifier = Modifier.clickable {
                        viewModel.updateSearchMode(!isMovieBoxSearch)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.MovieBox,
                            contentDescription = null,
                            tint = if (isMovieBoxSearch) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isMovieBoxSearch) "MB" else "TMDB",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMovieBoxSearch) MaterialTheme.colorScheme.onPrimary else textColor
                        )
                    }
                }
            }

            // Content: Empty query (History & Recent shows) OR Active Results
            if (searchQuery.isEmpty()) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // 1. RECENT SEARCHES SECTION (Safe, scrollable chip list)
                    if (recentSearches.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.History,
                                    contentDescription = null,
                                    tint = textColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "عمليات البحث الأخيرة",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                            TextButton(
                                onClick = { viewModel.clearRecentSearches() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "مسح الكل",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Safe Horizontal LazyRow of recent searches
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(recentSearches, key = { it }) { query ->
                                Surface(
                                    shape = CircleShape,
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.clickable {
                                        viewModel.setSearchQueryOnly(query)
                                        viewModel.triggerSearch()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = query,
                                            fontSize = 13.sp,
                                            color = textColor
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .clickable { viewModel.removeRecentSearch(query) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Close,
                                                contentDescription = "حذف",
                                                tint = textColor.copy(alpha = 0.5f),
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 2. RECENTLY VIEWED SHOWS SECTION
                    if (recentViewedShows.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "شاهدت مؤخراً / العروض الأخيرة",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            TextButton(
                                onClick = { viewModel.clearRecentViewedShows() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "مسح",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(recentViewedShows, key = { it.id }) { item ->
                                RecentViewedCard(
                                    item = item,
                                    cardBg = cardBg,
                                    textColor = textColor,
                                    onClick = {
                                        if (item.isMovieBox) {
                                            onNavigateToMovieBoxDetails(item.id, item.mediaType, item.title, item.posterPath)
                                        } else {
                                            val numericId = item.id.toIntOrNull() ?: item.id.hashCode()
                                            onNavigateToDetails(numericId, item.mediaType)
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Helper prompt when both are empty
                    if (recentSearches.isEmpty() && recentViewedShows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Search,
                                    contentDescription = null,
                                    tint = textColor.copy(alpha = 0.25f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "اكتب اسم أي فيلم أو مسلسل للبحث المباشر",
                                    color = textColor.copy(alpha = 0.5f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                // ACTIVE SEARCH RESULTS
                if (isMovieBoxSearch) {
                    when (val mbState = movieBoxSearchResults) {
                        is RequestState.Success -> {
                            val list = mbState.data
                            if (list.isEmpty()) {
                                EmptySearchState(message = "لم نجد نتائج في MB للبحث: $searchQuery", textColor = textColor)
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(list, key = { it.subjectId }) { item ->
                                        val posterUrl = item.posterUrl
                                        val isTv = item.type == "series"
                                        val mediaType = if (isTv) "tv" else "movie"
                                        val watchlistId = "mb_${item.subjectId}"
                                        val inList = watchlistItems.any { it.id == watchlistId }

                                        TmdbPosterCard(
                                            title = item.title,
                                            posterUrl = posterUrl,
                                            rating = item.rating,
                                            year = item.year,
                                            isTv = isTv,
                                            isInMyList = inList,
                                            onClick = {
                                                viewModel.recordRecentView(
                                                    RecentViewedItem(
                                                        id = item.subjectId,
                                                        title = item.title,
                                                        posterPath = posterUrl,
                                                        mediaType = mediaType,
                                                        year = item.year,
                                                        rating = item.rating,
                                                        isMovieBox = true
                                                    )
                                                )
                                                onNavigateToMovieBoxDetails(item.subjectId, mediaType, item.title, posterUrl)
                                            },
                                            onToggleMyList = {
                                                viewModel.toggleWatchlist(watchlistId, item.title, posterUrl, mediaType, item.rating)
                                            },
                                            onShare = {
                                                pendingShare = PendingShare(item.title, watchlistId, mediaType)
                                                showShareSheet = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        is RequestState.Loading -> {
                            SearchGridSkeleton()
                        }
                        is RequestState.Error -> {
                            ErrorSearchState(message = "فشل البحث في MB! تأكد من اتصالك بالإنترنت.")
                        }
                        else -> {}
                    }
                } else {
                    when (val searchState = searchResultsState) {
                        is RequestState.Success -> {
                            val list = searchState.data
                            if (list.isEmpty()) {
                                EmptySearchState(message = "لم يتم العثور على أي نتائج في TMDB لـ: $searchQuery", textColor = textColor)
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(list, key = { it.id }) { item ->
                                        val rawId = item.id.toString()
                                        val inList = watchlistItems.any { it.id == rawId }
                                        val mediaType = item.mediaType ?: "movie"

                                        SearchGridCard(
                                            item = item,
                                            onClick = {
                                                val year = item.releaseDate?.take(4) ?: item.firstAirDate?.take(4) ?: ""
                                                viewModel.recordRecentView(
                                                    RecentViewedItem(
                                                        id = rawId,
                                                        title = item.title ?: item.name ?: "",
                                                        posterPath = item.posterPath ?: "",
                                                        mediaType = mediaType,
                                                        year = year,
                                                        rating = item.voteAverage ?: 0.0,
                                                        isMovieBox = false
                                                    )
                                                )
                                                onNavigateToDetails(item.id, mediaType)
                                            },
                                            isInMyList = inList,
                                            onToggleMyList = {
                                                viewModel.toggleWatchlist(
                                                    rawId,
                                                    item.title ?: item.name ?: "",
                                                    item.posterPath ?: "",
                                                    mediaType,
                                                    item.voteAverage ?: 0.0
                                                )
                                            },
                                            onShare = {
                                                pendingShare = PendingShare(item.title ?: item.name ?: "", rawId, mediaType)
                                                showShareSheet = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        is RequestState.Loading -> {
                            SearchGridSkeleton()
                        }
                        is RequestState.Error -> {
                            ErrorSearchState(message = "حدث خطأ أثناء البحث، يرجى المحاولة ثانية.")
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    pendingShare?.let { shareItem: PendingShare ->
        ShowShareSheet(
            visible = showShareSheet,
            title = shareItem.title,
            id = shareItem.id,
            mediaType = shareItem.mediaType,
            onDismiss = { showShareSheet = false },
            onNativeShare = {
                shareShow(context, shareItem.title, shareItem.id, shareItem.mediaType)
                showShareSheet = false
            }
        )
    }
}

@Composable
private fun RecentViewedCard(
    item: RecentViewedItem,
    cardBg: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    val posterUrl = if (item.posterPath.startsWith("http")) item.posterPath else "https://image.tmdb.org/t/p/w342${item.posterPath}"

    Column(
        modifier = Modifier
            .width(108.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg)
        ) {
            if (item.posterPath.isNotEmpty()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title.take(3),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            if (item.rating > 0.0) {
                Box(
                    modifier = Modifier
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "★ %.1f".format(item.rating),
                        color = Color(0xFFFFD700),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = textColor
        )

        if (item.year.isNotEmpty()) {
            Text(
                text = item.year,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun EmptySearchState(message: String, textColor: Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = AppIcons.Search,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                textAlign = TextAlign.Center,
                color = textColor.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ErrorSearchState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
    }
}
