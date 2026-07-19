package com.example.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SubtitleDownloadEntity
import com.example.ui.viewmodel.SubtitleHelper
import com.example.ui.viewmodel.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SearchStep {
    CHECKING_AUTO,
    CHECKING_DOWNLOADS,
    FETCHING_MOVIEBOX,
    FOUND,
    NOT_FOUND
}

@Composable
fun SubtitleAutoSearchSheet(
    visible: Boolean,
    activeId: String,
    isTv: Boolean,
    tmdbId: String,
    season: Int,
    episode: Int,
    title: String,
    context: Context = LocalContext.current,
    subtitleDownloads: List<SubtitleDownloadEntity>,
    onSubtitleFound: (File) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    var currentStep by remember { mutableStateOf(SearchStep.CHECKING_AUTO) }
    var movieBoxError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun startSearch() {
        coroutineScope.launch {
            currentStep = SearchStep.CHECKING_AUTO
            delay(400)

            val standaloneDir = File(context.filesDir, "standalone_subtitles")
            val autoFiles = standaloneDir.listFiles()?.filter {
                it.name.startsWith(activeId) && (it.name.endsWith(".srt") || it.name.endsWith(".vtt"))
            }
            val autoSub = autoFiles?.firstOrNull()

            if (autoSub != null && autoSub.exists()) {
                currentStep = SearchStep.FOUND
                delay(600)
                val playerExt = if (autoSub.name.endsWith(".vtt")) ".vtt" else ".srt"
                val playerFile = File(context.filesDir, "downloads/$activeId$playerExt")
                playerFile.parentFile?.mkdirs()
                autoSub.copyTo(playerFile, overwrite = true)
                val parsed = SubtitleParser.parseBlock(autoSub)
                if (parsed.isNotEmpty()) {
                    onSubtitleFound(autoSub)
                }
                return@launch
            }

            currentStep = SearchStep.CHECKING_DOWNLOADS
            delay(400)

            val matchId = if (isTv) "${tmdbId}_s${season}e${episode}_ar" else "${tmdbId}_ar"
            val found = subtitleDownloads.firstOrNull { it.id.startsWith(matchId) }
            if (found != null) {
                val file = File(found.localFilePath)
                if (file.exists()) {
                    currentStep = SearchStep.FOUND
                    delay(600)
                    val parsed = SubtitleParser.parseBlock(file)
                    if (parsed.isNotEmpty()) {
                        val playerExt = if (file.name.endsWith(".vtt")) ".vtt" else ".srt"
                        val playerFile = File(context.filesDir, "downloads/$activeId$playerExt")
                        playerFile.parentFile?.mkdirs()
                        file.copyTo(playerFile, overwrite = true)
                        onSubtitleFound(file)
                    }
                    return@launch
                }
            }

            currentStep = SearchStep.FETCHING_MOVIEBOX
            delay(400)

            try {
                val subs = withContext(Dispatchers.IO) {
                    SubtitleHelper.fetchSubtitles(
                        tmdbId, isTv, season, episode, title
                    )
                }
                val arSub = subs.firstOrNull { it.lang.contains("AR", ignoreCase = true) }
                    ?: subs.firstOrNull()
                if (arSub != null) {
                    val extracted = withContext(Dispatchers.IO) {
                        SubtitleHelper.downloadAndExtractSubtitle(context, arSub.url, activeId)
                    }
                    if (extracted != null) {
                        currentStep = SearchStep.FOUND
                        delay(600)
                        val parsed = SubtitleParser.parseBlock(extracted)
                        if (parsed.isNotEmpty()) {
                            onSubtitleFound(extracted)
                        }
                        return@launch
                    }
                }
            } catch (_: Exception) {
                movieBoxError = true
            }

            currentStep = SearchStep.NOT_FOUND
            delay(2000)
            onDismiss()
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            movieBoxError = false
            startSearch()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { }
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "البحث عن ترجمة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(20.dp))

                SearchStepRow(
                    icon = Icons.Default.DownloadDone,
                    label = "الترجمة المحملة تلقائياً",
                    step = currentStep,
                    target = SearchStep.CHECKING_AUTO
                )

                Spacer(modifier = Modifier.height(12.dp))

                SearchStepRow(
                    icon = Icons.Default.Folder,
                    label = "الترجمات المحملة",
                    step = currentStep,
                    target = SearchStep.CHECKING_DOWNLOADS
                )

                Spacer(modifier = Modifier.height(12.dp))

                SearchStepRow(
                    icon = Icons.Default.CloudDownload,
                    label = "البحث في MovieBox",
                    step = currentStep,
                    target = SearchStep.FETCHING_MOVIEBOX,
                    isError = movieBoxError
                )

                Spacer(modifier = Modifier.height(4.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn(animationSpec = tween(300)) with fadeOut(animationSpec = tween(200)) },
                    label = "status"
                ) { step ->
                    when (step) {
                        SearchStep.FOUND -> {
                            Text(
                                "تم العثور على الترجمة ✓",
                                color = Color(0xFF4CAF50),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        SearchStep.NOT_FOUND -> {
                            Text(
                                if (movieBoxError) "تعذر الاتصال بخادم MovieBox"
                                else "لم نتمكن من العثور على ترجمة",
                                color = Color(0xFFEF5350),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        else -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .size(24.dp),
                                color = Color(0xFF90CAF9),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchStepRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    step: SearchStep,
    target: SearchStep,
    isError: Boolean = false
) {
    val isActive = step == target
    val isDone = when (target) {
        SearchStep.CHECKING_AUTO -> step.ordinal > SearchStep.CHECKING_AUTO.ordinal
        SearchStep.CHECKING_DOWNLOADS -> step.ordinal > SearchStep.CHECKING_DOWNLOADS.ordinal
        SearchStep.FETCHING_MOVIEBOX -> step == SearchStep.FOUND || step == SearchStep.NOT_FOUND
        else -> false
    }

    val bgColor = when {
        isError && isActive -> Color(0xFFD32F2F).copy(alpha = 0.2f)
        isActive -> Color(0xFF1565C0).copy(alpha = 0.2f)
        isDone -> Color(0xFF2E7D32).copy(alpha = 0.15f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    val iconColor = when {
        isError && isActive -> Color(0xFFEF5350)
        isActive -> Color(0xFF64B5F6)
        isDone -> Color(0xFF4CAF50)
        else -> Color.White.copy(alpha = 0.3f)
    }

    val textColor = when {
        isError && isActive -> Color(0xFFEF5350)
        isActive -> Color.White
        isDone -> Color.White.copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.3f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isDone) Icons.Default.CheckCircle else icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (isActive && !isDone && step != SearchStep.FOUND && step != SearchStep.NOT_FOUND) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = iconColor,
                strokeWidth = 2.dp
            )
        } else if (isDone && !(isActive && isError)) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
