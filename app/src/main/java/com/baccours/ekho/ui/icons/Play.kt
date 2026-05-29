package com.baccours.ekho.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("UnusedReceiverParameter")
val Icons.Play: ImageVector
    get() {
        val currentIcon = play
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f
        ).path(
            fill = SolidColor(Color(0xFF000000)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(8.0f, 6.82f)
            verticalLineToRelative(10.36f)
            curveToRelative(0.0f, 0.79f, 0.87f, 1.27f, 1.54f, 0.84f)
            lineToRelative(8.14f, -5.18f)
            curveToRelative(0.62f, -0.39f, 0.62f, -1.29f, 0.0f, -1.69f)
            lineTo(9.54f, 5.98f)
            curveTo(8.87f, 5.55f, 8.0f, 6.03f, 8.0f, 6.82f)
            close()
        }.build().also { play = it }
    }
private var play: ImageVector? = null
