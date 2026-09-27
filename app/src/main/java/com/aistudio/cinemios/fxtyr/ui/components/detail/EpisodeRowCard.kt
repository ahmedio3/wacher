package com.aistudio.cinemios.fxtyr.ui.components.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.data.remote.TmdbEpisode
import com.aistudio.cinemios.fxtyr.ui.theme.JetBrainsMonoFontFamily
import com.aistudio.cinemios.fxtyr.utils.isLatinText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRowCard(
    episode: TmdbEpisode,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDownloadSubtitle: () -> Unit = {}
) {
    val backdropUrl = "https://image.tmdb.org/t/p/w300${episode.stillPath}"

    // Custom press effect (replaces default ripple) — subtle scale + alpha on touch-down
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.91f else 1f, animationSpec = tween(150))
    val pressScale by animateFloatAsState(if (pressed) 0.92f else 1f, animationSpec = tween(150))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .alpha(pressAlpha)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
                onLongClick = onDownloadSubtitle
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Image
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            if (!episode.stillPath.isNullOrEmpty()) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Details Block
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "الحلقة ${episode.episodeNumber}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = episode.name ?: "بدون عنوان",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (isLatinText(episode.name ?: "")) JetBrainsMonoFontFamily else null,
                    textDirection = if (isLatinText(episode.name ?: "")) TextDirection.Ltr else TextDirection.Unspecified
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Quick action button for download (subtitle moved to long-press)
        Button(
            onClick = onDownload,
            modifier = Modifier
                .height(36.dp)
                .width(72.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(imageVector = Icons.Default.ArrowCircleDown, contentDescription = "تحميل حلقة أوفلاين", modifier = Modifier.size(18.dp))
        }
    }
}
