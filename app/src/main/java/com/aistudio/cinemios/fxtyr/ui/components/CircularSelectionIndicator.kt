package com.aistudio.cinemios.fxtyr.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
@Composable
fun CircularSelectionIndicator(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorFill by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(200)
    )
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .border(
                2.dp,
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                CircleShape
            )
            .background(MaterialTheme.colorScheme.primary.copy(alpha = indicatorFill))
            .clickable { onClick() }
    )
}
