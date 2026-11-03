package com.aistudio.cinemios.fxtyr.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.cinemios.fxtyr.data.remote.TmdbMediaItem
import com.aistudio.cinemios.fxtyr.ui.components.SkeletonItem
import com.aistudio.cinemios.fxtyr.ui.viewmodel.RequestState

@Composable
fun MediaCategoryCarousel(
    title: String,
    state: RequestState<List<TmdbMediaItem>>,
    onItemClick: (TmdbMediaItem) -> Unit,
    isInMyList: (TmdbMediaItem) -> Boolean = { false },
    onToggleMyList: (TmdbMediaItem) -> Unit = {},
    onShare: (TmdbMediaItem) -> Unit = {}
) {
    Column {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        when (state) {
            is RequestState.Success -> {
                val items = state.data
                if (items.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
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
                    contentPadding = PaddingValues(horizontal = 20.dp),
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
