package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.composed
import androidx.compose.ui.unit.Velocity

private const val MAX_OVERSCROLL_PX = 250f

fun Modifier.bouncyOverscroll(isVertical: Boolean = true): Modifier = composed {
    val overscrollState = remember { Animatable(0f) }
    var currentOverscroll by remember { mutableFloatStateOf(0f) }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (currentOverscroll != 0f) {
                    val delta = if (isVertical) available.y else available.x
                    if (delta * currentOverscroll < 0f) {
                        val consumed = delta.coerceIn(-currentOverscroll, -currentOverscroll)
                        currentOverscroll += consumed
                        overscrollState.snapTo(currentOverscroll)
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
                        overscrollState.snapTo(currentOverscroll)
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

    this
        .nestedScroll(connection)
        .graphicsLayer {
            if (isVertical) translationY = currentOverscroll
            else translationX = currentOverscroll
        }
}
