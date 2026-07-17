package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.WatchlistEntity
import com.example.data.remote.TmdbMediaItem
import com.example.ui.components.SkeletonItem
import com.example.ui.components.ShowCardContextMenu
import com.example.ui.components.WatchlistPosterCard
import com.example.ui.components.rememberPressState
import com.example.ui.components.shareShow
import com.example.ui.components.shimmerBrush
import com.example.ui.components.ShowShareSheet
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.RequestState

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
    
    val popularMoviesState by viewModel.popularMovies.collectAsState()
    val popularTvState by viewModel.popularTvShows.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isMovieBoxSearch by viewModel.isMovieBoxSearchMode.collectAsState()
    val watchlistItems by viewModel.watchlist.collectAsState()
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
        // 1. iOS Top Header with Settings Instead of Cinema Logo
        HomeHeader(onNavigateToSettings = onNavigateToSettings)

        // 2. Search Box right below the Header (Netflix / Apple TV design)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQueryOnly(it) },
                modifier = Modifier.weight(1f).height(56.dp),
                placeholder = { Text(if (isMovieBoxSearch) "ابحث في MovieBox..." else "ابحث عن الأفلام أو المسلسلات...", fontSize = 14.sp) },
                leadingIcon = {
                    IconButton(
                        onClick = { viewModel.triggerSearch() },
                        modifier = Modifier
                            .offset(x = (-4).dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
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
                    imageVector = Icons.Default.CloudSync, // Generic online icon
                    contentDescription = "MovieBox Mode",
                    tint = if (isMovieBoxSearch) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Conditional Layout: Empty Search (Standard Feed) vs Search Results
        if (searchQuery.isEmpty()) {
            // New Custom Section "بتاع" — collect only inside this branch
            val customSectionItems by com.example.data.remote.CustomSectionManager.getItems().collectAsState(initial = emptyList())
            if (customSectionItems.isNotEmpty()) {
                Text(
                    text = "بتاع",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
                )
                
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(customSectionItems, key = { it.id }) { item ->
                        CustomSectionItemCard(
                            item = item,
                            onClick = {
                                when(item.targetAction) {
                                    "details" -> {
                                        val parts = item.targetData.split(":")
                                        if (parts.size == 2) onNavigateToDetails(parts[1].toInt(), parts[0])
                                    }
                                    // Handle other intents via LocalUriHandler or navController
                                }
                            },
                            isInMyList = watchlistItems.any { w -> w.id == item.id },
                            onToggleMyList = { viewModel.toggleWatchlist(item.id, item.title, "", "movie", 0.0) },
                            onShare = { pendingShare = PendingShare(item.title, item.id, "movie"); showShareSheet = true }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // A. Featured Slider
            when (val popularState = popularMoviesState) {
                is RequestState.Success -> {
                    val featured = popularState.data.take(5)
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

            // C+. Watchlist Section (قائمتي) — between Featured and Popular Movies
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

            // B. TMDB Popular Movies Carousel (الأفلام الأكثر شعبية)
            MediaCategoryCarousel(
                title = "الأفلام الأكثر شعبية",
                icon = Icons.Default.Movie,
                state = popularMoviesState,
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

            // C. TMDB Popular TV Shows Carousel (المسلسلات الأكثر شهرة)
            MediaCategoryCarousel(
                title = "المسلسلات الأكثر شهرة",
                icon = Icons.Default.LiveTv,
                state = popularTvState,
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
                } else {
                    // B. Show Search Results Grid — collect only inside this branch

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
                                    MovieBoxSearchGridCard(
                                        item = item,
                                        onClick = { 
                                            val type = if (item.type == "series") "tv" else "movie"
                                            onNavigateToMovieBoxDetails(item.subjectId, type, item.title, item.posterUrl)
                                        },
                                        isInMyList = false,
                                        onToggleMyList = {},
                                        onShare = { pendingShare = PendingShare(item.title, item.subjectId, if (item.type == "series") "tv" else "movie"); showShareSheet = true }
                                    )
                                }
                            }
                        }
                    }
                    is RequestState.Loading -> {
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
                                        modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(16.dp)).background(shimmerBrush())
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(4.dp)).background(shimmerBrush()))
                                }
                            }
                        }
                    }
                    is RequestState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(mbSearchState.message, color = MaterialTheme.colorScheme.error)
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
                                    text = "لم نجد أي فيلم أو مسلسل بهذا الاسم!\nيرجى التحقق من هجاء الأحرف.",
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
                                    SearchGridCard(
                                        item = item,
                                        onClick = { onNavigateToDetails(item.id, item.mediaType ?: "movie") },
                                        isInMyList = watchlistItems.any { it.id == item.id.toString() },
                                        onToggleMyList = {
                                            viewModel.toggleWatchlist(item.id.toString(), item.title ?: item.name ?: "", item.posterPath ?: "", item.mediaType ?: "movie", item.voteAverage ?: 0.0)
                                        },
                                        onShare = { pendingShare = PendingShare(item.title ?: item.name ?: "", item.id.toString(), item.mediaType ?: "movie"); showShareSheet = true }
                                    )
                                }
                            }
                        }
                    }
                    is RequestState.Loading -> {
                        // Show a beautiful skeleton grid loader!
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
fun HomeHeader(onNavigateToSettings: () -> Unit) {
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
        
        // Settings Button replaces Cinema Avatar for clean functional navigation
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "الإعدادات",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
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
                val backdropUrl = remember(item) { "https://image.tmdb.org/t/p/w500${item.backdropPath ?: item.posterPath}" }
                
                Box(
                    modifier = Modifier
                        .width(310.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onItemClick(item) }
                ) {
                    // Movie Backdrop
                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Dark elegant gradient overlay for text readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                    startY = 50f
                                )
                            )
                    )
                    
                    // iOS badge play button and texts
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "عرض",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.title ?: item.name ?: "",
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "تقييم",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = remember(item) { String.format("%.1f", item.voteAverage ?: 0.0) },
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (item.mediaType == "movie") "فيلم" else "مسلسل",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            )
                        }
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }

        when (state) {
            is RequestState.Success -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.data, key = { it.id }) { item ->
                        MediaCompactPosterCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            isInMyList = isInMyList(item),
                            onToggleMyList = { onToggleMyList(item) },
                            onShare = { onShare(item) }
                        )
                    }
                }
            }
            is RequestState.Loading -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false
                ) {
                    items(5) {
                        Column {
                            SkeletonItem(width = 110.dp, height = 154.dp, cornerRadius = 16.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            SkeletonItem(width = 80.dp, height = 12.dp)
                        }
                    }
                }
            }
            is RequestState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("خطأ في الاتصال بالشبكة.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCompactPosterCard(
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
            .width(110.dp)
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
                .width(110.dp)
                .height(154.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = item.title ?: item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Rating star overlay
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = remember(item) { String.format("%.1f", item.voteAverage ?: 0.0) },
                        color = Color.White,
                        fontSize = 8.sp,
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
        
        Spacer(modifier = Modifier.height(4.dp))
        
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieBoxSearchGridCard(
    item: com.example.data.remote.moviebox.models.SearchResult,
    onClick: () -> Unit,
    isInMyList: Boolean = false,
    onToggleMyList: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val (interactionSource, pressed) = rememberPressState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.75f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, animationSpec = tween(150))

    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .alpha(pressAlpha)
            .clip(RoundedCornerShape(12.dp))
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
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Gradient signature of MovieBox Search
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF673AB7).copy(alpha = 0.5f))))
            )
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
            )
            if (item.year.isNotEmpty()) {
                Box(
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = item.year, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomSectionItemCard(
    item: com.example.data.remote.CustomSectionItem,
    onClick: () -> Unit,
    isInMyList: Boolean = false,
    onToggleMyList: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val (interactionSource, pressed) = rememberPressState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.75f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, animationSpec = tween(150))

    var menuExpanded by remember { mutableStateOf(false) }

    if (item.displayType == "poster") {
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(195.dp)
                .scale(pressScale)
                .alpha(pressAlpha)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Gradient
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
            Text(
                text = item.message.ifEmpty { item.title },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            ShowCardContextMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                isInMyList = isInMyList,
                onToggleMyList = onToggleMyList,
                onShare = onShare
            )
        }
    } else if (item.displayType == "landscape") {
        Box(
            modifier = Modifier
                .width(260.dp)
                .height(150.dp)
                .scale(pressScale)
                .alpha(pressAlpha)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
            Text(
                text = item.message.ifEmpty { item.title },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            ShowCardContextMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                isInMyList = isInMyList,
                onToggleMyList = onToggleMyList,
                onShare = onShare
            )
        }
    } else {
        // Gradient box
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(100.dp)
                .scale(pressScale)
                .alpha(pressAlpha)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.message.ifEmpty { item.title },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center
            )

            ShowCardContextMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                isInMyList = isInMyList,
                onToggleMyList = onToggleMyList,
                onShare = onShare
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


