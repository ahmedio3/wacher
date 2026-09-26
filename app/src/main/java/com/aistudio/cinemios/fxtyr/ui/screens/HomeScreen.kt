package com.aistudio.cinemios.fxtyr.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.data.remote.TmdbMediaItem
import com.aistudio.cinemios.fxtyr.ui.components.*
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import com.aistudio.cinemios.fxtyr.ui.viewmodel.RequestState

data class PendingShare(val title: String, val id: String, val mediaType: String)

@OptIn(ExperimentalMaterial3Api::class)
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

    val searchQuery by viewModel.searchQuery.collectAsState()
    val isMovieBoxSearch by viewModel.isMovieBoxSearchMode.collectAsState()
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
            .imePadding()
            .then(if (searchQuery.isEmpty()) Modifier.verticalScroll(scrollState) else Modifier)
    ) {
        // 1. Top Header
        HomeHeader()

        // 2. Search Bar + Mode Switch Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = {
                    Text(
                        if (isMovieBoxSearch) "ابحث في سيرفرات MovieBox..." else "ابحث عن فيلم أو مسلسل في TMDB...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier.weight(1f),
                leadingIcon = {
                    IconButton(onClick = { viewModel.triggerSearch() }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setSearchQueryOnly("") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.triggerSearch() })
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.updateSearchMode(!isMovieBoxSearch) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isMovieBoxSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "MovieBox Mode",
                    tint = if (isMovieBoxSearch) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Conditional Layout: Home Feed vs Search Results
        if (searchQuery.isEmpty()) {
            // A. Featured Carousel (Top 5 trending movies)
            when (val trendingState = trendingMoviesState) {
                is RequestState.Success -> {
                    val featured = trendingState.data.take(5)
                    if (featured.isNotEmpty()) {
                        FeaturedCarousel(
                            items = featured,
                            onItemClick = { item -> onNavigateToDetails(item.id, "movie") }
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

            // B. Watchlist Section (قائمتي) — shown if not empty
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

            // C. Section 1: Trending Movies (الأفلام الرائجة)
            MediaCategoryCarousel(
                title = "الأفلام الرائجة هذا الأسبوع",
                icon = Icons.Default.Movie,
                state = trendingMoviesState,
                onItemClick = { onNavigateToDetails(it.id, "movie") },
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

            // D. Section 2: Trending TV Shows (المسلسلات الرائجة)
            MediaCategoryCarousel(
                title = "المسلسلات الرائجة هذا الأسبوع",
                icon = Icons.Default.LiveTv,
                state = trendingTvState,
                onItemClick = { onNavigateToDetails(it.id, "tv") },
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
        } else {
            // Search Results Grid
            val searchResultsState by viewModel.searchResults.collectAsState()
            val movieBoxSearchResults by viewModel.movieBoxSearchResults.collectAsState()
            Text(
                text = "نتائج البحث عن: $searchQuery",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            if (isMovieBoxSearch) {
                when (val mbSearchState = movieBoxSearchResults) {
                    is RequestState.Success -> {
                        val mbList = mbSearchState.data
                        if (mbList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لم نجد أي نتيجة في MovieBox!\nيرجى التحقق من الكلمات.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(mbList, key = { it.subjectId }) { item ->
                                    val posterUrl = item.posterUrl
                                    val isTv = item.type == "series"
                                    val mediaType = if (isTv) "tv" else "movie"
                                    val rawId = item.subjectId
                                    val watchlistId = "mb_$rawId"
                                    val inList = watchlistItems.any { it.id == watchlistId }

                                    TmdbPosterCard(
                                        title = item.title,
                                        posterUrl = posterUrl,
                                        rating = item.rating,
                                        year = item.year,
                                        isTv = isTv,
                                        isInMyList = inList,
                                        onClick = {
                                            onNavigateToMovieBoxDetails(rawId, mediaType, item.title, posterUrl)
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
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "فشل البحث في MovieBox! تأكد من اتصالك بالإنترنت.",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {}
                }
            } else {
                when (val searchState = searchResultsState) {
                    is RequestState.Success -> {
                        val list = searchState.data
                        if (list.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لم يتم العثور على أي نتائج!\nيرجى تجربة كلمات أخرى.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(list, key = { it.id }) { item ->
                                    val isTv = item.mediaType == "tv"
                                    val rawId = item.id.toString()
                                    val inList = watchlistItems.any { it.id == rawId }

                                    SearchGridCard(
                                        item = item,
                                        onClick = { onNavigateToDetails(item.id, item.mediaType ?: "movie") },
                                        isInMyList = inList,
                                        onToggleMyList = {
                                            viewModel.toggleWatchlist(rawId, item.title ?: item.name ?: "", item.posterPath ?: "", item.mediaType ?: "movie", item.voteAverage ?: 0.0)
                                        },
                                        onShare = {
                                            pendingShare = PendingShare(item.title ?: item.name ?: "", rawId, item.mediaType ?: "movie")
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
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "حدث خطأ في جلب تفاصيل البحث، أعد المحاولة.",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
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

@Composable
fun HomeHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "مرحباً بك في",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "ووتشيرا — Watchera",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun FeaturedCarousel(
    items: List<TmdbMediaItem>,
    onItemClick: (TmdbMediaItem) -> Unit
) {
    Column {
        Text(
            text = "المميز اليوم",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items, key = { it.id }) { item ->
                FeaturedBackdropCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedBackdropCard(
    item: TmdbMediaItem,
    onClick: () -> Unit
) {
    val backdropUrl = remember(item) {
        if (!item.backdropPath.isNullOrEmpty()) {
            "https://image.tmdb.org/t/p/w780${item.backdropPath}"
        } else if (!item.posterPath.isNullOrEmpty()) {
            "https://image.tmdb.org/t/p/w780${item.posterPath}"
        } else {
            ""
        }
    }

    val (interactionSource, pressed) = rememberPressState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.8f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (pressed) 0.98f else 1f, animationSpec = tween(150))

    Box(
        modifier = Modifier
            .width(300.dp)
            .height(180.dp)
            .scale(pressScale)
            .alpha(pressAlpha)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        if (backdropUrl.isNotEmpty()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = item.title ?: item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.title ?: item.name ?: "",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 60f
                    )
                )
        )

        // Text Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                text = item.title ?: item.name ?: "",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.releaseDate.isNullOrEmpty() || (item.voteAverage != null && item.voteAverage > 0)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.voteAverage != null && item.voteAverage > 0) {
                        Text(
                            text = "★ %.1f".format(item.voteAverage),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFFD700)
                        )
                    }
                    val year = item.releaseDate?.take(4) ?: item.firstAirDate?.take(4) ?: ""
                    if (year.isNotEmpty()) {
                        Text(
                            text = year,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaCategoryCarousel(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    state: RequestState<List<TmdbMediaItem>>,
    onItemClick: (TmdbMediaItem) -> Unit,
    isInMyList: (TmdbMediaItem) -> Boolean = { false },
    onToggleMyList: (TmdbMediaItem) -> Unit = {},
    onShare: (TmdbMediaItem) -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        when (state) {
            is RequestState.Success -> {
                val items = state.data
                if (items.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            val posterUrl = if (!item.posterPath.isNullOrEmpty()) {
                                "https://image.tmdb.org/t/p/w342${item.posterPath}"
                            } else ""

                            TmdbPosterCard(
                                title = item.title ?: item.name ?: "",
                                posterUrl = posterUrl,
                                rating = item.voteAverage ?: 0.0,
                                year = item.releaseDate?.take(4) ?: item.firstAirDate?.take(4) ?: "",
                                isTv = item.mediaType == "tv",
                                isInMyList = isInMyList(item),
                                onClick = { onItemClick(item) },
                                onToggleMyList = { onToggleMyList(item) },
                                onShare = { onShare(item) }
                            )
                        }
                    }
                }
            }
            is RequestState.Loading -> {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(5) {
                        SkeletonItem(width = 120.dp, height = 180.dp, cornerRadius = 16.dp)
                    }
                }
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TmdbPosterCard(
    title: String,
    posterUrl: String,
    rating: Double,
    year: String,
    isTv: Boolean,
    isInMyList: Boolean,
    onClick: () -> Unit,
    onToggleMyList: () -> Unit,
    onShare: () -> Unit
) {
    val (interactionSource, pressed) = rememberPressState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.8f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, animationSpec = tween(150))
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(120.dp)
            .scale(pressScale)
            .alpha(pressAlpha)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.take(4),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (rating > 0.0) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "★ %.1f".format(rating),
                        color = Color(0xFFFFD700),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            ShowCardContextMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                isInMyList = isInMyList,
                onToggleMyList = onToggleMyList,
                onShare = onShare
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (year.isNotEmpty()) {
            Text(
                text = year,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchGridCard(
    item: TmdbMediaItem,
    onClick: () -> Unit,
    isInMyList: Boolean = false,
    onToggleMyList: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val posterUrl = remember(item) { "https://image.tmdb.org/t/p/w342${item.posterPath}" }

    val (interactionSource, pressed) = rememberPressState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.75f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, animationSpec = tween(150))

    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .alpha(pressAlpha)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.71f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (!item.posterPath.isNullOrEmpty()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = item.title ?: item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (item.title ?: item.name ?: "بلا اسم").take(3),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            val badgeName = if (item.mediaType == "tv") "مسلسل" else "فيلم"
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = badgeName,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            ShowCardContextMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                isInMyList = isInMyList,
                onToggleMyList = onToggleMyList,
                onShare = onShare
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title ?: item.name ?: "",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SearchGridSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(9) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(shimmerBrush())
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(14.dp)
                        .fillMaxWidth(0.8f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
            }
        }
    }
}
