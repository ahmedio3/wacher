package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun shimmerBrush(
    showShimmer: Boolean = true,
    targetValue: Float = 1000f
): Brush {
    val baseColor = MaterialTheme.colorScheme.surface
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant
    return if (showShimmer) {
        val shimmerColors = listOf(
            baseColor,
            shimmerColor,
            baseColor
        )

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_anim"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

@Composable
fun SkeletonItem(
    width: Dp,
    height: Dp,
    cornerRadius: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .background(shimmerBrush(), shape = RoundedCornerShape(cornerRadius))
    )
}

@Composable
fun SkeletonCardGridLoading(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        SkeletonItem(width = 140.dp, height = 24.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                Column {
                    SkeletonItem(width = 110.dp, height = 160.dp, cornerRadius = 16.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonItem(width = 90.dp, height = 16.dp)
                }
            }
        }
    }
}
