package com.aistudio.cinemios.fxtyr.ui.components.player

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons
import com.aistudio.cinemios.fxtyr.ui.viewmodel.SubtitleLine
import kotlinx.coroutines.delay

data class PlayerEpisodeItem(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val overview: String = "",
    val stillPath: String? = null,
    val localFilePath: String = "",
    val streamUrl: String = "",
    val isDownloaded: Boolean = false,
    val hasSubtitle: Boolean = false
)

@Composable
fun PortraitPlayerBottomContent(
    activeId: String,
    activeTitle: String,
    isTv: Boolean,
    currentSeason: Int,
    currentEpisode: Int,
    isStream: Boolean,
    streamResolution: String,
    episodes: List<PlayerEpisodeItem>,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (PlayerEpisodeItem) -> Unit,
    // Subtitles
    parsedSubtitles: List<SubtitleLine>,
    subtitleStatusText: String,
    subtitleTimeOffsetMs: Long,
    onSubtitleTimeOffsetChange: (Long) -> Unit,
    subtitleSize: Float,
    onSubtitleSizeChange: (Float) -> Unit,
    isSubtitleHidden: Boolean,
    onToggleSubtitleHidden: () -> Unit,
    onOpenSubtitleSourceSheet: () -> Unit,
    // Action callbacks
    onMinimizePiP: () -> Unit,
    playbackSpeed: Float,
    onCycleSpeed: () -> Unit,
    onDownloadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Tab state: 0 = "الحلقات", 1 = "الترجمة" (Default to Episodes for TV, Subtitles for movie)
    var selectedTab by remember(isTv) { mutableIntStateOf(if (isTv) 0 else 1) }

    val episodesListState = rememberLazyListState()

    // Auto-scroll to currently playing episode
    val seasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.season == selectedSeason }.sortedBy { it.episode }
    }

    LaunchedEffect(activeId, selectedSeason, seasonEpisodes.size) {
        val currentIdx = seasonEpisodes.indexOfFirst {
            it.id == activeId || (it.episode == currentEpisode && it.season == currentSeason)
        }
        if (currentIdx >= 0) {
            delay(120)
            episodesListState.animateScrollToItem(currentIdx)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. HEADER: Title & Status Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = activeTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isTv) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "الموسم $currentSeason • الحلقة $currentEpisode",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isStream) (if (streamResolution.isNotEmpty()) streamResolution else "بث مباشر") else "ملف محلي",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (parsedSubtitles.isNotEmpty() && !isSubtitleHidden) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "الترجمة مفعلة",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 2. ACTION ROW (iOS-style pill buttons: Minimize / PiP, Speed, Download)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionPillButton(
                icon = Icons.Default.PictureInPictureAlt,
                label = "تصغير (PiP)",
                onClick = onMinimizePiP,
                modifier = Modifier.weight(1f)
            )

            ActionPillButton(
                icon = Icons.Default.Speed,
                label = "${playbackSpeed}x",
                onClick = onCycleSpeed,
                modifier = Modifier.weight(1f)
            )

            if (onDownloadClick != null) {
                ActionPillButton(
                    icon = Icons.Default.Download,
                    label = "تنزيل",
                    onClick = onDownloadClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. SEGMENTED FLOATING CAPSULE TABS ("الحلقات" vs "الترجمة")
        val availableSeasons = remember(episodes) {
            episodes.map { it.season }.distinct().sorted()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                val animatedTabOffset by animateFloatAsState(
                    targetValue = selectedTab.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "capsule_slide"
                )

                BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                    val halfWidth = maxWidth / 2

                    // Sliding active pill
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (halfWidth.toPx() * animatedTabOffset).toInt(),
                                    y = 0
                                )
                            }
                            .width(halfWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    )

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Tab 0: الحلقات
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { selectedTab = 0 }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Library,
                                    contentDescription = null,
                                    tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isTv && episodes.isNotEmpty()) "الحلقات (${episodes.size})" else "الحلقات",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Tab 1: الترجمة
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { selectedTab = 1 }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "الترجمة",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. CONTENT AREA (Tab 0: Episodes / Tab 1: Subtitles)
        if (selectedTab == 0) {
            // ===== TAB 0: EPISODES =====
            if (isTv) {
                // Season Chips (if multiple seasons)
                if (availableSeasons.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableSeasons) { seasonNum ->
                            val isSelected = seasonNum == selectedSeason
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { onSelectSeason(seasonNum) }
                            ) {
                                Text(
                                    text = "الموسم $seasonNum",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (seasonEpisodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "جاري تحميل الحلقات...",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = episodesListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(seasonEpisodes, key = { it.id }) { ep ->
                            val isPlayingThis = ep.id == activeId || (ep.season == currentSeason && ep.episode == currentEpisode)

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isPlayingThis) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                border = if (isPlayingThis) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectEpisode(ep) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Episode Number badge / play state
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPlayingThis) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isPlayingThis) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "${ep.episode}",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = ep.title.ifEmpty { "الحلقة ${ep.episode}" },
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (isPlayingThis) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Text(
                                                        text = "جاري التشغيل",
                                                        fontSize = 10.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (ep.overview.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = ep.overview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (ep.isDownloaded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.DownloadDone,
                                            contentDescription = "محمل محلياً",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Movie Info View
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("فيلم سينمائي", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "يمكنك التحكم في الترجمة وتأخيرها أو تصغير الشاشة من الأزرار أعلاه.",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            // ===== TAB 1: SUBTITLES =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status & Toggle Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "حالة الترجمة",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (parsedSubtitles.isNotEmpty()) subtitleStatusText else "لا توجد ملفات ترجمة مدمجة حالياً",
                                fontSize = 12.sp,
                                color = if (parsedSubtitles.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }

                        if (parsedSubtitles.isNotEmpty()) {
                            Button(
                                onClick = onToggleSubtitleHidden,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubtitleHidden) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                                ),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isSubtitleHidden) "إظهار" else "إخفاء",
                                    fontSize = 12.sp,
                                    color = if (isSubtitleHidden) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                                )
                            }
                        }
                    }
                }

                // Subtitle Delay Offset Row
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مزامنة توقيت الترجمة",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${subtitleTimeOffsetMs / 1000f} ثانية",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSubtitleTimeOffsetChange(subtitleTimeOffsetMs - 500L) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("-0.5s", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onSubtitleTimeOffsetChange(subtitleTimeOffsetMs - 100L) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("-0.1s", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { onSubtitleTimeOffsetChange(0L) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("إعادة ضبط", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onSubtitleTimeOffsetChange(subtitleTimeOffsetMs + 100L) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("+0.1s", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onSubtitleTimeOffsetChange(subtitleTimeOffsetMs + 500L) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("+0.5s", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Subtitle Size Row
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("حجم خط الترجمة", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { onSubtitleSizeChange((subtitleSize - 2f).coerceAtLeast(14f)) }
                            ) {
                                Text("A-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text("${subtitleSize.toInt()} sp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(
                                onClick = { onSubtitleSizeChange((subtitleSize + 2f).coerceAtMost(32f)) }
                            ) {
                                Text("A+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }

                // Search Online Subtitles Button
                Button(
                    onClick = onOpenSubtitleSourceSheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("البحث عن ترجمات إضافية (SubDL / OpenSubtitles)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
