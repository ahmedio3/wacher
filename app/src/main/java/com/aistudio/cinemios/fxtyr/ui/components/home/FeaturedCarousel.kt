package com.aistudio.cinemios.fxtyr.ui.components.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.data.remote.TmdbMediaItem
import com.aistudio.cinemios.fxtyr.ui.components.rememberPressState

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
