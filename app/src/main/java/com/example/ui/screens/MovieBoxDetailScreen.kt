package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
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
import com.example.data.remote.moviebox.models.VideoFile
import com.example.ui.viewmodel.MovieViewModel
import kotlinx.coroutines.launch
import android.widget.Toast

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
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var videoLinks by remember { mutableStateOf<List<VideoFile>>(emptyList()) }
    
    val displayTitle = titleParams.takeIf { it.isNotBlank() } ?: "بدون عنوان"
    val displayPoster = posterParams
    val scrollState = rememberScrollState()

    LaunchedEffect(subjectId) {
        isLoading = true
        coroutineScope.launch {
            val res = viewModel.movieBoxRepository.getDownloadLinks(subjectId, null)
            if (res.isSuccess) {
                videoLinks = res.getOrNull() ?: emptyList()
            } else {
                errorMessage = "خطأ في تحميل الحلقات أو الفيلم. (${res.exceptionOrNull()?.message})"
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { }, // Hide title initially behind the backdrop, it will be displayed below the poster
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Double layered poster like DetailScreen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
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

                    // Centered Overlay Poster
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
                
                // Title
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // State handling
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (errorMessage != null) {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedButton(onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(errorMessage!!))
                            Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("نسخ الخطأ")
                        }
                    }
                } else if (videoLinks.isEmpty()) {
                     Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد روابط تحميل متاحة.", color = MaterialTheme.colorScheme.onBackground)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        if (mediaType == "tv") {
                            val availableSeasons = videoLinks.map { it.season }.distinct().sorted()
                            var selectedSeason by remember { mutableIntStateOf(availableSeasons.firstOrNull() ?: 1) }

                            val seasonLinks = videoLinks.filter { it.season == selectedSeason }
                            val availableQualities = seasonLinks.map { it.resolution }.distinct().sortedDescending()
                            var selectedQuality by remember { mutableIntStateOf(availableQualities.firstOrNull() ?: 1080) }
                            
                            LaunchedEffect(selectedSeason) {
                                if (selectedQuality !in seasonLinks.map { it.resolution }) {
                                    selectedQuality = availableQualities.firstOrNull() ?: 1080
                                }
                            }

                            if (availableSeasons.size > 1) {
                                Text("المواسم:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(availableSeasons) { s ->
                                        val isSelected = s == selectedSeason
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { selectedSeason = s }
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
                            
                            if (availableQualities.isNotEmpty()) {
                                Text("الجودة المتاحة:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(availableQualities) { q ->
                                        val isSelected = q == selectedQuality
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { selectedQuality = q }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "${q}p",
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            val episodesMap = seasonLinks.filter { it.resolution == selectedQuality }.groupBy { it.episode }.toSortedMap(compareBy { it })
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                episodesMap.forEach { (episodeId, files) ->
                                    val videoFile = files.firstOrNull()
                                    if (videoFile != null) {
                                        val mbSize = if (videoFile.size > 0) {
                                            if (videoFile.size >= 1_000_000_000) String.format("%.1f GB", videoFile.size / 1_000_000_000.0) 
                                            else String.format("%d MB", videoFile.size / 1_000_000)
                                        } else "غير متوفر"
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("الحلقة $episodeId", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Text(mbSize, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                            
                                            Button(
                                                onClick = {
                                                    val tmdbIdFake = subjectId
                                                    viewModel.requestDownload(
                                                        mediaId = "${tmdbIdFake}-s${selectedSeason}-e${episodeId}",
                                                        title = displayTitle,
                                                        posterPath = displayPoster,
                                                        mediaType = "tv",
                                                        season = selectedSeason,
                                                        episode = episodeId,
                                                        quality = "${videoFile.resolution}p",
                                                        customUrl = videoFile.url
                                                    )
                                                    Toast.makeText(context, "بدأ التحميل", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = "تحميل", modifier = Modifier.padding(end = 8.dp))
                                                Text("تحميل الحلقة")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Movie
                            val availableQualities = videoLinks.distinctBy { it.resolution }.sortedByDescending { it.resolution }
                            
                            Text("اختر الجودة المتاحة للتحميل:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                availableQualities.forEach { videoFile ->
                                    val mbSize = if (videoFile.size > 0) {
                                        if (videoFile.size >= 1_000_000_000) String.format("%.1f GB", videoFile.size / 1_000_000_000.0)
                                        else String.format("%d MB", videoFile.size / 1_000_000)
                                    } else "غير متوفر"
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "دقة ${videoFile.resolution}p",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(text = mbSize, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))

                                        Button(
                                            onClick = {
                                                viewModel.requestDownload(
                                                    mediaId = subjectId,
                                                    title = displayTitle,
                                                    posterPath = displayPoster,
                                                    mediaType = "movie",
                                                    season = 0,
                                                    episode = 0,
                                                    quality = "${videoFile.resolution}p",
                                                    customUrl = videoFile.url
                                                )
                                                Toast.makeText(context, "بدأ التحميل", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "تحميل", modifier = Modifier.padding(end = 8.dp))
                                            Text("تحميل الفيلم")
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

