package com.example.ui.components.moviebox

import com.example.data.remote.moviebox.crypto.MovieBoxSigner
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.moviebox.models.VideoFile
import com.example.data.remote.moviebox.viewmodel.MovieBoxState
import com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel
import com.example.ui.components.CircularSelectionIndicator
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PaletteSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieBoxDownloadSheet(
    movieTitle: String,
    movieYear: Int?,
    mediaType: String,
    seasonInfo: Int? = null,
    episodeInfo: Int? = null,
    viewModel: MovieBoxViewModel,
    onDismissRequest: () -> Unit,
    onTryOtherMethod: () -> Unit,
    onDownloadClick: (String, String, Int, Int, String) -> Unit,
    episodeStillPaths: Map<Pair<Int, Int>, String> = emptyMap(),
    alreadyDownloaded: (Int, Int, String) -> Boolean = { _, _, _ -> false }
) {
    val context = LocalContext.current
    val searchState by viewModel.searchResults.collectAsState()
    val downloadLinksState by viewModel.downloadLinks.collectAsState()
    val qualityPrefs = context.getSharedPreferences("quality_prefs", Context.MODE_PRIVATE)

    // Quality + batch-selection state hoisted to the sheet level so the header quality
    // dropdown and the episodes list share it (and selection can be cleared on change).
    val standardQualities = listOf(1080, 720, 480, 360)
    val savedQuality = qualityPrefs.getInt("q_${movieTitle.replace(" ", "_")}", 1080)
    var selectedQuality by remember { mutableIntStateOf(if (savedQuality in standardQualities) savedQuality else 1080) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }
    var selectedEpisodeIds by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(selectedQuality) {
        qualityPrefs.edit().putInt("q_${movieTitle.replace(" ", "_")}", selectedQuality).apply()
    }

    var subjectId by remember { mutableStateOf<String?>(null) }
    var searchInitiated by remember { mutableStateOf(false) }

    LaunchedEffect(movieTitle) {
        if (!searchInitiated) {
            searchInitiated = true
            viewModel.search(movieTitle)
        }
    }

    LaunchedEffect(searchState) {
        if (searchState is MovieBoxState.Success) {
            val results = (searchState as MovieBoxState.Success).data
            val matchedResult = results.firstOrNull { 
                it.title.equals(movieTitle, ignoreCase = true) && 
                (movieYear == null || it.year == movieYear.toString())
            } ?: results.firstOrNull() // Fallback to first

            if (matchedResult != null) {
                subjectId = matchedResult.subjectId
                viewModel.getDownloadLinks(matchedResult.subjectId)
            } else {
                Toast.makeText(context, "لم يتم العثور على نتائج في MovieBox", Toast.LENGTH_SHORT).show()
                onTryOtherMethod()
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        modifier = Modifier,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: title (right) + quality dropdown (left, TV only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (mediaType == "tv") "تنزيل الحلقات" else "تحميل الفيلم",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End
                )
                if (mediaType == "tv") {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { qualityMenuExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${selectedQuality}p",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = JetBrainsMonoFontFamily
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = qualityMenuExpanded,
                            onDismissRequest = { qualityMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shadowElevation = 8.dp,
                            tonalElevation = 0.dp
                        ) {
                            standardQualities.forEach { q ->
                                DropdownMenuItem(
                                    text = { Text("${q}p", fontFamily = JetBrainsMonoFontFamily) },
                                    onClick = {
                                        selectedQuality = q
                                        qualityMenuExpanded = false
                                        selectedEpisodeIds = emptySet()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            when {
                searchState is MovieBoxState.Loading || (searchState is MovieBoxState.Success && (downloadLinksState is MovieBoxState.Loading || downloadLinksState is MovieBoxState.Idle)) -> {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                    Text(
                        text = "جاري البحث عن الروابط...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                searchState is MovieBoxState.Idle -> {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                    Text(
                        text = "جاري البحث...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                searchState is MovieBoxState.Error -> {
                    ErrorState(
                        message = (searchState as MovieBoxState.Error).message,
                        onTryOtherMethod = onTryOtherMethod
                    )
                }
                downloadLinksState is MovieBoxState.Error -> {
                    ErrorState(
                        message = (downloadLinksState as MovieBoxState.Error).message,
                        onTryOtherMethod = onTryOtherMethod
                    )
                }
                downloadLinksState is MovieBoxState.Success -> {
                    val links = (downloadLinksState as MovieBoxState.Success).data
                    
                    if (links.isEmpty()) {
                        ErrorState(
                            message = "لا توجد روابط تحميل متاحة.",
                            onTryOtherMethod = onTryOtherMethod
                        )
                    } else {
                        if (mediaType == "tv") {
                            var filteredLinks = links
                            if (seasonInfo != null && seasonInfo > 0 && episodeInfo != null && episodeInfo > 0) {
                                filteredLinks = links.filter { it.season == seasonInfo && it.episode == episodeInfo }
                            }
                            
                            val availableSeasons = filteredLinks.map { it.season }.distinct().sorted()
                            var selectedSeason by remember { mutableIntStateOf(seasonInfo ?: availableSeasons.firstOrNull() ?: 1) }
                            
                            val seasonLinks = filteredLinks.filter { it.season == selectedSeason }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Season selector
                                if (availableSeasons.size > 1 && (seasonInfo == null || seasonInfo <= 0)) {
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        items(availableSeasons) { s ->
                                            val isSelected = s == selectedSeason
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { selectedSeason = s; selectedEpisodeIds = emptySet() }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = "الموسم $s",
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Episodes list with batch selection
                                val episodesMap = seasonLinks.groupBy { it.episode }.toSortedMap(compareBy { it })
                                val episodeIds = episodesMap.keys.toList()
                                val eligibleIds = episodeIds.filter { epId ->
                                    val files = episodesMap[epId] ?: return@filter false
                                    val exact = files.any { it.resolution == selectedQuality }
                                    val downloaded = alreadyDownloaded(selectedSeason, epId, "${selectedQuality}p")
                                    exact && !downloaded
                                }

                                // Toggle helpers reuse the hoisted selection set
                                fun toggleEpisode(id: Int) {
                                    selectedEpisodeIds = if (id in selectedEpisodeIds) selectedEpisodeIds - id else selectedEpisodeIds + id
                                }

                                // Select All / Deselect All — only eligible (not already downloaded, exact quality)
                                val allSelected = eligibleIds.isNotEmpty() && selectedEpisodeIds.size == eligibleIds.size
                                fun toggleSelectAll() {
                                    selectedEpisodeIds = if (allSelected) emptySet() else eligibleIds.toSet()
                                }
                                
                                // Batch download button (shown when items selected) — animated
                                AnimatedVisibility(
                                    visible = selectedEpisodeIds.isNotEmpty(),
                                    enter = fadeIn(tween(200)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(200)),
                                    exit = fadeOut(tween(150)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(150))
                                ) {
                                    Button(
                                        onClick = {
                                            selectedEpisodeIds.forEach { epId ->
                                                val file = seasonLinks.find { it.episode == epId && it.resolution == selectedQuality }
                                                    ?: seasonLinks.filter { it.episode == epId }.minByOrNull { Math.abs(it.resolution - selectedQuality) }
                                                if (file != null) {
                                                    val still = episodeStillPaths[selectedSeason to epId] ?: ""
                                                    onDownloadClick(file.url, "${file.resolution}p", selectedSeason, epId, still)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                        Text("تحميل المحدد (${selectedEpisodeIds.size} / ${episodeIds.size})")
                                    }
                                }
                                
                                // Select / Deselect All row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Checkbox(checked = allSelected, onCheckedChange = { toggleSelectAll() })
                                    Text(
                                        text = if (allSelected) "إلغاء تحديد الكل" else "تحديد الكل",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    episodesMap.forEach { (episodeId, files) ->
                                        item {
                                            val exactFile = files.find { it.resolution == selectedQuality }
                                            val nearestFile = files.minByOrNull { Math.abs(it.resolution - selectedQuality) }
                                            val isExact = exactFile != null
                                            val isSelected = episodeId in selectedEpisodeIds
                                            val alpha = if (isExact) 1f else 0.5f
                                            
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f * alpha)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    .graphicsLayer(alpha = alpha)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val isDownloadedHere = alreadyDownloaded(selectedSeason, episodeId, "${selectedQuality}p")
                                                    if (isExact && !isDownloadedHere) {
                                                        CircularSelectionIndicator(
                                                            isSelected = isSelected,
                                                            onClick = { toggleEpisode(episodeId) },
                                                            modifier = Modifier.padding(end = 12.dp)
                                                        )
                                                    } else {
                                                        Spacer(modifier = Modifier.width(44.dp))
                                                    }
                                                    Text(
                                                        text = "الحلقة $episodeId",
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 14.sp,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (isDownloadedHere) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.CheckCircle, "تم التحميل", tint = PaletteSuccess, modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("تم التحميل", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    } else if (isExact) {
                                                        Text(
                                                            text = formatSize(exactFile.size),
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontFamily = JetBrainsMonoFontFamily
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        IconButton(
                                                            onClick = {
                                                                val still = episodeStillPaths[selectedSeason to episodeId] ?: ""
                                                                onDownloadClick(exactFile.url, "${exactFile.resolution}p", selectedSeason, episodeId, still)
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Download, "تحميل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                        }
                                                    } else {
                                                        Text(
                                                            text = "غير متوفر",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Movie: Show qualities
                            val availableQualities = links.distinctBy { it.resolution }.sortedByDescending { it.resolution }
                            
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(availableQualities) { videoFile ->
                                    QualityItem(
                                        videoFile = videoFile,
                                        onClick = {
                                            onDownloadClick(videoFile.url, "${videoFile.resolution}p", 0, 0, "")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ErrorState(message: String, onTryOtherMethod: () -> Unit) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onTryOtherMethod) {
                Text("جرب الطريقة الأخرى")
            }
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Error message", message)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "تم نسخ الخطأ", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("نسخ الخطأ")
            }
        }
    }
}

@Composable
private fun QualityItem(videoFile: VideoFile, onClick: () -> Unit) {
    val resolutionName = when (videoFile.resolution) {
        1080 -> "FHD (دقة فائقة الوضوح)"
        720 -> "HD (دقة عالية الوضوح)"
        480 -> "SD (دقة قياسية توفيرية)"
        360 -> "Low (توفير أقصى)"
        else -> "${videoFile.resolution}p"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تحميل بدقة ${videoFile.resolution}p",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatSize(videoFile.size),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = JetBrainsMonoFontFamily
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = "تحميل", modifier = Modifier.padding(end = 8.dp))
            Text("تحميل الفيلم")
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    return if (size >= 1_000_000_000) {
        String.format("%.1fGB", size / 1_000_000_000.0)
    } else {
        String.format("%dMB", size / 1_000_000)
    }
}

@Composable
private fun QualityBadge(resolution: Int, isAvailable: Boolean, onClick: () -> Unit) {
    val resName = when (resolution) {
        1080 -> "FHD"
        720 -> "HD"
        480 -> "SD"
        else -> "${resolution}p"
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isAvailable) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            .clickable(enabled = isAvailable, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Text(
                text = resName,
                color = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
             )
             if (!isAvailable) {
                 Text("غير متوفرة", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
             }
        }
    }
}
