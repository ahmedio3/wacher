package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.data.local.WatchlistEntity
import com.example.data.remote.TmdbSeasonDetails
import com.example.data.remote.TmdbTvDetails
import com.example.ui.components.SeasonEpisodeSheet
import com.example.ui.components.WatchlistPosterCard
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.RequestState
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onNavigateToDetails: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.watchlist.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val context = LocalContext.current

    // Episode tracking sheet state
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<WatchlistEntity?>(null) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var selectedSeasonDetails by remember { mutableStateOf<TmdbSeasonDetails?>(null) }
    var availableSeasons by remember { mutableStateOf<List<Int>>(listOf(1)) }

    // Observe tvDetails to extract season numbers
    val tvDetailsMap by viewModel.tvDetails.collectAsState()

    // Load season details + fetch TV details (for season list) when sheet opens
    LaunchedEffect(showEpisodeSheet, selectedItem, selectedSeason) {
        if (showEpisodeSheet && selectedItem != null && selectedItem!!.mediaType == "tv") {
            val tmdbId = selectedItem!!.id.toIntOrNull()
            if (tmdbId != null) {
                // Fetch TV details if not already cached (to get season numbers)
                viewModel.fetchTvDetails(tmdbId)
                // Fetch current season episodes
                viewModel.fetchSeasonDetails(tmdbId, selectedSeason)
            }
        }
    }

    // Extract available seasons from cached tvDetails
    LaunchedEffect(selectedItem, tvDetailsMap) {
        if (selectedItem != null && selectedItem!!.mediaType == "tv") {
            val tmdbId = selectedItem!!.id.toIntOrNull()
            if (tmdbId != null) {
                val tvState = tvDetailsMap[tmdbId]
                if (tvState is RequestState.Success) {
                    val seasons = tvState.data.seasons
                        ?.map { it.seasonNumber }
                        ?.filter { it > 0 } // exclude season 0 (specials)
                        ?.sorted()
                    if (!seasons.isNullOrEmpty()) {
                        availableSeasons = seasons
                    }
                }
            }
        }
    }

    // Observe season details
    val seasonKey = if (selectedItem != null && selectedItem!!.mediaType == "tv")
        "${selectedItem!!.id.toIntOrNull()}-$selectedSeason" else null
    val seasonState = if (seasonKey != null) viewModel.seasonDetails.collectAsState().value[seasonKey] else null
    LaunchedEffect(seasonState) {
        if (seasonState is RequestState.Success) {
            selectedSeasonDetails = seasonState.data
        }
    }

    // Episode statuses for current season — cached StateFlow
    val episodeStatuses = if (showEpisodeSheet && selectedItem != null)
        viewModel.getEpisodeWatchStatusForSeason(selectedItem!!.id, selectedSeason).collectAsState().value
    else emptyList()

    // Watched count
    val watchedCount = if (selectedItem != null)
        viewModel.getWatchedCountForTvShow(selectedItem!!.id).collectAsState().value else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قائمة المشاهدة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "رجوع")
                    }
                },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = {
                            val user = FirebaseAuth.getInstance().currentUser
                            if (user == null) {
                                Toast.makeText(context, "يجب تسجيل الدخول أولاً من الإعدادات", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.syncWatchlist { success ->
                                    val msg = if (success) "تمت المزامنة بنجاح" else "فشلت المزامنة"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Sync, "مزامنة")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (items.isEmpty()) {
                // Empty state
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
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "قائمتك فارغة تماماً!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تصفح الأفلام أو المسلسلات واضغط على زر العلامة لإضافتها وقراءتها لاحقاً في أي وقت.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items) { item ->
                        WatchlistPosterCard(
                            item = item,
                            onClick = {
                                if (item.mediaType == "tv") {
                                    selectedItem = item
                                    selectedSeason = 1
                                    selectedSeasonDetails = null
                                    showEpisodeSheet = true
                                } else {
                                    onNavigateToDetails(item.id.toIntOrNull() ?: 0, item.mediaType)
                                }
                            },
                            onRemove = { viewModel.deleteFromWatchlist(item.id) }
                        )
                    }
                }
            }
        }
    }

    // Episode tracking bottom sheet
    if (showEpisodeSheet && selectedItem != null) {
        val tvId = selectedItem!!.id.toIntOrNull() ?: 0
        val totalEps = selectedSeasonDetails?.episodes?.size ?: 0
        SeasonEpisodeSheet(
            title = selectedItem!!.title,
            seasonDetails = selectedSeasonDetails,
            episodeStatuses = episodeStatuses,
            watchedCount = watchedCount,
            totalEpisodes = totalEps,
            seasonNumber = selectedSeason,
            availableSeasons = availableSeasons,
            onSeasonSelected = { s ->
                selectedSeason = s
                selectedSeasonDetails = null
            },
            onToggleEpisode = { episodeNum ->
                viewModel.toggleEpisodeWatchStatus(selectedItem!!.id, selectedSeason, episodeNum)
            },
            onMarkAllWatched = {
                viewModel.markAllEpisodesAsWatched(selectedItem!!.id, selectedSeason)
            },
            onMarkAllUnwatched = {
                viewModel.markAllEpisodesAsUnwatched(selectedItem!!.id, selectedSeason)
            },
            onDismiss = {
                showEpisodeSheet = false
                selectedItem = null
                availableSeasons = listOf(1)
            }
        )
    }
}


