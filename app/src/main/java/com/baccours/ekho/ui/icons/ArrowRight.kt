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
val Icons.ArrowRight: ImageVector
    get() {
        val currentIcon = arrowRight
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Arrow Right",
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
            moveTo(9.29f, 15.88f)
            lineTo(13.17f, 12.0f)
            lineTo(9.29f, 8.12f)
            curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
            curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
            lineToRelative(4.59f, 4.59f)
            curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
            lineTo(10.7f, 17.3f)
            curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0.0f)
            curveToRelative(-0.38f, -0.39f, -0.39f, -1.03f, 0.0f, -1.42f)
            close()
        }.build().also { arrowRight = it }
    }
private var arrowRight: ImageVector? = null
