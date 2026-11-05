package com.baccours.ekho.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.baccours.ekho.R
import com.baccours.ekho.audio.EqualizerBands
import com.baccours.ekho.data.EqPreset
import java.text.DecimalFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSection(
    preset: EqPreset,
    bandLevels: List<Int>,
    onPresetChange: (EqPreset) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit
) {
    var isEditing by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { isEditing = !isEditing }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.equalizer_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Show preset label when collapsed.
            if (!isEditing) {
                Text(
                    text = stringResource(preset.labelRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                painter = painterResource(
                    if (isEditing) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
                ),
                contentDescription = stringResource(
                    if (isEditing) R.string.collapse else R.string.expand
                )
            )
        }
    }

    AnimatedVisibility(
        visible = isEditing,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Spacer(modifier = Modifier.height(8.dp))

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = stringResource(preset.labelRes()),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.preset_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    EqPreset.selectable.forEach { selection ->
                        DropdownMenuItem(
                            text = { Text(stringResource(selection.labelRes())) },
                            onClick = {
                                onPresetChange(selection)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                bandLevels.forEachIndexed { bandId, level ->
                    VerticalBandSlider(
                        level = level,
                        frequency = EqualizerBands.FREQUENCIES_HZ[bandId],
                        onLevelChange = { newLevel ->
                            onBandLevelChange(bandId, newLevel)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun VerticalBandSlider(
    level: Int,
    frequency: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val freqText = if (frequency >= 1000) {
        // Format frequency: e.g. 3.6 kHz vs 14 kHz.
        val khz = remember(frequency) { DecimalFormat("0.#").format(frequency / 1000.0) }
        stringResource(R.string.band_freq_khz, khz)
    } else {
        stringResource(R.string.band_freq_hz, frequency)
    }
    val db = (level / 100f).roundToInt()
    val levelText = if (db == 0) {
        stringResource(R.string.band_level_zero)
    } else {
        stringResource(R.string.band_level_format, db)
    }
    val bandDescription = stringResource(R.string.band_description, freqText)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = levelText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Rotated vertical slider.
        Slider(
            value = level.toFloat(),
            onValueChange = { onLevelChange(it.roundToInt()) }, // 1 dB grid steps
            valueRange = LEVEL_RANGE,
            steps = LEVEL_STEPS,
            modifier = Modifier
                .height(200.dp)
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(-placeable.width, 0)
                    }
                }
                .semantics {
                    contentDescription = bandDescription
                    stateDescription = levelText
                },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = freqText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private val LEVEL_RANGE =
    EqualizerBands.MIN_LEVEL_MB.toFloat()..EqualizerBands.MAX_LEVEL_MB.toFloat()

/** 1 dB detents (29 interior steps for 30 dB range). */
private const val LEVEL_STEPS = (EqualizerBands.MAX_LEVEL_MB - EqualizerBands.MIN_LEVEL_MB) / 100 - 1

private fun EqPreset.labelRes(): Int = when (this) {
    EqPreset.FLAT -> R.string.preset_flat
    EqPreset.VOICE -> R.string.preset_voice
    EqPreset.BOOST -> R.string.preset_boost
    EqPreset.CUSTOM -> R.string.preset_custom
}

@Preview(showBackground = true)
@Composable
fun EqualizerSectionPreview() {
    EqualizerSection(
        preset = EqPreset.FLAT,
        bandLevels = listOf(0, 0, 0, 0, 0),
        onPresetChange = {},
        onBandLevelChange = { _, _ -> }
    )
}
