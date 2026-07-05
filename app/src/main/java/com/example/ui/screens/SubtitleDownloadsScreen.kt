package com.example.ui.screens

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SubtitleDownloadEntity
import com.example.ui.components.DownloadedSubtitleBrowser
import com.example.ui.components.SubtitleDownloadViewType
import com.example.ui.viewmodel.MovieViewModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleDownloadsScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit
) {
    val subtitleBatchGroups by viewModel.subtitleBatchGroups.collectAsState(initial = emptyList())
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الترجمات المحملة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
