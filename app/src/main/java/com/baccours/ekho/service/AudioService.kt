package com.baccours.ekho.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.baccours.ekho.MainActivity
import com.baccours.ekho.R
import com.baccours.ekho.audio.AudioDeviceMonitor
import com.baccours.ekho.audio.AudioProcessor
import com.baccours.ekho.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

class AudioService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var audioProcessor: AudioProcessor
    private lateinit var audioDeviceMonitor: AudioDeviceMonitor
    private lateinit var settingsRepository: SettingsRepository

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "EkhoChannel"
        const val ACTION_STOP = "STOP"
    }

    // -------------------------------------------------------------------------
    // Service Lifecycle
    // -------------------------------------------------------------------------
    override fun onCreate() {
        super.onCreate()
        Timber.d("AudioService created")

        audioProcessor = AudioProcessor()
        audioDeviceMonitor = AudioDeviceMonitor(this)
        settingsRepository = SettingsRepository(this)

        createNotificationChannel()
        ServiceState.setRunning(true)

        observeStreamingConditions()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Timber.e("RECORD_AUDIO permission missing. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(isStreaming = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @SuppressLint("InlinedApi")
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Timber.d("AudioService destroying")
        serviceScope.cancel()   // cancels the streaming loop and all observers
        audioProcessor.stop()   // Hardware safety: ensure processor is stopped
        ServiceState.reset()

        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)

        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Reactive Observation
    // -------------------------------------------------------------------------
    private fun observeStreamingConditions() {
        serviceScope.launch {
            combine(
                settingsRepository.bypassLoopbackProtectionFlow,
                audioDeviceMonitor.loopbackSafeStatusFlow
            ) { bypass, safe -> bypass || safe }
                .distinctUntilChanged()
                .collectLatest { allowed ->
                    if (allowed) {
                        runStreamingLoop()
                    } else {
                        ServiceState.setStreaming(false)
                        updateNotification(isStreaming = false)
                    }
                }
        }

        serviceScope.launch {
            settingsRepository.presetFlow.collectLatest {
                if (ServiceState.state.value.isStreaming) {
                    val levels = settingsRepository.getAllBandLevels()
                    audioProcessor.applyBandLevels(levels)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Streaming
    // -------------------------------------------------------------------------
    /**
     * Because this is called via [collectLatest], this entire function is canceled
     * automatically if 'allowed' changes to false or the service is destroyed.
     * And the 'finally' block is guaranteed to run when the coroutine is canceled.
     */
    private suspend fun runStreamingLoop() {
        try {
            Timber.d("Starting audio streaming")
            ServiceState.setStreaming(true)
            updateNotification(isStreaming = true)

            audioProcessor.start {
                val levels = settingsRepository.getAllBandLevels()
                audioProcessor.applyBandLevels(levels)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during audio streaming")
        } finally {
            audioProcessor.stop()
            ServiceState.setStreaming(false)
            updateNotification(isStreaming = false)
            Timber.d("Streaming hardware released")
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------
    private fun buildNotification(isStreaming: Boolean): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isStreaming) "Ekho is active" else "Ekho is in standby")
            .setContentText(if (isStreaming) "Microphone pass-through is running" else "Waiting for safe audio output")
            .setSmallIcon(R.drawable.ic_megaphone)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_stop_circle, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(isStreaming: Boolean) {
        if (!ServiceState.state.value.isRunning) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(isStreaming))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ekho Audio Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}