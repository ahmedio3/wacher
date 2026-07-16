package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.remote.moviebox.models.*
import com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel
import com.example.ui.components.moviebox.MovieBoxDownloadSheet
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.utils.isLatinText
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.ViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val appContext = LocalContext.current.applicationContext as android.app.Application
    val movieBoxViewModel: MovieBoxViewModel = viewModel(factory = ViewModelFactory(appContext))

    val decodedTitle = remember(titleParams) { titleParams.replace("+", " ") }
    val decodedPoster = remember(posterParams) { posterParams.replace("+", " ") }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var itemDetail by remember { mutableStateOf<ItemDetailResult?>(null) }
    var videoLinks by remember { mutableStateOf<List<VideoFile>>(emptyList()) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var subtitleFiles by remember { mutableStateOf<List<Subtitle>>(emptyList()) }

    val isFavorited by viewModel.isItemInWatchlist(subjectId).collectAsState(initial = false)
    val scrollState = rememberScrollState()

    var showMovieBoxSheet by remember { mutableStateOf(false) }
    var pendingDownloadSeason by remember { mutableIntStateOf(0) }
    var pendingDownloadEpisode by remember { mutableIntStateOf(0) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .zIndex(1f)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (isLatinText(displayTitle)) JetBrainsMonoFontFamily else null,
                                    textDirection = if (isLatinText(displayTitle)) TextDirection.Ltr else TextDirection.Rtl
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = 44.dp)
                ) {
                    if (isLoading) {
                        DetailSkeleton()
                    } else if (errorMessage != null && itemDetail == null) {
                        ErrorContent(errorMessage!!)
                    } else {
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
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                                        )
                                    )
                            )
                            AsyncImage(
                                model = displayPoster,
                                contentDescription = displayTitle,
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                                    .align(Alignment.BottomCenter),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (isLatinText(displayTitle)) JetBrainsMonoFontFamily else null,
                                    textDirection = if (isLatinText(displayTitle)) TextDirection.Ltr else TextDirection.Unspecified
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val year = itemDetail?.year?.takeIf { it.isNotBlank() }
                                Text(
                                    text = year ?: "مجهول",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = if (isLatinText(year ?: "")) JetBrainsMonoFontFamily else null,
                                        textDirection = if (isLatinText(year ?: "")) TextDirection.Ltr else TextDirection.Unspecified
                                    )
                                )
                                Text(
                                    text = "•",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format("%.1f", itemDetail?.rating ?: 0.0),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            textDirection = TextDirection.Ltr
                                        )
                                    )
                                }
                                Text(
                                    text = "•",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                )
                                if (isTv) {
                                    val s = itemDetail?.seasonsCount?.takeIf { it > 0 } ?: videoLinks.map { it.season }.distinct().size
                                    Text(
                                        text = "$s مواسم",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    val seconds = itemDetail?.durationSeconds ?: 0
                                    Text(
                                        text = "${seconds / 60} دقيقة",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            val genres = itemDetail?.genre ?: emptyList()
                            if (genres.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                                ) {
                                    items(genres) { genre ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = genre,
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = if (isLatinText(genre)) JetBrainsMonoFontFamily else null,
                                                    textDirection = if (isLatinText(genre)) TextDirection.Ltr else TextDirection.Unspecified
                                                )
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            if (isTv) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            pendingDownloadSeason = 0
                                            pendingDownloadEpisode = 0
                                            showMovieBoxSheet = true
                                        },
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color.White)
                                            Text("تحميل المسلسل كاملًا", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .combinedClickable(
                                                onClick = {
                                                    viewModel.toggleWatchlist(
                                                        id = subjectId, title = displayTitle,
                                                        posterPath = displayPoster,
                                                        mediaType = "tv",
                                                        rating = itemDetail?.rating ?: 0.0
                                                    )
                                                },
                                                onLongClick = {}
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorited) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = "قائمة المشاهدة",
                                            tint = if (isFavorited) Color.White else MaterialTheme.colorScheme.onBackground
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val allSubs = videoLinks.flatMap { it.allSubtitles }.distinctBy { it.languageCode }
                                            if (allSubs.isNotEmpty()) {
                                                subtitleFiles = allSubs
                                                showSubtitleSheet = true
                                            } else {
                                                Toast.makeText(appContext, "لا توجد ترجمات متوفرة", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(imageVector = Icons.Default.Subtitles, contentDescription = "تحميل ترجمة", tint = Color.Black)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (videoLinks.isNotEmpty()) onPlayClick(subjectId, displayTitle)
                                            else Toast.makeText(appContext, "لا توجد روابط للمشاهدة", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1.5f).height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                                            Text("مشاهدة الآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .combinedClickable(
                                                onClick = {
                                                    viewModel.toggleWatchlist(
                                                        id = subjectId, title = displayTitle,
                                                        posterPath = displayPoster,
                                                        mediaType = "movie",
                                                        rating = itemDetail?.rating ?: 0.0
                                                    )
                                                },
                                                onLongClick = {}
                                            ),
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
                                            pendingDownloadSeason = 0
                                            pendingDownloadEpisode = 0
                                            showMovieBoxSheet = true
                                        },
                                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(imageVector = Icons.Default.ArrowCircleDown, contentDescription = "تحميل المشاهدة أوفلاين", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    IconButton(
                                        onClick = {
                                            val allSubs = videoLinks.flatMap { it.allSubtitles }.distinctBy { it.languageCode }
                                            if (allSubs.isNotEmpty()) {
                                                subtitleFiles = allSubs
                                                showSubtitleSheet = true
                                            } else {
                                                Toast.makeText(appContext, "لا توجد ترجمات متوفرة", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(imageVector = Icons.Default.Subtitles, contentDescription = "تحميل ترجمة", tint = Color.Black)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            val desc = itemDetail?.description?.takeIf { it.isNotBlank() }
                            if (desc != null) {
                                Text(
                                    text = if (isTv) "قصة المسلسل" else "قصة الفيلم",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 22.sp,
                                        fontFamily = if (isLatinText(desc)) JetBrainsMonoFontFamily else null,
                                        textDirection = if (isLatinText(desc)) TextDirection.Ltr else TextDirection.Unspecified
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                    textAlign = if (isLatinText(desc)) TextAlign.Left else TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            if (isTv && videoLinks.isNotEmpty()) {
                                val availableSeasons = remember(videoLinks) { videoLinks.map { it.season }.distinct().sorted() }
                                var selectedSeason by remember { mutableIntStateOf(availableSeasons.firstOrNull() ?: 1) }

                                Text(
                                    text = "الحلقات والمواسم",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (availableSeasons.size > 1) {
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                                    ) {
                                        items(availableSeasons) { s ->
                                            val sel = s == selectedSeason
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { selectedSeason = s }
                                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = "الموسم $s",
                                                    color = if (sel) Color.White else MaterialTheme.colorScheme.onBackground,
                                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                }

                                val seasonLinks = videoLinks.filter { it.season == selectedSeason }
                                val episodes = remember(seasonLinks) {
                                    seasonLinks.groupBy { it.episode }.toSortedMap()
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    episodes.forEach { (episodeNum, files) ->
                                        val file = files.firstOrNull() ?: return@forEach
                                        MovieBoxEpisodeRowCard(
                                            episodeNumber = episodeNum,
                                            videoFile = file,
                                            onClick = {
                                                pendingDownloadSeason = selectedSeason
                                                pendingDownloadEpisode = episodeNum
                                                showMovieBoxSheet = true
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            } else if (videoLinks.isEmpty() && !isLoading) {
                                Text(
                                    text = "لا توجد روابط تحميل متاحة.",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    }
                }
            }
        }

        if (showMovieBoxSheet) {
            MovieBoxDownloadSheet(
                movieTitle = displayTitle,
                movieYear = itemDetail?.year?.toIntOrNull(),
                mediaType = if (isTv) "tv" else "movie",
                seasonInfo = if (pendingDownloadSeason > 0) pendingDownloadSeason else null,
                episodeInfo = if (pendingDownloadEpisode > 0) pendingDownloadEpisode else null,
                viewModel = movieBoxViewModel,
                onDismissRequest = { showMovieBoxSheet = false },
                onTryOtherMethod = { showMovieBoxSheet = false },
                onDownloadClick = { url, quality, s, ep, still ->
                    viewModel.requestDownload(
                        mediaId = subjectId,
                        title = displayTitle,
                        posterPath = displayPoster,
                        stillPath = still,
                        mediaType = if (isTv) "tv" else "movie",
                        season = if (isTv) s else 0,
                        episode = if (isTv) ep else 0,
                        quality = quality,
                        customUrl = url
                    )
                    showMovieBoxSheet = false
                }
            )
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
                                Toast.makeText(appContext, "رابط الترجمة: ${sub.url}", Toast.LENGTH_LONG).show()
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieBoxEpisodeRowCard(
    episodeNumber: Int,
    videoFile: VideoFile,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {}
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = episodeNumber.toString(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMonoFontFamily
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "الحلقة $episodeNumber",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            val sizeText = videoFile.formattedSize()
            if (sizeText.isNotEmpty()) {
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        textDirection = TextDirection.Ltr
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Button(
            onClick = onClick,
            modifier = Modifier.height(36.dp).width(72.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(imageVector = Icons.Default.ArrowCircleDown, contentDescription = "تحميل حلقة", modifier = Modifier.size(18.dp))
        }
    }
}
