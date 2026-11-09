package com.aistudio.cinemios.fxtyr.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.data.local.SubtitleDownloadEntity
import com.aistudio.cinemios.fxtyr.ui.viewmodel.SubtitleBatchGroup

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
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IosIcons.ClosedCaptions,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "لا توجد ترجمات محملة بعد",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "قم بتحميل الترجمات لأي فيلم أو حلقة من شاشة التفاصيل أو مشغل الفيديو لتظهر هنا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
        return
    }

    var expandedBatchId by remember { mutableStateOf<String?>(null) }
    var showBatchSheet by remember { mutableStateOf<SubtitleBatchGroup?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
