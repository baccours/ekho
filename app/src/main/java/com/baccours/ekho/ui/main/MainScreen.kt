package com.baccours.ekho.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.ekho.data.SettingsRepository
import com.baccours.ekho.service.AudioService
import com.baccours.ekho.ui.components.SwipeToggle
import com.baccours.ekho.ui.icons.Icons
import com.baccours.ekho.ui.icons.Play
import com.baccours.ekho.ui.icons.Stop
import com.baccours.ekho.ui.theme.EkhoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions updated, ViewModel can react if needed, 
        // but here we just let the UI recompose and check again on next click
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ekho", fontWeight = FontWeight.Bold) },
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
                        onToggleBypass = { viewModel.setBypassLoopbackProtection(it) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            ServiceControlButton(
                isServiceRunning = uiState.isServiceRunning,
                isStreaming = uiState.isStreaming,
                onToggle = {
                    if (uiState.isServiceRunning) {
                        val intent = Intent(context, AudioService::class.java).apply {
                            action = AudioService.ACTION_STOP
                        }
                        context.startService(intent)
                    } else {
                        val permissions = mutableListOf<String>()
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permissions.add(Manifest.permission.RECORD_AUDIO)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        if (permissions.isNotEmpty()) {
                            launcher.launch(permissions.toTypedArray())
                        } else {
                            val intent = Intent(context, AudioService::class.java)
                            ContextCompat.startForegroundService(context, intent)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            EqualizerSection(
                preset = uiState.preset,
                bandLevels = uiState.bandLevels,
                bandFrequencies = viewModel.bandFrequencies,
                bandLevelRange = viewModel.bandLevelRange,
                onPresetChange = { viewModel.setPreset(it) },
                onBandLevelChange = { bandId, level -> viewModel.updateBandLevel(bandId, level) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                text = "Feedback Loop Risk",
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = if (bypassLoopbackProtection) "Slide left to re-enable safety"
                    else "Slide right to bypass safety",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.5f)
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
    onToggle: () -> Unit
) {
    val buttonColor = when {
        !isServiceRunning -> MaterialTheme.colorScheme.primary
        isStreaming -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    val buttonText = when {
        !isServiceRunning -> "START"
        isStreaming -> "STOP"
        else -> "STANDBY (unsafe)"
    }

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
    preset: String,
    bandLevels: Map<Int, Int>,
    bandFrequencies: List<Int>,
    bandLevelRange: ClosedFloatingPointRange<Float>,
    onPresetChange: (String) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit
) {
    Text(
        text = "Equalizer",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.fillMaxWidth()
    )
    
    Spacer(modifier = Modifier.height(16.dp))

    var expanded by remember { mutableStateOf(false) }
    val presets = listOf(
        SettingsRepository.PRESET_FLAT,
        SettingsRepository.PRESET_VOICE,
        SettingsRepository.PRESET_BOOST
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = preset,
            onValueChange = {},
            readOnly = true,
            label = { Text("Preset") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            presets.forEach { selection ->
                DropdownMenuItem(
                    text = { Text(selection) },
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
        bandLevels.toSortedMap().forEach { (bandId, level) ->
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

@Composable
fun VerticalBandSlider(
    bandId: Int,
    level: Int,
    frequency: Int?,
    valueRange: ClosedFloatingPointRange<Float>,
    onLevelChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${level / 100} dB",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // Vertical Slider using rotation
        Slider(
            value = level.toFloat(),
            onValueChange = { onLevelChange(it.toInt()) },
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
                },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        val freqText = when {
            frequency == null -> "B${bandId + 1}"
            frequency >= 1000 -> "${frequency / 1000} kHz"
            else -> "$frequency Hz"
        }
        Text(
            text = freqText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    EkhoTheme {
        MainScreen()
    }
}
