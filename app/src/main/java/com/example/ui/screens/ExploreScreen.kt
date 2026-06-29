package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.moviebox.models.SearchResult
import com.example.data.remote.moviebox.viewmodel.MovieBoxState
import com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel

// Adult keywords available for +18 search (synced with backend)
private val ADULT_KEYWORDS = listOf(
    "hentai", "ecchi", "yaoi", "yuri",
    "xxx", "adult movie", "erotic film", "nsfw",
    "mature", "porn", "sex scene", "nude",
    "hardcore", "softcore", "milf", "teen",
    "erotic", "sexy", "bondage", "lesbian",
    "bdsm", "fetish", "taboo", "anal",
    "gangbang", "orgy", "threesome",
)

private const val MAX_ADULT_KEYWORDS = 5
private const val ADULT_INITIAL_LIMIT = 10

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    viewModel: MovieBoxViewModel,
    onNavigateToMovieBoxDetails: (String, String, String, String) -> Unit,
    onNavigateToAdultContent: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val typeOptions = listOf("الكل", "أفلام", "مسلسلات")
    val sortOptions = listOf("التقييم", "الأحدث", "الأقدم", "عشوائي")
    var selectedType by remember { mutableStateOf(0) }
    var selectedSort by remember { mutableStateOf(0) }
    var safeMode by remember { mutableStateOf(true) }
    var adultLimit by remember { mutableIntStateOf(ADULT_INITIAL_LIMIT) }
    var selectedKeywords by remember { mutableStateOf<Set<String>>(emptySet()) }

    val browseState by viewModel.browseResults.collectAsState()
    val trendingState by viewModel.trendingResults.collectAsState()
    val adultContentState by viewModel.adultContent.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.trending()
    }

    fun onBrowse() {
        val typeValue = when (selectedType) {
            1 -> "movie"
            2 -> "series"
            else -> null
        }
        val sortValue = when (selectedSort) {
            1 -> "newest"
            2 -> "oldest"
            3 -> "random"
            else -> "rating"
        }
        viewModel.browse(genre = null, type = typeValue, sort = sortValue, safeMode = safeMode, limit = 30)
    }

    fun onAdultSearch() {
        if (selectedKeywords.isEmpty()) return
        val queries = selectedKeywords.joinToString(",")
        viewModel.fetchAdultContent(queries = queries, limit = adultLimit)
    }

    fun onShowMore() {
        adultLimit += 10
        onAdultSearch()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اكتشف", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 90.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Trending section
            item(span = { GridItemSpan(this.maxLineSpan) }) {
                Column {
                    Text(
                        text = "الرائج 🔥",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                    when (val state = trendingState) {
                        is MovieBoxState.Success -> {
                            if (state.data.isEmpty()) {
                                Text(
                                    text = "لا توجد نتائج",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.data, key = { it.subjectId.ifEmpty { it.title.ifEmpty { it.hashCode().toString() } } }) { item ->
                                        val w = 110.dp
                                        Column(
                                            modifier = Modifier
                                                .width(w)
                                                .clickable {
                                                    val type = if (item.type == "series") "tv" else "movie"
                                                    onNavigateToMovieBoxDetails(item.subjectId, type, item.title, item.posterUrl)
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(w)
                                                    .height((w / 0.7f))
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                            ) {
                                                AsyncImage(
                                                    model = item.posterUrl,
                                                    contentDescription = item.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                if (item.rating > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(4.dp)
                                                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            .align(Alignment.BottomEnd)
                                                    ) {
                                                        Text(
                                                            text = String.format("%.1f", item.rating),
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is MovieBoxState.Loading -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
                                items(5) {
                                    Column(modifier = Modifier.width(110.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .width(110.dp)
                                                .height(157.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(80.dp)
                                                .height(12.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                            }
                        }
                        is MovieBoxState.Error -> {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        else -> {}
                    }
                }
            }

            // 2. Filters + Browse section
            item(span = { GridItemSpan(this.maxLineSpan) }) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "النوع",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        typeOptions.forEachIndexed { index, label ->
                            FilterChip(
                                selected = selectedType == index,
                                onClick = { selectedType = index },
                                label = { Text(label, fontSize = 13.sp) },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "الترتيب",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sortOptions.forEachIndexed { index, label ->
                            FilterChip(
                                selected = selectedSort == index,
                                onClick = { selectedSort = index },
                                label = { Text(label, fontSize = 13.sp) },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Safe mode toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { safeMode = !safeMode }
                                .background(
                                    if (safeMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else Color(0xFFFF4444).copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (safeMode) "الوضع الآمن 🌙" else "+18 🔞",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (safeMode) MaterialTheme.colorScheme.primary else Color(0xFFFF4444)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = ::onBrowse,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصفح", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. +18 Section (visible when safe mode is OFF)
            if (!safeMode) {
                item(span = { GridItemSpan(this.maxLineSpan) }) {
                    Column {
                        // Header + full page button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "قسم +18",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFF4444)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "🔞", fontSize = 18.sp)

                            Spacer(modifier = Modifier.weight(1f))

                            // Full page button
                            TextButton(onClick = {
                                val q = if (selectedKeywords.isNotEmpty()) selectedKeywords.joinToString(",") else ""
                                onNavigateToAdultContent(q)
                            }) {
                                Text("صفحة كاملة", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Keyword picker chips (scrollable, max 5)
                        Text(
                            text = "اختر الكلمات المفتاحية (حد أقصى $MAX_ADULT_KEYWORDS):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            items(ADULT_KEYWORDS) { kw ->
                                val isSelected = kw in selectedKeywords
                                val canSelect = selectedKeywords.size < MAX_ADULT_KEYWORDS
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedKeywords = if (isSelected) {
                                            selectedKeywords - kw
                                        } else if (canSelect) {
                                            selectedKeywords + kw
                                        } else {
                                            selectedKeywords // max reached
                                        }
                                    },
                                    label = {
                                        Text(
                                            kw,
                                            fontSize = if (isSelected) 12.sp else 11.sp,
                                            maxLines = 1
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFF4444).copy(alpha = 0.2f),
                                        selectedLabelColor = Color(0xFFFF4444),
                                    )
                                )
                            }
                        }

                        // Search +18 button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    adultLimit = ADULT_INITIAL_LIMIT
                                    onAdultSearch()
                                },
                                enabled = selectedKeywords.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF4444),
                                    disabledContainerColor = Color(0xFFFF4444).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("بحث +18", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Adult results
                        when (val state = adultContentState) {
                            is MovieBoxState.Idle -> {
                                Text(
                                    text = "اختر كلمات مفتاحية واضغط بحث",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            is MovieBoxState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = Color(0xFFFF4444),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                            is MovieBoxState.Success -> {
                                if (state.data.isEmpty()) {
                                    Text(
                                        text = "لا توجد نتائج لهذه الكلمات",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                } else {
                                    // Show results in a LazyRow
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(
                                            state.data.take(adultLimit.coerceAtMost(state.data.size)),
                                            key = { it.subjectId.ifEmpty { it.title.ifEmpty { it.hashCode().toString() } } }
                                        ) { item ->
                                            val w = 110.dp
                                            Column(
                                                modifier = Modifier
                                                    .width(w)
                                                    .clickable {
                                                        val type = if (item.type == "series") "tv" else "movie"
                                                        onNavigateToMovieBoxDetails(item.subjectId, type, item.title, item.posterUrl)
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(w)
                                                        .height((w / 0.7f))
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(MaterialTheme.colorScheme.surface)
                                                ) {
                                                    AsyncImage(
                                                        model = item.posterUrl,
                                                        contentDescription = item.title,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(4.dp)
                                                            .background(Color(0xFFFF4444), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "+18",
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        // "عرض المزيد" item
                                        if (state.data.size > adultLimit) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .width(80.dp)
                                                        .height(157.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFFFF4444).copy(alpha = 0.1f))
                                                        .clickable { onShowMore() },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("+", fontSize = 24.sp, color = Color(0xFFFF4444), fontWeight = FontWeight.Bold)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text("عرض المزيد", fontSize = 10.sp, color = Color(0xFFFF4444))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is MovieBoxState.Error -> {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // 4. Browse results grid
            when (val state = browseState) {
                is MovieBoxState.Success -> {
                    val data = state.data
                    if (data.isEmpty()) {
                        item(span = { GridItemSpan(this.maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لا توجد نتائج للتصفح",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    } else {
                        items(data, key = { it.subjectId.ifEmpty { it.title.ifEmpty { it.hashCode().toString() } } }) { item ->
                            MovieBoxSearchGridCard(
                                item = item,
                                onClick = {
                                    val type = if (item.type == "series") "tv" else "movie"
                                    onNavigateToMovieBoxDetails(item.subjectId, type, item.title, item.posterUrl)
                                }
                            )
                        }
                    }
                }
                is MovieBoxState.Loading -> {
                    items(6) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .height(14.dp)
                                    .fillMaxWidth(0.8f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
                is MovieBoxState.Error -> {
                    item(span = { GridItemSpan(this.maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun MovieBoxSearchGridCard(
    item: SearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (item.rating > 0) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = String.format("%.1f", item.rating),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (item.type == "series") {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = "مسلسل",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
