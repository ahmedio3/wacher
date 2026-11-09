package com.aistudio.cinemios.fxtyr.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteMutedRed
import com.aistudio.cinemios.fxtyr.ui.viewmodel.SubtitleBatchGroup
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SubtitleBatchCard(
    batchGroup: SubtitleBatchGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onExportAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onExportOne: (Int) -> Unit,
    onDeleteOne: (Int) -> Unit,
    onClickOne: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "chevron_rotate"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Group Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Leading ClosedCaptions icon badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IosIcons.ClosedCaptions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Batch info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = batchGroup.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${batchGroup.items.size} ترجمة • ${formattedDate(batchGroup.items.first().downloadedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f)
                    )
                }

                // Header actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onExportAll,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = IosIcons.Download,
                            contentDescription = "تصدير الكل",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { showDeleteAllConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Trash,
                            contentDescription = "حذف الكل",
                            tint = PaletteMutedRed.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Rotating chevron indicator
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(chevronRotation),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IosIcons.ChevronDown,
                            contentDescription = if (expanded) "طي" else "توسيع",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expanded Episodes List (Flat rows with dividers, no nested cards!)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        thickness = 0.8.dp
                    )

                    batchGroup.items.forEachIndexed { index, item ->
                        SubtitleDownloadEpisodeRow(
                            item = item,
                            onExport = { onExportOne(index) },
                            onDelete = { onDeleteOne(index) },
                            onClick = if (onClickOne != null) { { onClickOne(index) } } else null
                        )
                        if (index < batchGroup.items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 58.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                thickness = 0.6.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "حذف جميع الترجمات",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("هل أنت متأكد من حذف ${batchGroup.items.size} ملف ترجمة من هذه المجموعة؟")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAll()
                        showDeleteAllConfirm = false
                    }
                ) {
                    Text("حذف الكل", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

private fun formattedDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
