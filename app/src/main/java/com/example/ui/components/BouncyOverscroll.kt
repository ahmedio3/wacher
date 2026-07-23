package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

private const val MAX_OVERSCROLL_PX = 250f

@Composable
fun Modifier.bouncyOverscroll(isVertical: Boolean = true): Modifier {
    val overscrollState = remember { Animatable(0f) }
    var currentOverscroll by remember { mutableStateOf(0f) }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (currentOverscroll != 0f) {
                    val delta = if (isVertical) available.y else available.x
                    if (delta * currentOverscroll < 0f) {
                        val consumed = delta.coerceIn(-currentOverscroll, -currentOverscroll)
                        currentOverscroll += consumed
                        return if (isVertical) Offset(0f, consumed) else Offset(consumed, 0f)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag) {
                    val delta = if (isVertical) available.y else available.x
                    if (delta != 0f) {
                        currentOverscroll =
                            (currentOverscroll + delta).coerceIn(-MAX_OVERSCROLL_PX, MAX_OVERSCROLL_PX)
                        return available
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentOverscroll != 0f) {
                    overscrollState.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    currentOverscroll = 0f
                }
                return Velocity.Zero
            }
        }
    }

    return this
        .nestedScroll(connection)
        .graphicsLayer {
            if (isVertical) translationY = currentOverscroll
            else translationX = currentOverscroll
        }
}
