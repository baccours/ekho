package com.baccours.ekho

import android.content.Context
import com.baccours.ekho.audio.AudioDeviceMonitor
import com.baccours.ekho.data.SettingsRepository
import com.baccours.ekho.service.ServiceState

/** Manual dependency container for process-wide shared instances. */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val audioDeviceMonitor = AudioDeviceMonitor(context)
    val serviceState = ServiceState()
}
