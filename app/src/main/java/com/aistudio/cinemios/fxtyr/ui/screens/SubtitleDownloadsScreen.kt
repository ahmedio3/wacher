package com.aistudio.cinemios.fxtyr.ui.screens

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.data.local.SubtitleDownloadEntity
import com.aistudio.cinemios.fxtyr.ui.components.DownloadedSubtitleBrowser
import com.aistudio.cinemios.fxtyr.ui.components.IosIcons
import com.aistudio.cinemios.fxtyr.ui.components.SubtitleDownloadViewType
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import java.io.File
import java.io.FileInputStream

@Composable
fun SubtitleDownloadsScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit
) {
    val subtitleBatchGroups by viewModel.subtitleBatchGroups.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val totalFiles = remember(subtitleBatchGroups) {
        subtitleBatchGroups.sumOf { it.items.size }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // iOS Custom Top Bar with Chevron
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = IosIcons.ChevronRight,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الترجمات المحملة",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (totalFiles > 0) {
                        Text(
                            text = "$totalFiles ملف ترجمة محفوظ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                DownloadedSubtitleBrowser(
                    batchGroups = subtitleBatchGroups,
                    viewType = SubtitleDownloadViewType.DEFAULT,
                    onExportFile = { item ->
                        exportSubtitleToDownloads(context, item)
                    },
                    onDeleteFile = { item ->
                        viewModel.deleteSubtitleDownload(item.id)
                    }
                )
            }
        }
    }
}

/**
 * Exports a subtitle file to the public Downloads/Watcher Subtitles/ folder.
 * Works on Android 10+ via MediaStore API.
 */
private fun exportSubtitleToDownloads(context: android.content.Context, item: SubtitleDownloadEntity) {
    try {
        val sourceFile = File(item.localFilePath)
        if (!sourceFile.exists()) {
            Toast.makeText(context, "ملف الترجمة غير موجود", Toast.LENGTH_SHORT).show()
            return
        }

        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "${item.title}_${item.language}.srt")
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Watcher Subtitles")
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: run {
                // Fallback: try using MediaStore.Files
                val fallbackValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME, "${item.title}_${item.language}.srt")
                    put(android.provider.MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream")
                    put(android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Watcher Subtitles")
                    put(android.provider.MediaStore.Files.FileColumns.IS_PENDING, 1)
                }
                resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, fallbackValues)
            }

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(outputStream)
                }
            }
            contentValues.clear()
            contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            Toast.makeText(context, "تم التصدير إلى Downloads/Watcher Subtitles", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "فشل إنشاء الملف في المجلد العام", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "فشل التصدير: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
