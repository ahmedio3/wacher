package com.aistudio.cinemios.fxtyr.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteMutedRed
import com.aistudio.cinemios.fxtyr.ui.viewmodel.SubtitleBatchGroup
import java.text.SimpleDateFormat
import java.util.*

/**
 * Bottom sheet displaying details of a downloaded batch group —
 * all the subtitle files that were downloaded together from one source.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleBatchSheet(
    batchGroup: SubtitleBatchGroup?,
    onDismiss: () -> Unit,
    onExportAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onExportOne: (Int) -> Unit,
    onDeleteOne: (Int) -> Unit,
    onClickOne: ((Int) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (batchGroup != null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = batchGroup.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        val df = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                        Text(
                            text = "${batchGroup.items.size} ترجمة • ${df.format(Date(batchGroup.items.firstOrNull()?.downloadedAt ?: 0L))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onExportAll, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = IosIcons.Download,
                                contentDescription = "تصدير الكل",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onDeleteAll, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = AppIcons.Trash,
                                contentDescription = "حذف الكل",
                                tint = PaletteMutedRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(batchGroup.items.withIndex().toList()) { (index, item) ->
                        SubtitleDownloadEpisodeRow(
                            item = item,
                            onExport = { onExportOne(index) },
                            onDelete = { onDeleteOne(index) },
                            onClick = if (onClickOne != null) { { onClickOne(index) } } else null
                        )
                    }
                }
            }
        }
    }
}
