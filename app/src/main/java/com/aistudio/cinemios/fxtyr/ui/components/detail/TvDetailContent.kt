package com.aistudio.cinemios.fxtyr.ui.components.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.data.remote.TmdbMediaItem
import com.aistudio.cinemios.fxtyr.data.remote.TmdbSeasonDetails
import com.aistudio.cinemios.fxtyr.data.remote.TmdbTvDetails
import com.aistudio.cinemios.fxtyr.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import com.aistudio.cinemios.fxtyr.ui.viewmodel.RequestState
import com.aistudio.cinemios.fxtyr.utils.isLatinText

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
