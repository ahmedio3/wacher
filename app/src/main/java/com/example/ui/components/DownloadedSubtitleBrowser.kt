package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.example.ui.components.bouncyOverscroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.SubtitleDownloadEntity
import com.example.ui.viewmodel.SubtitleBatchGroup

/**
 * Browser that shows all downloaded subtitles grouped by batch.
 * Allows expanding/collapsing batch groups, setting a subtitle file as active,
 * and exporting/deleting individual or all items in a group.
 */
@Composable
fun DownloadedSubtitleBrowser(
    batchGroups: List<SubtitleBatchGroup>,
    viewType: SubtitleDownloadViewType = SubtitleDownloadViewType.DEFAULT,
    onExportFile: (SubtitleDownloadEntity) -> Unit,
    onDeleteFile: (SubtitleDownloadEntity) -> Unit,
    onSelectActive: ((SubtitleDownloadEntity) -> Unit)? = null,
    activeFilePath: String? = null
) {
    if (batchGroups.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "لا توجد ترجمات محملة بعد",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "قم بتحميل ترجمات من شاشة التفاصيل أو المشغل",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var expandedBatchId by remember { mutableStateOf<String?>(null) }
    var showBatchSheet by remember { mutableStateOf<SubtitleBatchGroup?>(null) }

    LazyColumn(
        modifier = Modifier.bouncyOverscroll().fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(batchGroups, key = { it.batchId }) { batchGroup ->
            val isActive = batchGroup.items.any { it.localFilePath == activeFilePath }

            SubtitleBatchCard(
                batchGroup = batchGroup,
                expanded = expandedBatchId == batchGroup.batchId,
                onToggle = {
                    expandedBatchId = if (expandedBatchId == batchGroup.batchId) null else batchGroup.batchId
                },
                onExportAll = {
                    batchGroup.items.forEach { onExportFile(it) }
                },
                onDeleteAll = {
                    batchGroup.items.forEach { onDeleteFile(it) }
                },
                onExportOne = { index ->
                    onExportFile(batchGroup.items[index])
                },
                onDeleteOne = { index ->
                    onDeleteFile(batchGroup.items[index])
                },
                onClickOne = if (onSelectActive != null) { index ->
                    onSelectActive(batchGroup.items[index])
                } else null
            )
        }
    }

    // Detail bottom sheet
    SubtitleBatchSheet(
        batchGroup = showBatchSheet,
        onDismiss = { showBatchSheet = null },
        onExportAll = {
            showBatchSheet?.items?.forEach { onExportFile(it) }
        },
        onDeleteAll = {
            showBatchSheet?.items?.forEach { onDeleteFile(it) }
        },
        onExportOne = { index ->
            showBatchSheet?.items?.let { onExportFile(it[index]) }
        },
        onDeleteOne = { index ->
            showBatchSheet?.items?.let { onDeleteFile(it[index]) }
        },
        onClickOne = if (onSelectActive != null) { index ->
            showBatchSheet?.items?.let { onSelectActive(it[index]) }
        } else null
    )
}

enum class SubtitleDownloadViewType {
    DEFAULT,
    PLAYER_SELECTION
}
