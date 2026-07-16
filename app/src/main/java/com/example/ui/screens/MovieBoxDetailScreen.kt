package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.moviebox.models.*
import com.example.ui.viewmodel.MovieViewModel
import kotlinx.coroutines.launch
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieBoxDetailScreen(
    subjectId: String,
    mediaType: String,
    titleParams: String = "",
    posterParams: String = "",
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onPlayClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val decodedTitle = remember(titleParams) {
        try { URLDecoder.decode(titleParams, "UTF-8") } catch (_: Exception) { titleParams }
    }
    val decodedPoster = remember(posterParams) {
        try { URLDecoder.decode(posterParams, "UTF-8") } catch (_: Exception) { posterParams }
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var itemDetail by remember { mutableStateOf<ItemDetailResult?>(null) }
    var videoLinks by remember { mutableStateOf<List<VideoFile>>(emptyList()) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var subtitleFiles by remember { mutableStateOf<List<Subtitle>>(emptyList()) }

    val isFavorited by viewModel.isItemInWatchlist(subjectId).collectAsState(initial = false)
    val scrollState = rememberScrollState()

    LaunchedEffect(subjectId, retryTrigger) {
        isLoading = true
        errorMessage = null
        try {
            val detailsRes = kotlinx.coroutines.withTimeout(15_000L) {
                viewModel.movieBoxRepository.itemDetails(subjectId)
            }
            detailsRes.onSuccess { itemDetail = it }

            val linksRes = kotlinx.coroutines.withTimeout(30_000L) {
                viewModel.movieBoxRepository.getDownloadLinks(subjectId, null)
            }
            linksRes.onSuccess { videoLinks = it }

            if (videoLinks.isEmpty() && itemDetail == null) {
                errorMessage = "لم يتم العثور على بيانات لهذا المحتوى."
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            errorMessage = "انتهت مهلة الاتصال."
        } catch (e: Exception) {
            errorMessage = "حدث خطأ: ${e.localizedMessage ?: e.message ?: "غير معروف"}"
        } finally {
            isLoading = false
        }
    }

    val displayTitle = itemDetail?.title?.takeIf { it.isNotBlank() } ?: decodedTitle
    val displayPoster = itemDetail?.posterUrl?.takeIf { it.isNotBlank() } ?: decodedPoster
    val isTv = mediaType == "tv" || itemDetail?.type == "series"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(264.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = displayPoster,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
                    )
                    AsyncImage(
                        model = displayPoster,
                        contentDescription = displayTitle,
                        modifier = Modifier
                            .width(110.dp).height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                            .align(Alignment.BottomCenter),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (errorMessage != null && itemDetail == null) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 16.dp), textAlign = TextAlign.Center)
                            Button(onClick = { retryTrigger++ }) { Text("إعادة المحاولة") }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val year = itemDetail?.year?.takeIf { it.isNotBlank() }
                            if (year != null) {
                                Text(year, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
                                Text("•", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                            }
                            val rating = itemDetail?.rating ?: 0.0
                            if (rating > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(String.format("%.1f", rating), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text("•", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                            }
                            val seconds = itemDetail?.durationSeconds ?: 0
                            if (seconds > 0 && !isTv) {
                                Text("${seconds / 60} دقيقة", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
                            } else if (isTv) {
                                val s = itemDetail?.seasonsCount?.takeIf { it > 0 } ?: videoLinks.map { it.season }.distinct().size
                                Text("$s مواسم", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        val genres = itemDetail?.genre ?: emptyList()
                        if (genres.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                items(genres) { genre ->
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .clip(RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(genre, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    if (videoLinks.isNotEmpty()) onPlayClick(subjectId, displayTitle)
                                    else Toast.makeText(context, "لا توجد روابط للمشاهدة", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1.5f).height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("مشاهدة الآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        viewModel.toggleWatchlist(
                                            id = subjectId, title = displayTitle,
                                            posterPath = displayPoster,
                                            mediaType = if (isTv) "tv" else "movie",
                                            rating = itemDetail?.rating ?: 0.0
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFavorited) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "قائمة المشاهدة",
                                    tint = if (isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                )
                            }

                            IconButton(
                                onClick = {
                                    coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                                },
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(Icons.Default.ArrowCircleDown, contentDescription = "تحميل", tint = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(
                                onClick = {
                                    val allSubs = videoLinks.flatMap { it.allSubtitles }.distinctBy { it.languageCode }
                                    if (allSubs.isNotEmpty()) {
                                        subtitleFiles = allSubs
                                        showSubtitleSheet = true
                                    } else {
                                        Toast.makeText(context, "لا توجد ترجمات متوفرة", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(Icons.Default.Subtitles, contentDescription = "ترجمة", tint = Color.Black)
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        val desc = itemDetail?.description?.takeIf { it.isNotBlank() }
                        if (desc != null) {
                            Text(
                                text = if (isTv) "قصة المسلسل" else "قصة الفيلم",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(24.dp))
                        }

                        if (videoLinks.isEmpty()) {
                            Text(
                                text = "لا توجد روابط تحميل متاحة.",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp)
                            )
                        } else if (isTv) {
                            TvDownloadSection(subjectId, displayTitle, displayPoster, videoLinks, viewModel, context)
                        } else {
                            MovieDownloadSection(subjectId, displayTitle, displayPoster, videoLinks, viewModel, context)
                        }

                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }

    if (showSubtitleSheet && subtitleFiles.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showSubtitleSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("الترجمات المتوفرة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                subtitleFiles.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            Toast.makeText(context, "رابط الترجمة: ${sub.url}", Toast.LENGTH_LONG).show()
                        }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sub.languageName, fontWeight = FontWeight.Medium)
                        Text(sub.languageCode, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TvDownloadSection(
    subjectId: String, displayTitle: String, displayPoster: String,
    videoLinks: List<VideoFile>, viewModel: MovieViewModel, context: android.content.Context
) {
    val availableSeasons = remember(videoLinks) { videoLinks.map { it.season }.distinct().sorted() }
    var selectedSeason by remember { mutableIntStateOf(availableSeasons.firstOrNull() ?: 1) }
    var selectedQuality by remember { mutableIntStateOf(0) }
    val seasonLinks = videoLinks.filter { it.season == selectedSeason }
    val availableQualities = remember(seasonLinks) { seasonLinks.map { it.resolution }.distinct().sortedDescending() }

    LaunchedEffect(selectedSeason, seasonLinks) {
        if (selectedQuality !in seasonLinks.map { it.resolution }) {
            selectedQuality = availableQualities.firstOrNull() ?: 0
        }
    }

    Text("الحلقات والمواسم", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))

    if (availableSeasons.size > 1) {
        LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            items(availableSeasons) { s ->
                val sel = s == selectedSeason
                Box(Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { selectedSeason = s }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("الموسم $s", color = if (sel) Color.White else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (availableQualities.isNotEmpty()) {
        LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            items(availableQualities) { q ->
                val sel = q == selectedQuality
                Box(Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { selectedQuality = q }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("${q}p", color = if (sel) Color.White else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    val episodes = remember(seasonLinks, selectedQuality) {
        seasonLinks.filter { selectedQuality == 0 || it.resolution == selectedQuality }
            .groupBy { it.episode }.toSortedMap()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        episodes.forEach { (episodeNum, files) ->
            val file = files.firstOrNull() ?: return@forEach
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("الحلقة $episodeNum", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    val sz = file.formattedSize()
                    if (sz.isNotEmpty()) Text(sz, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (file.subtitlesAvailable) {
                    Spacer(Modifier.height(4.dp))
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    viewModel.requestDownload(
                        mediaId = subjectId, title = displayTitle, posterPath = displayPoster,
                        mediaType = "tv", season = selectedSeason, episode = episodeNum,
                        quality = "${selectedQuality}p", customUrl = file.url
                    )
                    Toast.makeText(context, "بدأ التحميل", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("تحميل الحلقة")
                }
            }
        }
    }
}

@Composable
private fun MovieDownloadSection(
    subjectId: String, displayTitle: String, displayPoster: String,
    videoLinks: List<VideoFile>, viewModel: MovieViewModel, context: android.content.Context
) {
    val qualities = remember(videoLinks) { videoLinks.distinctBy { it.resolution }.sortedByDescending { it.resolution } }

    Text("اختر الجودة للتحميل:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        qualities.forEach { videoFile ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("دقة ${videoFile.resolution}p", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    val sz = videoFile.formattedSize()
                    if (sz.isNotEmpty()) Text(sz, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (videoFile.subtitlesAvailable) {
                    Spacer(Modifier.height(4.dp))
                    Text("الترجمة: ${if (videoFile.hasArabicSubtitle) "العربية متوفرة" else "متوفرة"}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    viewModel.requestDownload(
                        mediaId = subjectId, title = displayTitle, posterPath = displayPoster,
                        mediaType = "movie", season = 0, episode = 0,
                        quality = "${videoFile.resolution}p", customUrl = videoFile.url
                    )
                    Toast.makeText(context, "بدأ التحميل", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("تحميل الفيلم")
                }
            }
        }
    }
}
