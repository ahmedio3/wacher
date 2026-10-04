package com.aistudio.cinemios.fxtyr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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
     * iOS Home Filled - Matching user reference image (media_1791077938178.jpg)
     * Gable roof with soft rounded tip, curved bottom corners, and bottom arched doorway cutout.
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
                moveTo(12f, 3.2f)
                curveTo(12.5f, 3.2f, 13f, 3.5f, 13.4f, 3.8f)
                lineTo(20.4f, 9.5f)
                curveTo(20.8f, 9.8f, 21f, 10.3f, 21f, 10.8f)
                lineTo(21f, 18.5f)
                curveTo(21f, 19.9f, 19.9f, 21f, 18.5f, 21f)
                lineTo(14.5f, 21f)
                lineTo(14.5f, 15f)
                curveTo(14.5f, 13.6f, 13.4f, 12.5f, 12f, 12.5f)
                curveTo(10.6f, 12.5f, 9.5f, 13.6f, 9.5f, 15f)
                lineTo(9.5f, 21f)
                lineTo(5.5f, 21f)
                curveTo(4.1f, 21f, 3f, 19.9f, 3f, 18.5f)
                lineTo(3f, 10.8f)
                curveTo(3f, 10.3f, 3.2f, 9.8f, 3.6f, 9.5f)
                lineTo(10.6f, 3.8f)
                curveTo(11f, 3.5f, 11.5f, 3.2f, 12f, 3.2f)
                close()
            }
        }.build()
    }

    /**
     * iOS Home Outline - Exactly matching HomeFilled silhouette with arched doorway
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
                lineTo(20.2f, 10.2f)
                lineTo(20.2f, 18.5f)
                curveTo(20.2f, 19.8f, 19.2f, 20.8f, 18f, 20.8f)
                lineTo(14.8f, 20.8f)
                lineTo(14.8f, 15f)
                curveTo(14.8f, 13.5f, 13.5f, 12.2f, 12f, 12.2f)
                curveTo(10.5f, 12.2f, 9.2f, 13.5f, 9.2f, 15f)
                lineTo(9.2f, 20.8f)
                lineTo(6f, 20.8f)
                curveTo(4.8f, 20.8f, 3.8f, 19.8f, 3.8f, 18.5f)
                lineTo(3.8f, 10.2f)
                close()
            }
        }.build()
    }

    /**
     * iOS Settings Gear (Filled)
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
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(12f, 15.5f)
                curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
                curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
                curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
                curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
                close()

                moveTo(19.43f, 12.98f)
                curveTo(19.47f, 12.66f, 19.5f, 12.34f, 19.5f, 12f)
                curveTo(19.5f, 11.66f, 19.47f, 11.34f, 19.43f, 11.02f)
                lineTo(21.54f, 9.37f)
                curveTo(21.73f, 9.22f, 21.78f, 8.95f, 21.66f, 8.73f)
                lineTo(19.66f, 5.27f)
                curveTo(19.54f, 5.05f, 19.27f, 4.96f, 19.05f, 5.05f)
                lineTo(16.56f, 6.05f)
                curveTo(16.04f, 5.65f, 15.48f, 5.32f, 14.87f, 5.07f)
                lineTo(14.49f, 2.42f)
                curveTo(14.46f, 2.18f, 14.25f, 2f, 14f, 2f)
                lineTo(10f, 2f)
                curveTo(9.75f, 2f, 9.54f, 2.18f, 9.51f, 2.42f)
                lineTo(9.13f, 5.07f)
                curveTo(8.52f, 5.32f, 7.96f, 5.66f, 7.44f, 6.05f)
                lineTo(4.95f, 5.05f)
                curveTo(4.72f, 4.96f, 4.46f, 5.05f, 4.34f, 5.27f)
                lineTo(2.34f, 8.73f)
                curveTo(2.21f, 8.95f, 2.27f, 9.22f, 2.46f, 9.37f)
                lineTo(4.57f, 11.02f)
                curveTo(4.53f, 11.34f, 4.5f, 11.67f, 4.5f, 12f)
                curveTo(4.5f, 12.33f, 4.53f, 12.66f, 4.57f, 12.98f)
                lineTo(2.46f, 14.63f)
                curveTo(2.27f, 14.78f, 2.21f, 15.05f, 2.34f, 15.27f)
                lineTo(4.34f, 18.73f)
                curveTo(4.46f, 18.95f, 4.73f, 19.04f, 4.95f, 18.95f)
                lineTo(7.44f, 17.95f)
                curveTo(7.96f, 18.35f, 8.52f, 18.68f, 9.13f, 18.93f)
                lineTo(9.51f, 21.58f)
                curveTo(9.54f, 21.82f, 9.75f, 22f, 10f, 22f)
                lineTo(14f, 22f)
                curveTo(14.25f, 22f, 14.46f, 21.82f, 14.49f, 21.58f)
                lineTo(14.87f, 18.93f)
                curveTo(15.48f, 18.68f, 16.04f, 18.34f, 16.56f, 17.95f)
                lineTo(19.05f, 18.95f)
                curveTo(19.28f, 19.04f, 19.54f, 18.95f, 19.66f, 18.73f)
                lineTo(21.66f, 15.27f)
                curveTo(21.78f, 15.05f, 21.73f, 14.78f, 21.54f, 14.63f)
                lineTo(19.43f, 12.98f)
                close()
            }
        }.build()
    }

    /**
     * iOS Settings Gear (Outlined)
     */
    val SettingsOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "AppIcons.SettingsOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19.43f, 12.98f)
                curveTo(19.47f, 12.66f, 19.5f, 12.34f, 19.5f, 12f)
                curveTo(19.5f, 11.66f, 19.47f, 11.34f, 19.43f, 11.02f)
                lineTo(21.54f, 9.37f)
                curveTo(21.73f, 9.22f, 21.78f, 8.95f, 21.66f, 8.73f)
                lineTo(19.66f, 5.27f)
                curveTo(19.54f, 5.05f, 19.27f, 4.96f, 19.05f, 5.05f)
                lineTo(16.56f, 6.05f)
                curveTo(16.04f, 5.65f, 15.48f, 5.32f, 14.87f, 5.07f)
                lineTo(14.49f, 2.42f)
                curveTo(14.46f, 2.18f, 14.25f, 2f, 14f, 2f)
                lineTo(10f, 2f)
                curveTo(9.75f, 2f, 9.54f, 2.18f, 9.51f, 2.42f)
                lineTo(9.13f, 5.07f)
                curveTo(8.52f, 5.32f, 7.96f, 5.66f, 7.44f, 6.05f)
                lineTo(4.95f, 5.05f)
                curveTo(4.72f, 4.96f, 4.46f, 5.05f, 4.34f, 5.27f)
                lineTo(2.34f, 8.73f)
                curveTo(2.21f, 8.95f, 2.27f, 9.22f, 2.46f, 9.37f)
                lineTo(4.57f, 11.02f)
                curveTo(4.53f, 11.34f, 4.5f, 11.67f, 4.5f, 12f)
                curveTo(4.5f, 12.33f, 4.53f, 12.66f, 4.57f, 12.98f)
                lineTo(2.46f, 14.63f)
                curveTo(2.27f, 14.78f, 2.21f, 15.05f, 2.34f, 15.27f)
                lineTo(4.34f, 18.73f)
                curveTo(4.46f, 18.95f, 4.73f, 19.04f, 4.95f, 18.95f)
                lineTo(7.44f, 17.95f)
                curveTo(7.96f, 18.35f, 8.52f, 18.68f, 9.13f, 18.93f)
                lineTo(9.51f, 21.58f)
                curveTo(9.54f, 21.82f, 9.75f, 22f, 10f, 22f)
                lineTo(14f, 22f)
                curveTo(14.25f, 22f, 14.46f, 21.82f, 14.49f, 21.58f)
                lineTo(14.87f, 18.93f)
                curveTo(15.48f, 18.68f, 16.04f, 18.34f, 16.56f, 17.95f)
                lineTo(19.05f, 18.95f)
                curveTo(19.28f, 19.04f, 19.54f, 18.95f, 19.66f, 18.73f)
                lineTo(21.66f, 15.27f)
                curveTo(21.78f, 15.05f, 21.73f, 14.78f, 21.54f, 14.63f)
                lineTo(19.43f, 12.98f)
                close()
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f
            ) {
                moveTo(12f, 15.5f)
                curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
                curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
                curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
                curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
                close()
            }
        }.build()
    }

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
