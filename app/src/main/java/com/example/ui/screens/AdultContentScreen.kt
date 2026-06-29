package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.data.remote.moviebox.viewmodel.MovieBoxState
import com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel

private val ADULT_KEYWORDS = listOf(
    "hentai", "ecchi", "yaoi", "yuri",
    "xxx", "adult movie", "erotic film", "nsfw",
    "mature", "porn", "sex scene", "nude",
    "hardcore", "softcore", "milf", "teen",
    "erotic", "sexy", "bondage", "lesbian",
    "bdsm", "fetish", "taboo", "anal",
    "gangbang", "orgy", "threesome",
)

private const val MAX_KEYWORDS = 5
private const val PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdultContentScreen(
    viewModel: MovieBoxViewModel,
    initialQueries: String = "",
    onNavigateToMovieBoxDetails: (String, String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var selectedKeywords by remember {
        mutableStateOf(
            if (initialQueries.isNotBlank())
                initialQueries.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            else emptySet()
        )
    }
    var currentLimit by remember { mutableIntStateOf(PAGE_SIZE) }
    var isLoadingMore by remember { mutableStateOf(false) }

    val adultContentState by viewModel.adultContent.collectAsState()

    fun doSearch(limit: Int = currentLimit) {
        if (selectedKeywords.isEmpty()) return
        val queries = selectedKeywords.joinToString(",")
        viewModel.fetchAdultContent(queries = queries, limit = limit)
    }

    // Auto-search if initial queries provided
    LaunchedEffect(Unit) {
        if (initialQueries.isNotBlank() && selectedKeywords.isNotEmpty()) {
            doSearch()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("قسم +18", fontWeight = FontWeight.Bold, color = Color(0xFFFF4444))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🔞", fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Keyword picker
            item {
                Column {
                    Text(
                        text = "اختر الكلمات المفتاحية (حد أقصى $MAX_KEYWORDS):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Flow-like row of chips (using LazyRow)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ADULT_KEYWORDS) { kw ->
                            val isSelected = kw in selectedKeywords
                            val canSelect = selectedKeywords.size < MAX_KEYWORDS
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedKeywords = if (isSelected) {
                                        selectedKeywords - kw
                                    } else if (canSelect) {
                                        selectedKeywords + kw
                                    } else {
                                        selectedKeywords
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            currentLimit = PAGE_SIZE
                            doSearch()
                        },
                        enabled = selectedKeywords.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF4444),
                            disabledContainerColor = Color(0xFFFF4444).copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("بحث", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // Results
            when (val state = adultContentState) {
                is MovieBoxState.Idle -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "اختر كلمات مفتاحية واضغط بحث",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                is MovieBoxState.Loading -> {
                    if (!isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = Color(0xFFFF4444),
                                    strokeWidth = 4.dp
                                )
                            }
                        }
                    }
                }
                is MovieBoxState.Success -> {
                    val data = state.data
                    if (data.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لا توجد نتائج",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    } else {
                        // Results in 3-column grid
                        val displayedData = data.take(currentLimit)
                        val totalCount = data.size

                        // We need to show items in a grid-like pattern
                        // Since we're inside LazyColumn, we can't nest LazyVerticalGrid easily
                        // Instead, chunk the data into rows of 3
                        val chunked = displayedData.chunked(3)
                        items(chunked) { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { item ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val type = if (item.type == "series") "tv" else "movie"
                                                onNavigateToMovieBoxDetails(item.subjectId, type, item.title, item.posterUrl)
                                            }
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
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                                // Fill remaining space if less than 3 items
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        // Show more button
                        if (totalCount > currentLimit) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            isLoadingMore = true
                                            currentLimit += PAGE_SIZE
                                            doSearch()
                                            isLoadingMore = false
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFFF4444)
                                        )
                                    ) {
                                        Text("عرض المزيد ($totalCount)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                is MovieBoxState.Error -> {
                    item {
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
            }
        }
    }
}
