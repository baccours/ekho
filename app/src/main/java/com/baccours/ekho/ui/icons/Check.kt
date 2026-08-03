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
val Icons.Check: ImageVector
    get() {
        val currentIcon = check
        if (currentIcon != null) {
            return currentIcon
        }
        return ImageVector.Builder(
            name = "Check",
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
            moveTo(9.0f, 16.17f)
            lineTo(5.53f, 12.7f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
            curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
            lineToRelative(4.18f, 4.18f)
            curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
            lineTo(20.29f, 7.71f)
            curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
            curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
            lineTo(9.0f, 16.17f)
            close()
        }.build().also { check = it }
    }
private var check: ImageVector? = null
