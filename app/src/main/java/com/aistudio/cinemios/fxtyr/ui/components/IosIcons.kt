package com.aistudio.cinemios.fxtyr.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Minimal iOS-style line icons drawn as vectors so they stay crisp and follow
 * the current tint instead of relying on filled Material glyphs.
 */
object IosIcons {

    val Share: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosShare",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Upward arrow leaving the box
                moveTo(12f, 14.5f)
                lineTo(12f, 3.5f)
                moveTo(8.5f, 7f)
                lineTo(12f, 3.5f)
                lineTo(15.5f, 7f)

                // Open-top box
                moveTo(8.5f, 10f)
                lineTo(5.5f, 10f)
                lineTo(5.5f, 20f)
                lineTo(18.5f, 20f)
                lineTo(18.5f, 10f)
                lineTo(15.5f, 10f)
            }
        }.build()
    }

    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosDownload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Downward arrow
                moveTo(12f, 3.5f)
                lineTo(12f, 14.5f)
                moveTo(8.5f, 11f)
                lineTo(12f, 14.5f)
                lineTo(15.5f, 11f)

                // Tray
                moveTo(5.5f, 16.5f)
                lineTo(5.5f, 19.5f)
                lineTo(18.5f, 19.5f)
                lineTo(18.5f, 16.5f)
            }
        }.build()
    }
}
