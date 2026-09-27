package com.aistudio.cinemios.fxtyr.ui.screens

import android.app.Application
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.ui.components.downloads.*
import com.aistudio.cinemios.fxtyr.data.local.DownloadEntity
import com.aistudio.cinemios.fxtyr.data.local.SeasonMetaEntity
import com.aistudio.cinemios.fxtyr.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteMutedRed
import com.aistudio.cinemios.fxtyr.utils.isLatinText
import com.aistudio.cinemios.fxtyr.ui.components.CircularSelectionIndicator
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import com.aistudio.cinemios.fxtyr.ui.viewmodel.SubtitleHelper
import com.aistudio.cinemios.fxtyr.ui.viewmodel.RequestState
import java.io.File

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: MovieViewModel,
    onNavigateToPlayer: (String, String, String) -> Unit,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    
    // Active segment tab: 0 for Series, 1 for Movies. 
    // In RTL, 0 is on the right side (Series) and is active by default.
    var activeSegmentTab by remember { mutableIntStateOf(0) }

    // Grouping series downloads by family name/id
    val tvShowDownloads = downloads.filter { it.mediaType == "tv" }
    val playlistGroups = tvShowDownloads.groupBy { it.mediaId }
    val seriesPlaylists = playlistGroups.filter { it.value.isNotEmpty() }

    // Movies are kept individual
    val individualDownloads = downloads.filter { it.mediaType == "movie" }

    // Active bottom sheet series tracking
    // (Series open as a full NavHost page now — see PlaylistFolderCard.onClick)

    // Force Arabic Layout Direction RTL Globally on pages
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
                // iOS Styled Custom Premium Header with RTL alignment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "التحميلات غير المتصلة",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowCircleDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Elegant iOS Segment Bar layout with custom touch feedback ripples
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { activeSegmentTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // RENDER SELECTED SECTION
                when (activeSegmentTab) {
                    0 -> {
                        // SERIES PLAYLISTS
                        if (seriesPlaylists.isEmpty()) {
                            EmptyDownloadsView(message = "لا تملك مسلسلات منزلة بعد. قم بتحميل حلقات مسلسل لتنظيمها وعرضها هنا.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(seriesPlaylists.keys.toList()) { mediaId ->
                                    val playlistEpisodes = playlistGroups[mediaId] ?: emptyList()
                                    val parentTitle = playlistEpisodes.firstOrNull()?.title?.substringBefore(" - ") ?: "مسلسل"
                                    // posterPath is now always the series poster (stored at download time)
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
                            EmptyDownloadsView(message = "لا توجد أفلام أو تنزيلات فردية")
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
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

                // Files Storage Directory Location Notice Card (only in non-local tabs)
                if (activeSegmentTab != 2) {
                    val exactPath = remember(context) {
                        File(context.filesDir, "downloads").absolutePath
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "مسار حفظ الملفات على الجهاز:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = exactPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
