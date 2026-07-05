package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.SubtitleBatchGroup
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
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = batchGroup.releaseName,
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
                        FilledTonalButton(onClick = onExportAll) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تصدير الكل")
                        }
                        FilledTonalButton(
                            onClick = onDeleteAll,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("حذف الكل")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(batchGroup.items.withIndex().toList()) { (index, item) ->
                        SubtitleDownloadEpisodeRow(
                            item = item,
                            onExport = { onExportOne(index) },
                            onDelete = { onDeleteOne(index) },
                            onClick = { onClickOne?.invoke(index) }
                        )
                    }
                }
            }
        }
    }
}
