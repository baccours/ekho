package com.baccours.ekho.service

import android.Manifest
import android.annotation.SuppressLint
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
import com.baccours.ekho.audio.AudioDeviceMonitor
import com.baccours.ekho.audio.AudioProcessor
import com.baccours.ekho.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class AudioService : Service() {

    // --- Lifecycle & Coroutines ---
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    // --- Dependencies ---
    private lateinit var audioProcessor: AudioProcessor
    private lateinit var audioDeviceMonitor: AudioDeviceMonitor
    private lateinit var settingsRepository: SettingsRepository

    // --- Streaming State ---
    private val streamingMutex = Mutex()
    private var streamingJob: Job? = null
    private enum class StreamState { IDLE, STARTING, RUNNING, STOPPING }
    private var streamState = StreamState.IDLE

    // --- Constants ---
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
        audioProcessor = AudioProcessor()
        audioDeviceMonitor = AudioDeviceMonitor(this)
        settingsRepository = SettingsRepository(this)

        createNotificationChannel()
        observeSettingsAndDevices()
        ServiceState.setRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handleStopAction()
            return START_NOT_STICKY
        }

        if (startForegroundService()) {
            serviceScope.launch { startStreamingIfAllowed() }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.launch { stopStreaming() }
        ServiceState.setRunning(false)
        serviceJob.cancel()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Foreground Notification
    // -------------------------------------------------------------------------

    private fun startForegroundService(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Timber.e("Cannot start foreground service: RECORD_AUDIO permission missing")
            stopSelf()
            return false
        }

        val stopPendingIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ekho is active")
            .setContentText("Microphone pass-through is running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppPendingIntent)
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
            Timber.e(e, "Failed to start foreground service")
            stopSelf()
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ekho Audio Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // -------------------------------------------------------------------------
    // Streaming
    // -------------------------------------------------------------------------

    private suspend fun startStreamingIfAllowed() {
        val canStream = canStream()
        if (!canStream) {
            Timber.w("Conditions not met for streaming. Stopping service.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        startStreaming()
    }

    private suspend fun startStreaming() {
        streamingMutex.withLock {
            if (streamState != StreamState.IDLE) {
                Timber.d("startStreaming() ignored — current state: $streamState")
                return
            }
            streamState = StreamState.STARTING
        }

        streamingJob = serviceScope.launch {
            try {
                streamingMutex.withLock { streamState = StreamState.RUNNING }
                Timber.d("Streaming started")

                audioProcessor.start {
                    val levels = settingsRepository.getAllBandLevels()
                    audioProcessor.applyBandLevels(levels)
                }
            } catch (e: Exception) {
                Timber.e(e, "Streaming error")
            } finally {
                streamingMutex.withLock { streamState = StreamState.IDLE }
                Timber.d("Streaming ended")
            }
        }
    }

    private suspend fun stopStreaming() {
        streamingMutex.withLock {
            if (streamState == StreamState.IDLE || streamState == StreamState.STOPPING) return
            streamState = StreamState.STOPPING
        }

        audioProcessor.stop()
        streamingJob?.join()
        streamingJob = null

        streamingMutex.withLock { streamState = StreamState.IDLE }
        Timber.d("Streaming stopped")
    }

    // -------------------------------------------------------------------------
    // Reactive Observation
    // -------------------------------------------------------------------------

    private fun observeSettingsAndDevices() {
        // React to headphone/speaker changes while streaming
        serviceScope.launch {
            combine(
                settingsRepository.bypassLoopbackProtectionFlow,
                audioDeviceMonitor.loopbackSafeStatusFlow
            ) { bypassLoopbackProtection, isLoopbackSafe ->
                bypassLoopbackProtection  to isLoopbackSafe
            }.collect { (bypassLoopbackProtection, isLoopbackSafe) ->
                val isRunning = streamingMutex.withLock { streamState == StreamState.RUNNING }
                if (isRunning && !isLoopbackSafe && !bypassLoopbackProtection) {
                    Timber.d("Stopping stream: unsafe output not allowed")
                    stopStreaming()
                }
            }
        }

        // Apply equalizer band levels whenever the preset changes
        serviceScope.launch {
            settingsRepository.presetFlow.collectLatest {
                val levels = settingsRepository.getAllBandLevels()
                audioProcessor.applyBandLevels(levels)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun canStream(): Boolean {
        val isLoopbackSafe = audioDeviceMonitor.isLoopbackSafe()
        val bypassLoopbackProtection = settingsRepository.bypassLoopbackProtectionFlow.first()
        return isLoopbackSafe || bypassLoopbackProtection
    }

    private fun handleStopAction() {
        serviceScope.launch {
            stopStreaming()
            stopForeground(STOP_FOREGROUND_REMOVE)
            ServiceState.setRunning(false)
            stopSelf()
        }
    }
}
