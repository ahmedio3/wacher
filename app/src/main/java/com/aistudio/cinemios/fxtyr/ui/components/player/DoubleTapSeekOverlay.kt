package com.aistudio.cinemios.fxtyr.ui.components.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

enum class SeekDirection {
    FORWARD,
    REWIND
}

/**
 * YouTube-style double-tap seek animation overlay.
 * Displays a curved translucent dome on the left or right side with flowing animated chevrons
 * and accumulating seek seconds indicator (e.g. 10 ثوانٍ, 20 ثانية, 30 ثانية).
 */
@Composable
fun DoubleTapSeekOverlay(
    direction: SeekDirection?,
    seconds: Int,
    triggerCount: Int,
    isPortrait: Boolean,
    modifier: Modifier = Modifier
) {
    val isVisible = direction != null
    val isForward = direction == SeekDirection.FORWARD

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(120)) + scaleIn(
            animationSpec = tween(150, easing = FastOutSlowInEasing),
            initialScale = 0.88f,
            transformOrigin = if (isForward) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)
        ),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier.fillMaxSize()
    ) {
        if (direction != null) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Expanding ripple progress triggered on each tap
                val rippleProgress = remember { Animatable(0f) }
                LaunchedEffect(triggerCount) {
                    if (triggerCount > 0) {
                        rippleProgress.snapTo(0f)
                        rippleProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                        )
                    }
                }

                // Half-width dome container
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.46f)
                        .align(if (isForward) Alignment.CenterEnd else Alignment.CenterStart)
                ) {
                    // Curved dome & expanding ripple background
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path()
                        if (isForward) {
                            path.moveTo(size.width, 0f)
                            path.lineTo(size.width * 0.28f, 0f)
                            path.quadraticBezierTo(
                                0f, size.height / 2f,
                                size.width * 0.28f, size.height
                            )
                            path.lineTo(size.width, size.height)
                            path.close()
                        } else {
                            path.moveTo(0f, 0f)
                            path.lineTo(size.width * 0.72f, 0f)
                            path.quadraticBezierTo(
                                size.width, size.height / 2f,
                                size.width * 0.72f, size.height
                            )
                            path.lineTo(0f, size.height)
                            path.close()
                        }

                        // Base translucent background
                        drawPath(path = path, color = Color.White.copy(alpha = 0.16f))

                        // Ripple wave clipped inside the dome
                        clipPath(path) {
                            val r = rippleProgress.value
                            if (r > 0f && r < 1f) {
                                val rippleRadius = size.width * (0.2f + 1.1f * r)
                                val rippleAlpha = (0.24f * (1f - r)).coerceIn(0f, 1f)
                                val centerX = if (isForward) size.width * 0.55f else size.width * 0.45f
                                drawCircle(
                                    color = Color.White.copy(alpha = rippleAlpha),
                                    radius = rippleRadius,
                                    center = Offset(centerX, size.height / 2f)
                                )
                            }
                        }
                    }

                    // Centered flowing arrows + accumulated seconds
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        SeekAnimatedArrows(
                            isForward = isForward,
                            isPortrait = isPortrait
                        )

                        Spacer(Modifier.height(if (isPortrait) 6.dp else 10.dp))

                        val secondsText = if (seconds <= 10) "$seconds ثوانٍ" else "$seconds ثانية"
                        Text(
                            text = secondsText,
                            color = Color.White,
                            fontSize = if (isPortrait) 13.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    offset = Offset(1.5f, 1.5f),
                                    blurRadius = 6f
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Three small flowing chevron triangles animating sequentially in the seek direction (YouTube-style).
 */
@Composable
fun SeekAnimatedArrows(
    isForward: Boolean,
    isPortrait: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SeekArrowsTransition")
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SeekArrowsWaveProgress"
    )

    val iconSize = if (isPortrait) 20.dp else 26.dp
    val spacing = if (isPortrait) (-4).dp else (-5).dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..2) {
            val step = if (isForward) i else (2 - i)
            val p = (waveProgress * 3.6f) - (step * 0.9f)
            val factor = if (p in 0f..1f) sin(p * PI.toFloat()).coerceAtLeast(0f) else 0f
            val alpha = (0.26f + 0.74f * factor).coerceIn(0f, 1f)
            val scale = 0.86f + 0.26f * factor

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = if (isForward) scale else -scale
                        scaleY = scale
                    }
            )
        }
    }
}
