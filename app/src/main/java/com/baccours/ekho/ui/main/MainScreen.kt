package com.baccours.ekho.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baccours.ekho.R
import com.baccours.ekho.data.AppSettings
import com.baccours.ekho.data.EqPreset
import com.baccours.ekho.service.AudioService
import com.baccours.ekho.service.ServiceUiState
import com.baccours.ekho.ui.components.SwipeToggle
import com.baccours.ekho.ui.icons.Icons
import com.baccours.ekho.ui.icons.Play
import com.baccours.ekho.ui.icons.Stop
import com.baccours.ekho.ui.theme.EkhoTheme

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var micPermissionDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Only the microphone is required. Notifications are optional: the foreground service
        // runs without them, so a denial there must never block the app.
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true
        micPermissionDenied = !micGranted
        if (micGranted) AudioService.start(context)
    }

    // The permission can be granted in system Settings; clear the banner when we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (context.isGranted(Manifest.permission.RECORD_AUDIO)) micPermissionDenied = false
    }

    // Wait for the saved settings so the safety card doesn't pop in after launch.
    if (uiState.isLoaded) {
        MainContent(
            uiState = uiState,
            micPermissionDenied = micPermissionDenied,
            onToggleService = {
                if (uiState.service.isRunning) {
                    AudioService.stop(context)
                } else if (context.isGranted(Manifest.permission.RECORD_AUDIO)) {
                    micPermissionDenied = false
                    AudioService.start(context)
                } else {
                    val permissions = buildList {
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !context.isGranted(Manifest.permission.POST_NOTIFICATIONS)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    uiState: MainUiState,
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
                visible = !uiState.isLoopbackSafe || uiState.settings.bypassLoopbackProtection,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    SafetyStatusCard(
                        bypassLoopbackProtection = uiState.settings.bypassLoopbackProtection,
                        onToggleBypass = onToggleBypass
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            ServiceControlButton(
                isServiceRunning = uiState.service.isRunning,
                isStreaming = uiState.service.isStreaming,
                hasError = uiState.service.hasError,
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
                preset = uiState.settings.preset,
                bandLevels = uiState.settings.bandLevels,
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
                text = stringResource(
                    if (bypassLoopbackProtection) R.string.safety_title_bypassed
                    else R.string.safety_title
                ),
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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    EkhoTheme {
        MainContent(
            uiState = MainUiState(
                settings = AppSettings(
                    preset = EqPreset.CUSTOM,
                    bandLevels = listOf(200, 0, -300, 100, 0)
                ),
                service = ServiceUiState(isRunning = true),
                isLoopbackSafe = false
            ),
            micPermissionDenied = false,
            onToggleService = {},
            onOpenAppSettings = {},
            onToggleBypass = {},
            onPresetChange = {},
            onBandLevelChange = { _, _ -> }
        )
    }
}
