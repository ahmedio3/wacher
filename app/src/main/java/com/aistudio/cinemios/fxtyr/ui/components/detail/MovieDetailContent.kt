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
import com.aistudio.cinemios.fxtyr.data.remote.TmdbMovieDetails
import com.aistudio.cinemios.fxtyr.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import com.aistudio.cinemios.fxtyr.utils.isLatinText

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
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Overviews block
            Text(
                text = "قصة الفيلم",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (movie.overview.isNullOrEmpty()) "لا يتوفر نص القصة باللغة العربية حالياً." else movie.overview,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    fontFamily = if (isLatinText(movie.overview ?: "")) JetBrainsMonoFontFamily else null,
                    textDirection = if (isLatinText(movie.overview ?: "")) TextDirection.Ltr else TextDirection.Unspecified
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = if (isLatinText(movie.overview ?: "")) TextAlign.Left else TextAlign.Right,
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
                    modifier = Modifier.fillMaxWidth(),
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
        }
    }
}
