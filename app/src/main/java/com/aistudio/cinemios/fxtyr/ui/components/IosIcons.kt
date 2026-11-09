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

    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosBookmark",
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
                moveTo(6.5f, 4f)
                lineTo(17.5f, 4f)
                curveTo(18.6f, 4f, 19.5f, 4.9f, 19.5f, 6f)
                lineTo(19.5f, 20.5f)
                lineTo(12f, 16.5f)
                lineTo(4.5f, 20.5f)
                lineTo(4.5f, 6f)
                curveTo(4.5f, 4.9f, 5.4f, 4f, 6.5f, 4f)
                close()
            }
        }.build()
    }

    val BookmarkFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosBookmarkFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6.5f, 4f)
                lineTo(17.5f, 4f)
                curveTo(18.6f, 4f, 19.5f, 4.9f, 19.5f, 6f)
                lineTo(19.5f, 20.5f)
                lineTo(12f, 16.5f)
                lineTo(4.5f, 20.5f)
                lineTo(4.5f, 6f)
                curveTo(4.5f, 4.9f, 5.4f, 4f, 6.5f, 4f)
                close()
            }
        }.build()
    }

    val Checkmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosCheckmark",
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
                moveTo(5f, 12.5f)
                lineTo(10f, 17f)
                lineTo(19.5f, 7f)
            }
        }.build()
    }

    val CheckmarkCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosCheckmarkCircle",
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

                moveTo(8f, 12f)
                lineTo(11f, 15f)
                lineTo(16.5f, 9.5f)
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosPlay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8.5f, 5.8f)
                curveTo(7.8f, 5.3f, 7f, 5.8f, 7f, 6.7f)
                lineTo(7f, 17.3f)
                curveTo(7f, 18.2f, 7.8f, 18.7f, 8.5f, 18.2f)
                lineTo(18f, 12.9f)
                curveTo(18.7f, 12.5f, 18.7f, 11.5f, 18f, 11.1f)
                close()
            }
        }.build()
    }

    val PlayCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosPlayCircle",
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

                moveTo(10f, 8.5f)
                lineTo(16f, 12f)
                lineTo(10f, 15.5f)
                close()
            }
        }.build()
    }

    val Sparkle: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosSparkle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2.5f)
                curveTo(12.3f, 7.8f, 16.2f, 11.7f, 21.5f, 12f)
                curveTo(16.2f, 12.3f, 12.3f, 16.2f, 12f, 21.5f)
                curveTo(11.7f, 16.2f, 7.8f, 12.3f, 2.5f, 12f)
                curveTo(7.8f, 11.7f, 11.7f, 7.8f, 12f, 2.5f)
                close()
            }
        }.build()
    }

    val Sparkles: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosSparkles",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Primary sparkle
                moveTo(10f, 3.5f)
                curveTo(10.3f, 7.8f, 14.2f, 11.7f, 18.5f, 12f)
                curveTo(14.2f, 12.3f, 10.3f, 16.2f, 10f, 20.5f)
                curveTo(9.7f, 16.2f, 5.8f, 12.3f, 1.5f, 12f)
                curveTo(5.8f, 11.7f, 9.7f, 7.8f, 10f, 3.5f)
                close()

                // Secondary sparkle at top-right
                moveTo(18.5f, 3f)
                curveTo(18.7f, 4.6f, 19.9f, 5.8f, 21.5f, 6f)
                curveTo(19.9f, 6.2f, 18.7f, 7.4f, 18.5f, 9f)
                curveTo(18.3f, 7.4f, 17.1f, 6.2f, 15.5f, 6f)
                curveTo(17.1f, 5.8f, 18.3f, 4.6f, 18.5f, 3f)
                close()
            }
        }.build()
    }

    val Crown: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosCrown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.5f, 18.5f)
                curveTo(3.5f, 19.3f, 4.2f, 20f, 5f, 20f)
                lineTo(19f, 20f)
                curveTo(19.8f, 20f, 20.5f, 19.3f, 20.5f, 18.5f)
                lineTo(20.5f, 17f)
                lineTo(3.5f, 17f)
                close()

                moveTo(4f, 15.5f)
                lineTo(20f, 15.5f)
                lineTo(18.5f, 9.5f)
                lineTo(14f, 13f)
                lineTo(12f, 6f)
                lineTo(10f, 13f)
                lineTo(5.5f, 9.5f)
                close()
            }
        }.build()
    }

    val TrendingUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosTrendingUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 17.2f)
                lineTo(9.2f, 12f)
                lineTo(13f, 15.8f)
                lineTo(20f, 7.4f)

                moveTo(15.5f, 7.4f)
                lineTo(20f, 7.4f)
                lineTo(20f, 11.9f)
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosLock",
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
                moveTo(7f, 10.5f)
                lineTo(17f, 10.5f)
                curveTo(18.1f, 10.5f, 19f, 11.4f, 19f, 12.5f)
                lineTo(19f, 19f)
                curveTo(19f, 20.1f, 18.1f, 21f, 17f, 21f)
                lineTo(7f, 21f)
                curveTo(5.9f, 21f, 5f, 20.1f, 5f, 19f)
                lineTo(5f, 12.5f)
                curveTo(5f, 11.4f, 5.9f, 10.5f, 7f, 10.5f)
                close()

                moveTo(8.5f, 10.5f)
                lineTo(8.5f, 7.5f)
                curveTo(8.5f, 5.57f, 10.07f, 4f, 12f, 4f)
                curveTo(13.93f, 4f, 15.5f, 5.57f, 15.5f, 7.5f)
                lineTo(15.5f, 10.5f)
            }
        }.build()
    }

    val Filter: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosFilter",
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
                moveTo(4f, 5.8f)
                lineTo(20f, 5.8f)
                lineTo(14f, 12.8f)
                lineTo(14f, 19f)
                lineTo(10f, 17.2f)
                lineTo(10f, 12.8f)
                close()
            }
        }.build()
    }

    val Tag: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosTag",
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
                moveTo(4.5f, 12.5f)
                lineTo(4.5f, 5.5f)
                curveTo(4.5f, 4.95f, 4.95f, 4.5f, 5.5f, 4.5f)
                lineTo(12.5f, 4.5f)
                curveTo(12.77f, 4.5f, 13.03f, 4.61f, 13.23f, 4.8f)
                lineTo(20.2f, 11.77f)
                curveTo(20.6f, 12.17f, 20.6f, 12.83f, 20.2f, 13.23f)
                lineTo(13.23f, 20.2f)
                curveTo(12.83f, 20.6f, 12.17f, 20.6f, 11.77f, 20.2f)
                lineTo(4.8f, 13.23f)
                curveTo(4.61f, 13.03f, 4.5f, 12.77f, 4.5f, 12.5f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(8.5f, 7f)
                curveTo(9.33f, 7f, 10f, 7.67f, 10f, 8.5f)
                curveTo(10f, 9.33f, 9.33f, 10f, 8.5f, 10f)
                curveTo(7.67f, 10f, 7f, 9.33f, 7f, 8.5f)
                curveTo(7f, 7.67f, 7.67f, 7f, 8.5f, 7f)
                close()
            }
        }.build()
    }

    val Sort: ImageVector by lazy {
        ImageVector.Builder(
            name = "IosSort",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.5f, 18.5f)
                lineTo(7.5f, 6f)
                moveTo(4.5f, 9f)
                lineTo(7.5f, 6f)
                lineTo(10.5f, 9f)

                moveTo(16.5f, 5.5f)
                lineTo(16.5f, 18f)
                moveTo(13.5f, 15f)
                lineTo(16.5f, 18f)
                lineTo(19.5f, 15f)
            }
        }.build()
    }
}
