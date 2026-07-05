package com.example.ui.components

import androidx.compose.foundation.layout.*
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

@Composable
fun SubtitleBatchCard(
    batchGroup: SubtitleBatchGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onExportAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onExportOne: (Int) -> Unit,
    onDeleteOne: (Int) -> Unit,
    onClickOne: ((Int) -> Unit)? = null
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = batchGroup.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${batchGroup.items.size} ترجمة • بتاريخ ${formattedDate(batchGroup.items.first().downloadedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onExportAll, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Download, contentDescription = "تصدير الكل", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDeleteAll, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف الكل", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "طي" else "توسيع",
                        modifier = Modifier.size(24.dp).padding(start = 4.dp)
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                batchGroup.items.forEachIndexed { index, item ->
                    SubtitleDownloadEpisodeRow(
                        item = item,
                        onExport = { onExportOne(index) },
                        onDelete = { onDeleteOne(index) },
                        onClick = { onClickOne?.invoke(index) }
                    )
                    if (index < batchGroup.items.lastIndex) {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

private fun formattedDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
