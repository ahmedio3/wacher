package com.aistudio.cinemios.fxtyr.ui.components.downloads

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.data.local.LocalPlaylist
import com.aistudio.cinemios.fxtyr.data.local.LocalVideoFile
import com.aistudio.cinemios.fxtyr.data.local.UserPickedFileList
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteMutedRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFilesTab(
    context: android.content.Context,
    onNavigateToPlayer: (String, String, String) -> Unit
) {
    // Scan /storage/emulated/0/Movies/ for video files + user-picked files
    val prefs = context.getSharedPreferences("local_videos", android.content.Context.MODE_PRIVATE)
    var localFiles by remember { mutableStateOf<List<com.aistudio.cinemios.fxtyr.data.local.LocalVideoFile>>(emptyList()) }
    var selectedVideoForPlaylist by remember { mutableStateOf<String?>(null) }
    var playlists by remember { mutableStateOf<List<com.aistudio.cinemios.fxtyr.data.local.LocalPlaylist>>(emptyList()) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var currentPlaylistName by remember { mutableStateOf("") }

    // Load user-picked files from prefs
    fun loadUserPickedFiles(): List<String> {
        val json = prefs.getString("picked_files", "[]") ?: "[]"
        return try {
            com.aistudio.cinemios.fxtyr.data.local.UserPickedFileList.fromJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    fun saveUserPickedFiles(files: List<String>) {
        prefs.edit().putString("picked_files", com.aistudio.cinemios.fxtyr.data.local.UserPickedFileList.toJson(files)).apply()
    }
    fun loadPlaylists(): List<com.aistudio.cinemios.fxtyr.data.local.LocalPlaylist> {
        val json = prefs.getString("playlists", "[]") ?: "[]"
        return try {
            com.aistudio.cinemios.fxtyr.data.local.LocalPlaylistList.fromJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    fun savePlaylists(pl: List<com.aistudio.cinemios.fxtyr.data.local.LocalPlaylist>) {
        prefs.edit().putString("playlists", com.aistudio.cinemios.fxtyr.data.local.LocalPlaylistList.toJson(pl)).apply()
    }

    // Scan Movies directory on first composition
    LaunchedEffect(Unit) {
        playlists = loadPlaylists()
        val userPicked = loadUserPickedFiles()
        val files = mutableListOf<com.aistudio.cinemios.fxtyr.data.local.LocalVideoFile>()
        // Scan /storage/emulated/0/Movies/
        try {
            val moviesDir = java.io.File("/storage/emulated/0/Movies/")
            if (moviesDir.exists() && moviesDir.isDirectory) {
                val videoExts = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp")
                moviesDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.contains(".") && file.extension.lowercase() in videoExts) {
                        files.add(com.aistudio.cinemios.fxtyr.data.local.LocalVideoFile(
                            id = file.absolutePath,
                            name = file.nameWithoutExtension,
                            filePath = file.absolutePath,
                            size = file.length()
                        ))
                    }
                }
            }
        } catch (_: Exception) {}
        // Add user-picked files
        userPicked.forEach { path ->
            if (path.startsWith("content://")) {
                // content:// URI from file picker — query display name from content resolver
                val displayName = try {
                    val cursor = context.contentResolver.query(android.net.Uri.parse(path), null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) it.getString(idx) ?: "فيديو محلي" else "فيديو محلي"
                        } else "فيديو محلي"
                    } ?: "فيديو محلي"
                } catch (_: Exception) { "فيديو محلي" }
                files.add(com.aistudio.cinemios.fxtyr.data.local.LocalVideoFile(
                    id = path,
                    name = displayName,
                    filePath = path,
                    size = 0L
                ))
            } else {
                val f = java.io.File(path)
                if (f.exists()) {
                    files.add(com.aistudio.cinemios.fxtyr.data.local.LocalVideoFile(
                        id = f.absolutePath,
                        name = f.nameWithoutExtension,
                        filePath = f.absolutePath,
                        size = f.length()
                    ))
                }
            }
        }
        localFiles = files.distinctBy { it.id }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // Copy the selected file URI info
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        val name = if (nameIdx >= 0) it.getString(nameIdx) else "فيديو"
                        val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0L
                        // Save as a reference — we'll use content:// URI directly
                        val filePath = uri.toString()
                        val userPicked = loadUserPickedFiles()
                        if (filePath !in userPicked) {
                            saveUserPickedFiles(userPicked + filePath)
                            // Add to local files list
                            localFiles = localFiles + com.aistudio.cinemios.fxtyr.data.local.LocalVideoFile(
                                id = filePath,
                                name = name,
                                filePath = filePath,
                                size = size
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun removeFile(id: String) {
        localFiles = localFiles.filter { it.id != id }
        val userPicked = loadUserPickedFiles().filter { it != id }
        saveUserPickedFiles(userPicked)
        // Remove from playlists too
        var updatedPlaylists = playlists.map { pl ->
            pl.copy(videoIds = pl.videoIds.filter { it != id })
        }
        updatedPlaylists = updatedPlaylists.filter { it.videoIds.isNotEmpty() }
        savePlaylists(updatedPlaylists)
        playlists = updatedPlaylists
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header with add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "فيديوهات الجهاز (${localFiles.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            IconButton(onClick = {
                filePickerLauncher.launch(arrayOf("video/*"))
            }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة فيديو", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Playlists section
        if (playlists.isNotEmpty()) {
            Text(
                text = "قوائم التشغيل",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LazyColumn(
                modifier = Modifier.height((playlists.size * 60).dp.coerceAtMost(180.dp)),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(playlists, key = { it.id }) { pl ->
                    val playlistVideos = pl.videoIds.mapNotNull { id -> localFiles.find { it.id == id } }
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Navigate to playlist — just show videos in this playlist
                                // Show videos in playlist
                            },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(pl.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${playlistVideos.size} فيديو", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Video list
        if (localFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا توجد فيديوهات. اضغط + لإضافة فيديو",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(localFiles, key = { it.id }) { video ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigateToPlayer(video.id, video.name, video.filePath)
                            },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Thumbnail placeholder
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatBytes(video.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Add to playlist button
                            IconButton(onClick = {
                                selectedVideoForPlaylist = video.id
                                showCreatePlaylistDialog = true
                            }) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = "إضافة لقائمة تشغيل", tint = MaterialTheme.colorScheme.secondary)
                            }
                            // Remove button
                            IconButton(onClick = { removeFile(video.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "إزالة", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // Create/Add to playlist dialog
    if (showCreatePlaylistDialog && selectedVideoForPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("إضافة لقائمة تشغيل") },
            text = {
                Column {
                    Text("اختر قائمة تشغيل موجودة أو أنشئ واحدة جديدة:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentPlaylistName,
                        onValueChange = { currentPlaylistName = it },
                        label = { Text("اسم قائمة جديدة") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (playlists.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("أو اختر من القوائم الموجودة:", style = MaterialTheme.typography.labelMedium)
                        playlists.forEach { pl ->
                            TextButton(
                                onClick = {
                                    val updatedPlaylists = playlists.map {
                                        if (it.id == pl.id && selectedVideoForPlaylist !in it.videoIds) {
                                            it.copy(videoIds = it.videoIds + selectedVideoForPlaylist!!)
                                        } else it
                                    }
                                    savePlaylists(updatedPlaylists)
                                    playlists = updatedPlaylists
                                    showCreatePlaylistDialog = false
                                }
                            ) {
                                Text(pl.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (currentPlaylistName.isNotBlank()) {
                    TextButton(onClick = {
                        val newPlaylist = com.aistudio.cinemios.fxtyr.data.local.LocalPlaylist(
                            id = java.util.UUID.randomUUID().toString(),
                            name = currentPlaylistName,
                            videoIds = listOf(selectedVideoForPlaylist!!)
                        )
                        val updatedPlaylists = playlists + newPlaylist
                        savePlaylists(updatedPlaylists)
                        playlists = updatedPlaylists
                        currentPlaylistName = ""
                        showCreatePlaylistDialog = false
                    }) { Text("إنشاء") }
                }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("إلغاء") } }
        )
    }

}

