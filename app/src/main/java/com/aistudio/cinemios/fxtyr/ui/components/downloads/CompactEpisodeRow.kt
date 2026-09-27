package com.aistudio.cinemios.fxtyr.ui.components.downloads

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.data.local.DownloadEntity
import com.aistudio.cinemios.fxtyr.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteMutedRed
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Compact row for bottom sheet episode list (no card background, larger thumb, gradient progress bar)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CompactEpisodeRow(
    item: DownloadEntity,
    viewModel: MovieViewModel,
    onPlayClick: (String?) -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    dimAlpha: Float = 1f,
    isContextMenuTarget: Boolean = false,
    menuOpen: Boolean = false,
    lastTargetId: String? = null,
    onLongClick: () -> Unit = {},
    onCheckedChange: (Boolean) -> Unit = {},
    onDismissContextMenu: () -> Unit = {},
    onEnterMultiSelect: () -> Unit = {},
    onSubtitleFetched: () -> Unit = {},
    durationCache: MutableMap<String, Long> = mutableMapOf(),
    sizeCache: MutableMap<String, String> = mutableMapOf()
) {
    val context = LocalContext.current
    val isCompleted = item.status == "completed"
    val isPaused = item.status == "paused" || item.status == "error"
    val isDownloading = !isCompleted && !isPaused && item.status != "queued"

    // Duration + size are loaded OFF the main thread (MediaMetadataRetriever / file stat are blocking I/O)
    // and cached per-session so switching seasons back and forth doesn't re-read the file from disk.
    var durationSecs by remember(item.id) { mutableStateOf(durationCache[item.id] ?: -1L) }
    var fileSizeText by remember(item.id) { mutableStateOf(sizeCache[item.id] ?: "...") }
    LaunchedEffect(item.id, isCompleted) {
        if (isCompleted && (durationSecs < 0 || fileSizeText == "...")) {
            val (secs, sizeStr) = withContext(Dispatchers.IO) {
                val file = File(item.localFilePath)
                val len = if (file.exists()) {
                    if (file.name == "index.mpd") {
                        file.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
                    } else {
                        file.length()
                    }
                } else 0L
                val dur = try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    durStr?.toLongOrNull()?.let { it / 1000 } ?: 0L
                } catch (_: Exception) { 0L }
                dur to formatBytes(len)
            }
            if (durationSecs < 0) { durationCache[item.id] = secs; durationSecs = secs }
            if (fileSizeText == "...") { sizeCache[item.id] = sizeStr; fileSizeText = sizeStr }
        }
    }
    val subtitleDownloads by viewModel.subtitleDownloads.collectAsState(initial = emptyList())
    val compactScope = rememberCoroutineScope()
    var isDownloadingSubtitle by remember { mutableStateOf(false) }
    val hasSubtitle = remember(item.id, subtitleDownloads) {
        subtitleDownloads.any { sub ->
            if (item.mediaType == "tv")
                sub.tmdbId == item.mediaId && sub.season == item.season && sub.episode == item.episode
            else
                sub.tmdbId == item.mediaId
        }
    }

    // Pulse animation for active downloading thumbnail overlay
    val infiniteTransition = rememberInfiniteTransition(label = "downloadPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    // Use stillPath for episode thumbnail, fall back to posterPath
    val thumbUrl = if (item.stillPath.isNotEmpty()) {
        if (item.stillPath.startsWith("http")) item.stillPath else "https://image.tmdb.org/t/p/w300${item.stillPath}"
    } else {
        if (item.posterPath.startsWith("http")) item.posterPath else "https://image.tmdb.org/t/p/w300${item.posterPath}"
    }

    // Custom press effect (replaces default ripple) — subtle scale + alpha on touch-down
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.92f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (isContextMenuTarget) 1f else if (pressed) 0.98f else 1f, animationSpec = tween(150))

    // Watch progress (durationSecs / fileSizeText are loaded async + cached above)
    val prefs = context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE)
    val lastPos = prefs.getLong("pos_${item.id}", 0L)
    val progress = if (isCompleted && durationSecs > 0 && lastPos > 0) {
        (lastPos.toFloat() / 1000f / durationSecs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .alpha(
                pressAlpha * (
                    if (isContextMenuTarget) 1f
                    else if (!menuOpen && lastTargetId == item.id) 1f
                    else dimAlpha
                )
            )
    ) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            if (isSelectionMode) {
                                onCheckedChange(!isSelected)
                            } else {
                                if (isCompleted) onPlayClick(item.localFilePath)
                                else if (isPaused) viewModel.resumeDownload(item.id)
                                else if (!isCompleted) viewModel.pauseDownload(item.id)
                            }
                        },
                        onLongClick = {
                            if (isSelectionMode) {
                                onCheckedChange(!isSelected)
                            } else {
                                onLongClick()
                            }
                        }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular selection indicator (replaces Checkbox) — animates in/out with selection mode
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = scaleIn(spring()) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    CircularSelectionIndicator(
                        isSelected = isSelected,
                        onClick = { onCheckedChange(!isSelected) }
                    )
                }

                // Thumbnail with gradient progress bar at bottom
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 50.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (!isCompleted) Modifier.alpha(0.55f) else Modifier)
                ) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Pulse overlay during active download
                    if (isDownloading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(pulseAlpha)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        )
                    }
                    // Gradient progress bar at bottom of thumbnail
                    if (isCompleted && progress > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(5.dp)
                        ) {
                            // Track background
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                            )
                            // Active progress with gradient
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
                    }
                }

                // Details
                Column(
                    modifier = Modifier.weight(1f)
                        .then(if (!isCompleted) Modifier.alpha(0.55f) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Episode number + quality
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "الحلقة ${item.episode}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        }
                        if (isDownloadingSubtitle) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                        } else if (hasSubtitle) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "ترجمة",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (isCompleted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (durationSecs > 0 && lastPos > 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val posSecs = lastPos / 1000
                                    val durMins = durationSecs / 60
                                    val durSecs = durationSecs % 60
                                    // Watched time (green) / total time (faded)
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFontFamily)) {
                                                append("${posSecs / 60}:${String.format("%02d", posSecs % 60)}")
                                            }
                                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontWeight = FontWeight.Medium, fontFamily = JetBrainsMonoFontFamily)) {
                                                append("/$durMins:${String.format("%02d", durSecs)}")
                                            }
                                        },
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "—",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                    )
                                }
                            }
                            if (fileSizeText != "...") {
                                Text(
                                    text = fileSizeText,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            }
                        }
                    } else {
                        // Download progress — NO transparency
                        val formattedDownloaded = formatBytes(item.downloadedBytes)
                        val formattedTotal = formatBytes(item.totalBytes)
                        Text(
                            text = if (item.totalBytes == item.downloadedBytes) formattedDownloaded else "$formattedDownloaded / $formattedTotal",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontFamily = JetBrainsMonoFontFamily
                        )
                        LinearProgressIndicator(
                            progress = { item.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                // Play/Pause button — hidden when completed (whole card is clickable)
                if (!isCompleted) {
                    IconButton(
                        onClick = {
                            if (isPaused) viewModel.resumeDownload(item.id)
                            else viewModel.pauseDownload(item.id)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "استئناف" else "إيقاف مؤقت",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
        }

        // DropdownMenu anchored to this row when it is the long-press target
        DropdownMenu(
            expanded = isContextMenuTarget,
            onDismissRequest = onDismissContextMenu,
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
                        onDismissContextMenu()
                        val partialPath = File(context.filesDir, "downloads/${item.id}.mp4").absolutePath
                        if (File(partialPath).exists()) {
                            onPlayClick(partialPath)
                        } else {
                            android.widget.Toast.makeText(context, "الملف غير جاهز بعد", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                DropdownMenuItem(
                    text = { Text(if (isPaused) "استئناف التحميل" else "إيقاف التحميل") },
                    onClick = {
                        onDismissContextMenu()
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
                        onDismissContextMenu()
                        try {
                            if (item.localFilePath.endsWith(".mpd")) {
                                android.widget.Toast.makeText(context, "هذا المحتوى بتنسيق DASH مخصص للمشاهدة بدون إنترنت داخل التطبيق", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                val destDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
                                if (!destDir.exists()) destDir.mkdirs()
                                val safeTitle = item.title.replace("/", "_").replace("\\", "_")
                                val destFile = File(destDir, "$safeTitle.mp4")
                                File(item.localFilePath).copyTo(destFile, overwrite = true)
                                android.widget.Toast.makeText(context, "تم حفظ الفيديو للمعرض", android.widget.Toast.LENGTH_LONG).show()
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
                    onDismissContextMenu()
                    isDownloadingSubtitle = true
                    onSubtitleFetched()
                    context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("has_fetched_subtitle_once", true).apply()
                    compactScope.launch {
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
                    onDismissContextMenu()
                    viewModel.deleteDownload(item.id)
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PaletteMutedRed, modifier = Modifier.size(18.dp)) }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("تحديد متعدد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                onClick = {
                    onEnterMultiSelect()
                },
                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
