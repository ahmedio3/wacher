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

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9.5f, 6f)
                lineTo(15.5f, 12f)
                lineTo(9.5f, 18f)
            }
        }.build()
    }

    val ChevronLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosChevronLeft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14.5f, 6f)
                lineTo(8.5f, 12f)
                lineTo(14.5f, 18f)
            }
        }.build()
    }

    val ChevronDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosChevronDown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 9.5f)
                lineTo(12f, 15.5f)
                lineTo(18f, 9.5f)
            }
        }.build()
    }

    val ChevronUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosChevronUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 15.5f)
                lineTo(12f, 9.5f)
                lineTo(18f, 15.5f)
            }
        }.build()
    }

    val ArrowDownCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosArrowDownCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
                curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                close()

                moveTo(12f, 7.5f)
                lineTo(12f, 15.5f)

                moveTo(8.5f, 12f)
                lineTo(12f, 15.5f)
                lineTo(15.5f, 12f)
            }
        }.build()
    }

    val ClosedCaptions: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosClosedCaptions",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 6f)
                lineTo(19f, 6f)
                curveTo(20.5f, 6f, 21.5f, 7f, 21.5f, 8.5f)
                lineTo(21.5f, 15.5f)
                curveTo(21.5f, 17f, 20.5f, 18f, 19f, 18f)
                lineTo(5f, 18f)
                curveTo(3.5f, 18f, 2.5f, 17f, 2.5f, 15.5f)
                lineTo(2.5f, 8.5f)
                curveTo(2.5f, 7.5f, 3.5f, 6f, 5f, 6f)
                close()

                moveTo(10.5f, 10f)
                curveTo(8f, 9.5f, 6.5f, 11f, 6.5f, 12f)
                curveTo(6.5f, 13f, 8f, 14.5f, 10.5f, 14f)

                moveTo(17f, 10f)
                curveTo(14.5f, 9.5f, 13f, 11f, 13f, 12f)
                curveTo(13f, 13f, 14.5f, 14.5f, 17f, 14f)
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosRefresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 12f)
                curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
                curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
                curveTo(14.5f, 4f, 16.7f, 5.15f, 18.2f, 7f)

                moveTo(14.5f, 7f)
                lineTo(18.5f, 7f)
                lineTo(18.5f, 3f)
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosClose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                moveTo(17.5f, 6.5f)
                lineTo(6.5f, 17.5f)
            }
        }.build()
    }
}
