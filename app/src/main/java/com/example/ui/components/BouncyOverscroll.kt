package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch

private const val MAX_OVERSCROLL_PX = 250f

@Composable
fun Modifier.bouncyOverscroll(isVertical: Boolean = true): Modifier {
    val overscroll = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (overscroll.value != 0f) {
                    val delta = if (isVertical) available.y else available.x
                    if (delta * overscroll.value < 0f) {
                        val consumed = delta.coerceIn(-overscroll.value, -overscroll.value)
                        scope.launch { overscroll.snapTo(overscroll.value + consumed) }
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
                        val newValue = (overscroll.value + delta)
                            .coerceIn(-MAX_OVERSCROLL_PX, MAX_OVERSCROLL_PX)
                        scope.launch { overscroll.snapTo(newValue) }
                        return available
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (overscroll.value != 0f) {
                    overscroll.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                return Velocity.Zero
            }
        }
    }

    return this
        .nestedScroll(connection)
        .graphicsLayer {
            if (isVertical) translationY = overscroll.value
            else translationX = overscroll.value
        }
}
