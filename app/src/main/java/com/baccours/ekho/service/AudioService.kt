package com.baccours.ekho.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.baccours.ekho.MainActivity
import com.baccours.ekho.audio.AudioProcessor
import com.baccours.ekho.data.SettingsRepository
import com.baccours.ekho.util.AudioDeviceMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class AudioService : Service() {

    private val binder = AudioBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private lateinit var audioProcessor: AudioProcessor
    private lateinit var audioDeviceMonitor: AudioDeviceMonitor
    private lateinit var settingsRepository: SettingsRepository

    private var isStreaming = false

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onCreate() {
        super.onCreate()
        audioProcessor = AudioProcessor(this)
        audioDeviceMonitor = AudioDeviceMonitor(this)
        settingsRepository = SettingsRepository(this)
        
        createNotificationChannel()
        observeSettingsAndDevices()
        ServiceState.setRunning(true)
    }

    private fun observeSettingsAndDevices() {
        // Observe both allowSpeaker and headphone status to decide if we should stop
        serviceScope.launch {
            combine(
                settingsRepository.allowSpeakerFlow,
                audioDeviceMonitor.headphoneStatusFlow
            ) { allowSpeaker, isHeadphoneConnected ->
                allowSpeaker to isHeadphoneConnected
            }.collectLatest { (allowSpeaker, isHeadphoneConnected) ->
                if (isStreaming && !isHeadphoneConnected && !allowSpeaker) {
                    Log.d(TAG, "Stopping stream: No headphones and speaker not allowed")
                    stopStreaming()
                }
            }
        }

        // Observe band levels and apply to processor
        serviceScope.launch {
            settingsRepository.presetFlow.collectLatest { _ ->
                val levels = settingsRepository.getAllBandLevels(5) // Assuming 5 bands
                audioProcessor.applyBandLevels(levels)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceState.setRunning(true)
        if (intent?.action == ACTION_STOP) {
            stopStreaming()
            stopForeground(STOP_FOREGROUND_REMOVE)
            ServiceState.setRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (startForegroundService()) {
            startStreaming()
        }
        return START_STICKY
    }

    private fun startForegroundService(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start foreground service: RECORD_AUDIO permission missing")
            return false
        }

        val stopIntent = Intent(this, AudioService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ekho is active")
            .setContentText("Microphone pass-through is running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @SuppressLint("InlinedApi")
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopStreaming()
            stopSelf()
            false
        }
    }

    private fun startStreaming() {
        if (isStreaming) return
        
        val isHeadphoneConnected = audioDeviceMonitor.isHeadphoneConnected()
        serviceScope.launch {
            val allowSpeaker = settingsRepository.allowSpeakerFlow.first()
            if (!isHeadphoneConnected && !allowSpeaker) {
                Log.w(TAG, "Headphones not connected and speaker output not allowed. Not starting stream.")
                return@launch
            }

            isStreaming = true
            audioProcessor.start {
                // Apply current levels once equalizer is ready
                val levels = settingsRepository.getAllBandLevels(5)
                audioProcessor.applyBandLevels(levels)
            }
            isStreaming = false
        }
    }

    private fun stopStreaming() {
        isStreaming = false
        audioProcessor.stop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Ekho Audio Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopStreaming()
        ServiceState.setRunning(false)
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AudioService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "EkhoChannel"
        const val ACTION_STOP = "com.baccours.ekho.STOP"
    }
}
