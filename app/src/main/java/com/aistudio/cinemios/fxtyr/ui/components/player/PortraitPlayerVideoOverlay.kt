package com.aistudio.cinemios.fxtyr.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PortraitPlayerVideoOverlay(
    showControls: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    onExpandToLandscape: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = showControls || isLoading,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            // TOP BAR: Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // CENTER: Big Play / Pause button OR Loading spinner
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // BOTTOM BAR: Seekbar + Time + Expand to Landscape button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .align(Alignment.BottomCenter)
            ) {
                // Seekbar slider (slim, matching landscape mode)
                var isDragging by remember { mutableStateOf(false) }
                var dragFraction by remember { mutableFloatStateOf(0f) }

                val progress = if (isDragging) dragFraction else if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                val trackColor = MaterialTheme.colorScheme.primary
                val inactiveColor = Color.White.copy(alpha = 0.35f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .pointerInput(totalDuration) {
                            detectTapGestures { offset ->
                                if (totalDuration > 0 && size.width > 0) {
                                    val percent = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    onSeek((percent * totalDuration).toLong())
                                }
                            }
                        }
                        .pointerInput(totalDuration) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    if (size.width > 0) {
                                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (size.width > 0) {
                                        dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    isDragging = false
                                    if (totalDuration > 0) {
                                        onSeek((dragFraction * totalDuration).toLong())
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val trackHeight = 3.dp.toPx()
                        val thumbRadius = 5.dp.toPx()
                        val centerY = size.height / 2

                        // Inactive track
                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2),
                            size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
                        )
                        // Active track
                        val activeWidth = size.width * progress
                        if (activeWidth > 0f) {
                            drawRoundRect(
                                color = trackColor,
                                topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2),
                                size = androidx.compose.ui.geometry.Size(activeWidth, trackHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
                            )
                        }
                        // Thumb dot
                        val thumbX = activeWidth.coerceIn(thumbRadius, size.width - thumbRadius)
                        drawCircle(
                            color = trackColor,
                            radius = thumbRadius,
                            center = androidx.compose.ui.geometry.Offset(thumbX, centerY)
                        )
                    }

                    // Time preview on drag
                    if (isDragging && totalDuration > 0) {
                        val previewPos = (dragFraction * totalDuration).toLong()
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-10).dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = formatTime(previewPos),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Time Text (00:00 / 00:00)
                    Text(
                        text = "${formatTime(currentPosition)} / ${formatTime(totalDuration)}",
                        color = Color.White,
                        fontSize = 11.sp
                    )

                    // Expand to Landscape Fullscreen Button
                    IconButton(
                        onClick = onExpandToLandscape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "ملء الشاشة (وضع أفقي)",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
