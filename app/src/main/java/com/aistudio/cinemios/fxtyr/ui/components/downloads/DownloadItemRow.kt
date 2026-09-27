package com.aistudio.cinemios.fxtyr.ui.components.downloads

import java.io.File
import kotlinx.coroutines.launch
import com.aistudio.cinemios.fxtyr.ui.viewmodel.SubtitleHelper

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.data.local.DownloadEntity
import com.aistudio.cinemios.fxtyr.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteMutedRed
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import com.aistudio.cinemios.fxtyr.utils.isLatinText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadItemRow(
    item: DownloadEntity,
    viewModel: MovieViewModel,
    onPlayClick: (String?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isCompleted = item.status == "completed"
    val isPaused = item.status == "paused"
    val posterUrl = if (item.posterPath.startsWith("http")) item.posterPath else "https://image.tmdb.org/t/p/w185${item.posterPath}"
    val episodeStillUrl = if (item.stillPath.isNotEmpty()) "https://image.tmdb.org/t/p/w300${item.stillPath}" else null
    var showContextMenu by remember { mutableStateOf(false) }

    // Custom press effect (replaces default ripple) — subtle scale + alpha on touch-down
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.92f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, animationSpec = tween(150))

    // File size is read off the main thread (File.length() is still I/O) and shown once available
    var fileSizeText by remember(item.id) { mutableStateOf("...") }
    LaunchedEffect(item.id) {
        fileSizeText = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(item.localFilePath)
                if (f.name == "index.mpd") {
                    val total = f.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
                    formatBytes(total)
                } else {
                    formatBytes(f.length())
                }
            }.getOrDefault("...")
        }
    }

    val partialFile = remember(item.id) {
        val dashIndex = java.io.File(context.filesDir, "downloads/${item.id}/index.mpd")
        if (dashIndex.exists()) dashIndex else java.io.File(context.filesDir, "downloads/${item.id}.mp4")
    }
    val partialFilePath = partialFile.absolutePath

    val subtitleDownloads by viewModel.subtitleDownloads.collectAsState(initial = emptyList())
    val downloadScope = rememberCoroutineScope()
    var isDownloadingSubtitle by remember { mutableStateOf(false) }
    val hasSubtitle = remember(item.id, subtitleDownloads) {
        subtitleDownloads.any { sub ->
            if (item.mediaType == "tv")
                sub.tmdbId == item.mediaId && sub.season == item.season && sub.episode == item.episode
            else
                sub.tmdbId == item.mediaId
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .alpha(pressAlpha)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (isCompleted) {
                        onPlayClick(item.localFilePath)
                    } else if (isPaused) {
                        viewModel.resumeDownload(item.id)
                    } else {
                        viewModel.pauseDownload(item.id)
                    }
                },
                onLongClick = { showContextMenu = true }
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Mini Poster (use episode still if available for landscape 16:9)
        val thumbUrl = if (item.mediaType == "tv" && episodeStillUrl != null) episodeStillUrl else posterUrl
        Box(
            modifier = Modifier
                .size(if (item.mediaType == "tv" && episodeStillUrl != null) androidx.compose.ui.unit.DpSize(60.dp, 34.dp) else androidx.compose.ui.unit.DpSize(60.dp, 86.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Details Block
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (item.mediaType == "tv" && item.episode > 0) {
                // For TV episodes: show episode number, not series name (already in header)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الحلقة ${item.episode}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    if (item.season > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "الموسم ${item.season}",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.quality,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            } else {
                // For movies: show the movie title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = if (isLatinText(item.title)) JetBrainsMonoFontFamily else null),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "الفيلم السينمائي",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.quality,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            if (!isCompleted) {
                val formattedDownloaded = formatBytes(item.downloadedBytes)
                val formattedTotal = formatBytes(item.totalBytes)
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val progressText = if (item.mediaType == "tv" || item.totalBytes == item.downloadedBytes) {
                        formattedDownloaded
                    } else {
                        "$formattedDownloaded / $formattedTotal"
                    }
                    Text(
                        text = progressText,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = item.downloadSpeed,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = "${item.progress}%",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Last watched time (real from SharedPreferences)
                val prefs = context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE)
                val lastPos = prefs.getLong("pos_${item.id}", 0L)
                val hasProgress = lastPos > 0
                
                // Get video duration from MediaMetadataRetriever (cached via File length estimate)
                val durationSecs = try {
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        retriever.release()
                        durStr?.toLongOrNull()?.let { it / 1000 } ?: 0L
                    } else 0L
                } catch (_: Exception) { 0L }
                
                if (hasProgress && durationSecs > 0) {
                    val posSecs = lastPos / 1000
                    val progress = (posSecs.toFloat() / durationSecs.toFloat()).coerceIn(0f, 1f)
                    val posMins = posSecs / 60
                    val posSecsRem = posSecs % 60
                    val durMins = durationSecs / 60
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    // Gradient watch-progress bar (Cyan → Green)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Cyan, Color.Green)
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$posMins:${String.format("%02d", posSecsRem)} / $durMins دقيقة",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                } else if (hasProgress) {
                    val posSecs = lastPos / 1000
                    val posMins = posSecs / 60
                    val posSecsRem = posSecs % 60
                    Text(
                        text = "آخر مشاهدة: $posMins:${String.format("%02d", posSecsRem)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "جاهز للمشاهدة بدون اتصال ($fileSizeText)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (isDownloadingSubtitle) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "يتم جلب الترجمة..",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (hasSubtitle) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = "ترجمة",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        }

        // Long-press context menu (replaces the removed 3-dots ModalBottomSheet)
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp
        ) {
            if (!isCompleted) {
                DropdownMenuItem(
                    text = { Text("مشاهدة ما تم تحميله") },
                    onClick = {
                        showContextMenu = false
                        if (java.io.File(partialFilePath).exists()) {
                            onPlayClick(partialFilePath)
                        } else {
                            android.widget.Toast.makeText(context, "الملف غير جاهز بعد", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                DropdownMenuItem(
                    text = { Text(if (isPaused) "استئناف التحميل" else "إيقاف التحميل") },
                    onClick = {
                        showContextMenu = false
                        if (isPaused) viewModel.resumeDownload(item.id)
                        else viewModel.pauseDownload(item.id)
                    },
                    leadingIcon = { Icon(if (isPaused) Icons.Default.FileDownload else Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            if (isCompleted) {
                DropdownMenuItem(
                    text = { Text("حفظ الفيديو في المعرض") },
                    onClick = {
                        showContextMenu = false
                        try {
                            if (item.localFilePath.endsWith(".mpd")) {
                                android.widget.Toast.makeText(context, "هذا المحتوى بتنسيق DASH مخصص للمشاهدة بدون إنترنت داخل التطبيق", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                val destDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
                                if (!destDir.exists()) destDir.mkdirs()
                                val safeTitle = item.title.replace("/", "_").replace("\\", "_")
                                val destFile = java.io.File(destDir, "$safeTitle.mp4")
                                java.io.File(item.localFilePath).copyTo(destFile, overwrite = true)
                                android.widget.Toast.makeText(context, "تم حفظ الفيديو للمعرض (${destFile.absolutePath})", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "خطأ أثناء الحفظ: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            DropdownMenuItem(
                text = { Text("تحميل الترجمة") },
                onClick = {
                    showContextMenu = false
                    isDownloadingSubtitle = true
                    context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("has_fetched_subtitle_once", true).apply()
                    downloadScope.launch {
                        val tmdbId = if (item.mediaType == "tv") item.mediaId.substringBefore("-s") else item.mediaId
                        val file = SubtitleHelper.fetchAndSaveMovieBoxSubtitle(
                            context, tmdbId, item.mediaType == "tv", item.season, item.episode, item.title, item.id
                        )
                        isDownloadingSubtitle = false
                        android.widget.Toast.makeText(
                            context,
                            if (file != null) "✓ تم تحميل الترجمة العربية" else "لم يتم العثور على ترجمة عربية",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                leadingIcon = {
                    if (isDownloadingSubtitle) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("حذف الملف", color = PaletteMutedRed) },
                onClick = {
                    showContextMenu = false
                    viewModel.deleteDownload(item.id)
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PaletteMutedRed, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}
