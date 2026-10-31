package com.baccours.ekho.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.ekho.R
import com.baccours.ekho.audio.EqualizerBands
import com.baccours.ekho.data.EqPreset
import com.baccours.ekho.service.AudioService
import com.baccours.ekho.ui.components.SwipeToggle
import com.baccours.ekho.ui.icons.ArrowDown
import com.baccours.ekho.ui.icons.ArrowUp
import com.baccours.ekho.ui.icons.Icons
import com.baccours.ekho.ui.icons.Play
import com.baccours.ekho.ui.icons.Stop
import com.baccours.ekho.ui.theme.EkhoTheme
import kotlin.math.roundToInt

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var micPermissionDenied by remember { mutableStateOf(false) }

    fun startService() {
        ContextCompat.startForegroundService(context, Intent(context, AudioService::class.java))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Only the microphone is required. Notifications are optional: the foreground service
        // runs without them, so a denial there must never block the app.
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        micPermissionDenied = !micGranted
        if (micGranted) startService()
    }

    MainContent(
        uiState = uiState,
        bandFrequencies = viewModel.bandFrequencies,
        bandLevelRange = viewModel.bandLevelRange,
        micPermissionDenied = micPermissionDenied,
        onToggleService = {
            if (uiState.isServiceRunning) {
                val intent = Intent(context, AudioService::class.java).apply {
                    action = AudioService.ACTION_STOP
                }
                context.startService(intent)
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                micPermissionDenied = false
                startService()
            } else {
                val permissions = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                launcher.launch(permissions.toTypedArray())
            }
        },
        onOpenAppSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        },
        onToggleBypass = { viewModel.setBypassLoopbackProtection(it) },
        onPresetChange = { viewModel.setPreset(it) },
        onBandLevelChange = { bandId, level -> viewModel.updateBandLevel(bandId, level) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    uiState: MainUiState,
    bandFrequencies: List<Int>,
    bandLevelRange: ClosedFloatingPointRange<Float>,
    micPermissionDenied: Boolean,
    onToggleService: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onToggleBypass: (Boolean) -> Unit,
    onPresetChange: (EqPreset) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = !uiState.isLoopbackSafe,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    SafetyStatusCard(
                        bypassLoopbackProtection = uiState.bypassLoopbackProtection,
                        onToggleBypass = onToggleBypass
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            ServiceControlButton(
                isServiceRunning = uiState.isServiceRunning,
                isStreaming = uiState.isStreaming,
                hasError = uiState.hasError,
                onToggle = onToggleService
            )

            if (micPermissionDenied) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.permission_mic_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenAppSettings) {
                        Text(stringResource(R.string.permission_open_settings))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            EqualizerSection(
                preset = uiState.preset,
                bandLevels = uiState.bandLevels,
                bandFrequencies = bandFrequencies,
                bandLevelRange = bandLevelRange,
                onPresetChange = onPresetChange,
                onBandLevelChange = onBandLevelChange
            )
        }
    }
}

@Composable
fun SafetyStatusCard(
    bypassLoopbackProtection: Boolean,
    onToggleBypass: (Boolean) -> Unit
) {
    val backgroundColor = if (bypassLoopbackProtection) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.tertiaryContainer

    val contentColor = if (bypassLoopbackProtection) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onTertiaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.safety_title),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = stringResource(
                    if (bypassLoopbackProtection) R.string.safety_hint_bypassed
                    else R.string.safety_hint_protected
                ),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SwipeToggle(
                checked = bypassLoopbackProtection,
                onCheckedChange = onToggleBypass,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ServiceControlButton(
    isServiceRunning: Boolean,
    isStreaming: Boolean,
    hasError: Boolean,
    onToggle: () -> Unit
) {
    val buttonColor = when {
        !isServiceRunning -> MaterialTheme.colorScheme.primary
        isStreaming -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    val buttonText = stringResource(
        when {
            !isServiceRunning -> R.string.service_start
            isStreaming -> R.string.service_stop
            hasError -> R.string.service_error
            else -> R.string.service_waiting
        }
    )

    Button(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
    ) {
        Icon(
            imageVector = if (isServiceRunning) Icons.Stop else Icons.Play,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = buttonText,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSection(
    preset: EqPreset,
    bandLevels: List<Int>,
    bandFrequencies: List<Int>,
    bandLevelRange: ClosedFloatingPointRange<Float>,
    onPresetChange: (EqPreset) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isEditing = !isEditing }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.equalizer_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Icon(
            imageVector = if (isEditing) Icons.ArrowUp
            else Icons.ArrowDown,
            contentDescription = stringResource(
                if (isEditing) R.string.collapse else R.string.expand
            )
        )
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
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                bandLevels.forEachIndexed { bandId, level ->
                    VerticalBandSlider(
                        bandId = bandId,
                        level = level,
                        frequency = bandFrequencies.getOrNull(bandId),
                        valueRange = bandLevelRange,
                        onLevelChange = { newLevel ->
                            onBandLevelChange(bandId, newLevel)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VerticalBandSlider(
    bandId: Int,
    level: Int,
    frequency: Int?,
    valueRange: ClosedFloatingPointRange<Float>,
    onLevelChange: (Int) -> Unit
) {
    val freqText = when {
        frequency == null -> stringResource(R.string.band_fallback, bandId + 1)
        frequency >= 1000 -> stringResource(R.string.band_freq_khz, frequency / 1000)
        else -> stringResource(R.string.band_freq_hz, frequency)
    }
    val levelText = stringResource(R.string.band_level_format, (level / 100f).roundToInt())
    val bandDescription = stringResource(R.string.band_description, freqText)

    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = levelText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // Vertical Slider using rotation
        Slider(
            value = level.toFloat(),
            onValueChange = { onLevelChange(snapToDecibel(it)) },
            valueRange = valueRange,
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
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
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

/** Slider positions are snapped to whole decibels (levels are stored in millibels). */
private fun snapToDecibel(value: Float): Int = (value / 100f).roundToInt() * 100

private fun EqPreset.labelRes(): Int = when (this) {
    EqPreset.FLAT -> R.string.preset_flat
    EqPreset.VOICE -> R.string.preset_voice
    EqPreset.BOOST -> R.string.preset_boost
    EqPreset.CUSTOM -> R.string.preset_custom
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    EkhoTheme {
        MainContent(
            uiState = MainUiState(
                preset = EqPreset.CUSTOM,
                bandLevels = listOf(200, 0, -300, 100, 0),
                isLoopbackSafe = false,
                isServiceRunning = true
            ),
            bandFrequencies = EqualizerBands.FREQUENCIES_HZ,
            bandLevelRange = EqualizerBands.MIN_LEVEL_MB.toFloat()..EqualizerBands.MAX_LEVEL_MB.toFloat(),
            micPermissionDenied = false,
            onToggleService = {},
            onOpenAppSettings = {},
            onToggleBypass = {},
            onPresetChange = {},
            onBandLevelChange = { _, _ -> }
        )
    }
}
