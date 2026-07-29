package com.baccours.ekho.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.baccours.ekho.ui.icons.Check
import com.baccours.ekho.ui.icons.ArrowRight
import com.baccours.ekho.ui.icons.Icons
import kotlin.math.roundToInt

/**
 * A Material 3 slider-shaped toggle component.
 */
@Composable
fun SwipeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    swipeThreshold: Float = 0.9f,
    trackHeight: Dp = 56.dp,
    thumbSize: Dp = 48.dp,
    trackPadding: Dp = 4.dp,
    activeTrackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    thumbContentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val density = LocalDensity.current
    val clampedThreshold = swipeThreshold.coerceIn(0.01f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
                if (enabled) {
                    onClick(label = "Toggle") {
                        onCheckedChange(!checked)
                        true
                    }
                }
            }
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(
                if (enabled) inactiveTrackColor
                else inactiveTrackColor.copy(alpha = 0.38f)
            )
            .padding(trackPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val thumbSizePx = with(density) { thumbSize.toPx() }
        val maxOffsetPx = (totalWidthPx - thumbSizePx).coerceAtLeast(0f)

        // Immediate UI frame-state during drag.
        var offsetX by remember(maxOffsetPx) {
            mutableFloatStateOf(if (checked) maxOffsetPx else 0f)
        }
        var isDragging by remember { mutableStateOf(false) }

        // Persistent Animatable for smooth snaps & external updates.
        val animatable = remember { Animatable(offsetX) }

        // Sync internal offset when `checked` changes externally (programmatically).
        LaunchedEffect(checked, maxOffsetPx) {
            if (!isDragging && maxOffsetPx > 0f) {
                val targetPx = if (checked) maxOffsetPx else 0f
                if (offsetX != targetPx) {
                    animatable.snapTo(offsetX)
                    animatable.animateTo(
                        targetValue = targetPx,
                        animationSpec = tween(durationMillis = 200)
                    ) {
                        offsetX = value
                    }
                }
            }
        }

        // Normalized progress (0.0f to 1.0f).
        val progress = if (maxOffsetPx > 0f) (offsetX / maxOffsetPx).coerceIn(0f, 1f) else if (checked) 1f else 0f

        // Track.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(thumbSize + with(density) { offsetX.toDp() })
                .clip(CircleShape)
                .background(
                    if (enabled) activeTrackColor.copy(alpha = 0.2f + (progress * 0.8f))
                    else activeTrackColor.copy(alpha = 0.12f)
                )
        )

        // Thumb.
        val draggableState = rememberDraggableState { delta ->
            if (maxOffsetPx > 0f) {
                offsetX = (offsetX + delta).coerceIn(0f, maxOffsetPx)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .size(thumbSize)
                .shadow(
                    elevation = if (isDragging) 6.dp else 2.dp,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(
                    if (enabled) thumbColor
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                .then(
                    if (enabled) {
                        Modifier.draggable(
                            state = draggableState,
                            orientation = Orientation.Horizontal,
                            onDragStarted = {
                                isDragging = true
                                animatable.stop()
                            },
                            onDragStopped = {
                                isDragging = false
                                if (maxOffsetPx > 0f) {
                                    val currentProgress = offsetX / maxOffsetPx

                                    val targetChecked = if (checked) {
                                        currentProgress > (1f - clampedThreshold)
                                    } else {
                                        currentProgress >= clampedThreshold
                                    }

                                    val targetPx = if (targetChecked) maxOffsetPx else 0f

                                    animatable.snapTo(offsetX)
                                    animatable.animateTo(
                                        targetValue = targetPx,
                                        animationSpec = tween(durationMillis = 200)
                                    ) {
                                        offsetX = value
                                    }

                                    if (targetChecked != checked) {
                                        onCheckedChange(targetChecked)
                                    }
                                }
                            }
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (checked) Icons.Check else Icons.ArrowRight,
                contentDescription = null,
                tint = if (enabled) thumbContentColor else MaterialTheme.colorScheme.surface
            )
        }
    }
}
