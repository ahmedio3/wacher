package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.EpisodeWatchStatusEntity
import com.example.data.remote.TmdbSeasonDetails

/**
 * BottomSheet لعرض مواسم المسلسل وحلقاته مع إمكانية تتبع المشاهدة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonEpisodeSheet(
    title: String,
    seasonDetails: TmdbSeasonDetails?,
    episodeStatuses: List<EpisodeWatchStatusEntity>,
    watchedCount: Int,
    totalEpisodes: Int,
    seasonNumber: Int,
    onSeasonSelected: (Int) -> Unit,
    onToggleEpisode: (Int) -> Unit, // episode number
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // إحصاء
            if (totalEpisodes > 0) {
                Text(
                    text = "$watchedCount / $totalEpisodes حلقة تمت مشاهدتها",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Season selector
            // Note: seasons list is passed implicitly via onSeasonSelected.
            // For brevity, we show the current season number as a label.
            Text(
                text = "الموسم $seasonNumber",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Episodes list
            val episodes = seasonDetails?.episodes ?: emptyList()
            if (episodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "جارٍ تحميل الحلقات...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(episodes) { episode ->
                        val epNum = episode.episodeNumber
                        val isWatched = episodeStatuses.any { it.episode == epNum && it.watched }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isWatched)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Episode number badge
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isWatched) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$epNum",
                                        color = if (isWatched) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                // Episode title
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = episode.name ?: "الحلقة $epNum",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    if (episode.runtime != null && episode.runtime!! > 0) {
                                        Text(
                                            text = "${episode.runtime} دقيقة",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                // Watched toggle
                                IconButton(
                                    onClick = { onToggleEpisode(epNum) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isWatched) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isWatched) "تمت المشاهدة" else "لم تشاهد",
                                        tint = if (isWatched) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
