package com.aistudio.cinemios.fxtyr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    /**
     * Exact 3 books on a shelf from Bar 3 in the user reference image (|| \).
     * Two vertical books and one tilted book resting against them.
     */
    val Library: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.Library",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Book 1 (vertical, left)
                moveTo(6f, 4.5f)
                lineTo(6f, 19.5f)
                // Book 2 (vertical, middle)
                moveTo(11f, 4.5f)
                lineTo(11f, 19.5f)
                // Book 3 (tilted, leaning right)
                moveTo(15.5f, 5f)
                lineTo(19.5f, 19.5f)
            }
        }.build()
    }

    /**
     * Clean iOS Search Magnifying Glass with circular lens and angled stem.
     */
    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Circular lens
                moveTo(16.5f, 10.5f)
                curveTo(16.5f, 13.81f, 13.81f, 16.5f, 10.5f, 16.5f)
                curveTo(7.19f, 16.5f, 4.5f, 13.81f, 4.5f, 10.5f)
                curveTo(4.5f, 7.19f, 7.19f, 4.5f, 10.5f, 4.5f)
                curveTo(13.81f, 4.5f, 16.5f, 7.19f, 16.5f, 10.5f)
                close()
                // Angled handle
                moveTo(15.2f, 15.2f)
                lineTo(20f, 20f)
            }
        }.build()
    }

    /**
     * iOS Home Filled
     */
    val HomeFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.HomeFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 3f)
                lineTo(20.5f, 10f)
                curveTo(20.9f, 10.3f, 21f, 10.9f, 20.7f, 11.3f)
                curveTo(20.3f, 11.7f, 19.7f, 11.8f, 19.3f, 11.5f)
                lineTo(18.5f, 10.8f)
                lineTo(18.5f, 19f)
                curveTo(18.5f, 20.1f, 17.6f, 21f, 16.5f, 21f)
                lineTo(7.5f, 21f)
                curveTo(6.4f, 21f, 5.5f, 20.1f, 5.5f, 19f)
                lineTo(5.5f, 10.8f)
                lineTo(4.7f, 11.5f)
                curveTo(4.3f, 11.8f, 3.7f, 11.7f, 3.3f, 11.3f)
                curveTo(3f, 10.9f, 3.1f, 10.3f, 3.5f, 10f)
                close()
            }
        }.build()
    }

    /**
     * iOS Home Outline
     */
    val HomeOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.HomeOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f)
                lineTo(19.5f, 9.8f)
                lineTo(19.5f, 19f)
                curveTo(19.5f, 20f, 18.8f, 20.8f, 17.8f, 20.8f)
                lineTo(6.2f, 20.8f)
                curveTo(5.2f, 20.8f, 4.5f, 20f, 4.5f, 19f)
                lineTo(4.5f, 9.8f)
                close()
            }
        }.build()
    }

    /**
     * iOS Settings Sliders / Gear
     */
    val SettingsFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.SettingsFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 6.5f)
                lineTo(20f, 6.5f)
                moveTo(4f, 12f)
                lineTo(20f, 12f)
                moveTo(4f, 17.5f)
                lineTo(20f, 17.5f)
            }
            path(fill = SolidColor(Color.White)) {
                // Knobs
                moveTo(8.5f, 6.5f)
                curveTo(8.5f, 7.6f, 7.6f, 8.5f, 6.5f, 8.5f)
                curveTo(5.4f, 8.5f, 4.5f, 7.6f, 4.5f, 6.5f)
                curveTo(4.5f, 5.4f, 5.4f, 4.5f, 6.5f, 4.5f)
                curveTo(7.6f, 4.5f, 8.5f, 5.4f, 8.5f, 6.5f)
                close()

                moveTo(17.5f, 12f)
                curveTo(17.5f, 13.1f, 16.6f, 14f, 15.5f, 14f)
                curveTo(14.4f, 14f, 13.5f, 13.1f, 13.5f, 12f)
                curveTo(13.5f, 10.9f, 14.4f, 10f, 15.5f, 10f)
                curveTo(16.6f, 10f, 17.5f, 10.9f, 17.5f, 12f)
                close()

                moveTo(11.5f, 17.5f)
                curveTo(11.5f, 18.6f, 10.6f, 19.5f, 9.5f, 19.5f)
                curveTo(8.4f, 19.5f, 7.5f, 18.6f, 7.5f, 17.5f)
                curveTo(7.5f, 16.4f, 8.4f, 15.5f, 9.5f, 15.5f)
                curveTo(10.6f, 15.5f, 11.5f, 16.4f, 11.5f, 17.5f)
                close()
            }
        }.build()
    }

    val SettingsOutline: ImageVector by lazy { SettingsFilled }

    /**
     * iOS Close 'X'
     */
    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.Close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                moveTo(17.5f, 6.5f)
                lineTo(6.5f, 17.5f)
            }
        }.build()
    }

    /**
     * iOS History / Recent clock
     */
    val History: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.History",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 4f)
                curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
                curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
                curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
                curveTo(20f, 7.58f, 16.42f, 4f, 12f, 4f)
                close()
                moveTo(12f, 7.5f)
                lineTo(12f, 12f)
                lineTo(15.5f, 14f)
            }
        }.build()
    }

    /**
     * MovieBox Film / Clapper Icon
     */
    val MovieBox: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.MovieBox",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 7.5f)
                lineTo(20f, 7.5f)
                lineTo(20f, 18.5f)
                curveTo(20f, 19.5f, 19.2f, 20.3f, 18.2f, 20.3f)
                lineTo(5.8f, 20.3f)
                curveTo(4.8f, 20.3f, 4f, 19.5f, 4f, 18.5f)
                close()
                moveTo(4f, 7.5f)
                lineTo(5.5f, 4.2f)
                lineTo(18.5f, 4.2f)
                lineTo(20f, 7.5f)
                // Play indicator inside
                moveTo(10.5f, 11.2f)
                lineTo(15f, 13.8f)
                lineTo(10.5f, 16.4f)
                close()
            }
        }.build()
    }

    /**
     * Gold Star for Rating
     */
    val Star: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.Star",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFFFFD700))) {
                moveTo(12f, 2.5f)
                lineTo(15.09f, 8.26f)
                lineTo(22f, 9.27f)
                lineTo(17f, 14.14f)
                lineTo(18.18f, 21.02f)
                lineTo(12f, 17.77f)
                lineTo(5.82f, 21.02f)
                lineTo(7f, 14.14f)
                lineTo(2f, 9.27f)
                lineTo(8.91f, 8.26f)
                close()
            }
        }.build()
    }

    /**
     * Trash / Delete icon
     */
    val Trash: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.Trash",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 7f)
                lineTo(19f, 7f)
                moveTo(10f, 11f)
                lineTo(10f, 16f)
                moveTo(14f, 11f)
                lineTo(14f, 16f)
                moveTo(6.5f, 7f)
                lineTo(7.5f, 19f)
                curveTo(7.5f, 19.8f, 8.2f, 20.5f, 9f, 20.5f)
                lineTo(15f, 20.5f)
                curveTo(15.8f, 20.5f, 16.5f, 19.8f, 16.5f, 19f)
                lineTo(17.5f, 7f)
                moveTo(9f, 7f)
                lineTo(9.5f, 4.5f)
                lineTo(14.5f, 4.5f)
                lineTo(15f, 7f)
            }
        }.build()
    }
}
