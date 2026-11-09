package com.aistudio.cinemios.fxtyr.ui.screens

import android.app.Application
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.aistudio.cinemios.fxtyr.ui.components.IosIcons
import com.aistudio.cinemios.fxtyr.ui.components.downloads.*
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import java.io.File

@Composable
fun DownloadsScreen(
    viewModel: MovieViewModel,
    onNavigateToPlayer: (String, String, String) -> Unit,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())

    // Active segment tab: 0 for Series, 1 for Movies, 2 for Local
    var activeSegmentTab by remember { mutableIntStateOf(0) }

    // Grouping series downloads by family name/id
    val tvShowDownloads = downloads.filter { it.mediaType == "tv" }
    val playlistGroups = tvShowDownloads.groupBy { it.mediaId }
    val seriesPlaylists = playlistGroups.filter { it.value.isNotEmpty() }

    // Movies are kept individual
    val individualDownloads = downloads.filter { it.mediaType == "movie" }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // iOS Styled Custom Header with RTL alignment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "التحميلات غير المتصلة",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Icon(
                        imageVector = IosIcons.ArrowDownCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Elegant iOS Segmented Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "المسلسلات (${seriesPlaylists.size})",
                        "الأفلام (${individualDownloads.size})",
                        "محلي"
                    )
                    tabs.forEachIndexed { index, title ->
                        val isSelected = activeSegmentTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (isSelected) {
                                        Modifier.shadow(2.dp, RoundedCornerShape(11.dp))
                                    } else Modifier
                                )
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { activeSegmentTab = index }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // RENDER SELECTED SECTION
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activeSegmentTab) {
                        0 -> {
                            // SERIES PLAYLISTS
                            if (seriesPlaylists.isEmpty()) {
                                EmptyDownloadsView(message = "لا توجد مسلسلات منزلة بعد. قم بتحميل حلقات أي مسلسل لتنظيمها وعرضها هنا.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(seriesPlaylists.keys.toList(), key = { it }) { mediaId ->
                                        val playlistEpisodes = playlistGroups[mediaId] ?: emptyList()
                                        val parentTitle = playlistEpisodes.firstOrNull()?.title?.substringBefore(" - ") ?: "مسلسل"
                                        val posterPath = playlistEpisodes.firstOrNull()?.posterPath ?: ""
                                        val completedCount = playlistEpisodes.count { it.status == "completed" }
                                        val downloadingCount = playlistEpisodes.size - completedCount

                                        PlaylistFolderCard(
                                            seriesTitle = parentTitle,
                                            posterPath = posterPath,
                                            completedCount = completedCount,
                                            downloadingCount = downloadingCount,
                                            onClick = {
                                                navController.navigate("series_downloads/$mediaId")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            // MOVIE FILES
                            if (individualDownloads.isEmpty()) {
                                EmptyDownloadsView(message = "لا توجد أفلام أو تنزيلات فردية بعد.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(individualDownloads, key = { it.id }) { item ->
                                        DownloadItemRow(
                                            item = item,
                                            viewModel = viewModel,
                                            onPlayClick = { path ->
                                                if (path != null) {
                                                    onNavigateToPlayer(item.id, item.title, path)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            // LOCAL FILES from device storage
                            LocalFilesTab(
                                context = context,
                                onNavigateToPlayer = onNavigateToPlayer
                            )
                        }
                    }
                }

                // Storage location subtle iOS footnote (replacing the previous clunky card)
                if (activeSegmentTab != 2) {
                    val exactPath = remember(context) {
                        File(context.filesDir, "downloads").absolutePath
                    }
                    Text(
                        text = "مسار الحفظ: $exactPath",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
