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
val Icons.ArrowUp: ImageVector
    get() {
        val currentIcon = arrowUp
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
            moveTo(7.41f, 15.41f)
            lineTo(12.0f, 10.83f)
            lineToRelative(4.59f, 4.58f)
            lineTo(18.0f, 14.0f)
            lineToRelative(-6.0f, -6.0f)
            lineToRelative(-6.0f, 6.0f)
            close()
        }.build().also { arrowUp = it }
    }
private var arrowUp: ImageVector? = null
