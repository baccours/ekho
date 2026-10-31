package com.baccours.ekho

import android.content.Context
import com.baccours.ekho.audio.AudioDeviceMonitor
import com.baccours.ekho.data.SettingsRepository
import com.baccours.ekho.service.ServiceState

/**
 * Manual dependency container: one instance of each shared collaborator for the whole process,
 * handed to the service and the ViewModel instead of each constructing its own.
 */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val audioDeviceMonitor = AudioDeviceMonitor(context)
    val serviceState = ServiceState()
}
