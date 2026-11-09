package com.aistudio.cinemios.fxtyr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.data.local.SubtitleDownloadEntity
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteMutedRed

@Composable
fun SubtitleDownloadEpisodeRow(
    item: SubtitleDownloadEntity,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val rowModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Episode badge
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (item.episode > 0) "E${item.episode}" else "—",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.language.ifBlank { "الترجمة" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.episode > 0 || item.season > 0) {
                Text(
                    text = "الموسم ${item.season} • الحلقة ${item.episode}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }

        // Export button (iOS Share icon)
        IconButton(
            onClick = onExport,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = IosIcons.Share,
                contentDescription = "تصدير",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
        }

        // Delete button (iOS Trash icon)
        IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = AppIcons.Trash,
                contentDescription = "حذف",
                tint = PaletteMutedRed.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "حذف الترجمة",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("هل أنت متأكد من حذف ترجمة ${if (item.episode > 0) "الحلقة ${item.episode}" else item.language}؟")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
