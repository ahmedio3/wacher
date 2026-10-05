package com.aistudio.cinemios.fxtyr.ui.screens

import androidx.compose.runtime.Composable
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel

/**
 * Legacy PlayerScreen - Deprecated in favor of unified OfflinePlayerScreen
 * which supports both offline files and online streaming with YouTube-style
 * portrait mode and sensor landscape mode.
 */
@Composable
fun PlayerScreen(
    mediaId: String,
    title: String,
    localFilePath: String,
    viewModel: MovieViewModel,
    onBack: () -> Unit
) {
    OfflinePlayerScreen(
        mediaId = mediaId,
        title = title,
        localFilePath = localFilePath,
        viewModel = viewModel,
        onBack = onBack
    )
}
