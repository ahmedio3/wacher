package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.example.ui.components.bouncyOverscroll
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
import androidx.compose.ui.text.style.TextOverflow
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
    availableSeasons: List<Int> = listOf(seasonNumber),
    onSeasonSelected: (Int) -> Unit,
    onToggleEpisode: (Int) -> Unit,
    onMarkAllWatched: () -> Unit = {},
    onMarkAllUnwatched: () -> Unit = {},
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

            // Season selector — horizontal scrollable row
            if (availableSeasons.size > 1) {
                Text(
                    text = "اختر الموسم",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyRow(
                    modifier = Modifier.bouncyOverscroll(isVertical = false).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableSeasons) { sNum ->
                        val isSelected = sNum == seasonNumber
                        FilterChip(
                            selected = isSelected,
                            onClick = { if (!isSelected) onSeasonSelected(sNum) },
                            label = { Text("الموسم $sNum", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            } else {
                // Single season — just show label
                Text(
                    text = "الموسم $seasonNumber",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Mark all row
            if (totalEpisodes > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onMarkAllWatched) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تحديد الكل", fontSize = 13.sp)
                    }
                    TextButton(onClick = onMarkAllUnwatched) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إلغاء الكل", fontSize = 13.sp)
                    }
                }
            }

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
                    modifier = Modifier.bouncyOverscroll().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                        overflow = TextOverflow.Ellipsis
                                    )
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
