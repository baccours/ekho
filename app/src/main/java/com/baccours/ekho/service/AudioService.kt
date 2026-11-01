package com.baccours.ekho.service

import android.Manifest
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
import com.baccours.ekho.EkhoApplication
import com.baccours.ekho.MainActivity
import com.baccours.ekho.R
import com.baccours.ekho.audio.AudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/** Thin Android host: owns the foreground notification and runs a [StreamingController]. */
class AudioService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var audioProcessor: AudioProcessor
    private lateinit var serviceState: ServiceState
    private lateinit var controller: StreamingController

    private var isObserving = false

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

        val container = (application as EkhoApplication).container
        serviceState = container.serviceState
        audioProcessor = AudioProcessor(AudioProcessor.nativeSampleRate(this))
        controller = StreamingController(
            engine = audioProcessor,
            devices = container.audioDeviceMonitor,
            bypassProtection = container.settingsRepository.bypassLoopbackProtectionFlow,
            bandLevels = container.settingsRepository.bandLevelsFlow,
            onStatusChanged = ::onStatusChanged
        )

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Enter the foreground before anything else: after startForegroundService() the system
        // requires it within seconds, whatever else we decide to do.
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Timber.e("RECORD_AUDIO permission missing. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        serviceState.setRunning(true)
        if (!isObserving) {
            isObserving = true
            serviceScope.launch { controller.run() }
        }

        // Never restart silently: reopening the microphone must be a user action.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Timber.d("AudioService destroying")
        serviceScope.cancel()   // cancels the controller and with it the streaming loop
        audioProcessor.close()  // Hardware safety: stop the processor and free the audio thread
        serviceState.reset()

        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)

        super.onDestroy()
    }

    private fun enterForeground(): Boolean {
        val notification = buildNotification(StreamingStatus.Waiting)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Unable to enter the foreground")
            false
        }
    }

    private fun onStatusChanged(status: StreamingStatus) {
        serviceState.setStreamingState(
            isStreaming = status == StreamingStatus.Streaming,
            hasError = status == StreamingStatus.Error
        )
        updateNotification(status)
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------
    private fun buildNotification(status: StreamingStatus): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Reuse the running activity instead of stacking a new one on every tap.
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val (title, text) = when (status) {
            StreamingStatus.Streaming -> R.string.notification_title_active to R.string.notification_text_active
            StreamingStatus.Waiting -> R.string.notification_title_standby to R.string.notification_text_standby
            StreamingStatus.Error -> R.string.notification_title_error to R.string.notification_text_error
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(title))
            .setContentText(getString(text))
            .setSmallIcon(R.drawable.ic_megaphone)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_stop_circle, getString(R.string.notification_action_stop), stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: StreamingStatus) {
        if (!serviceState.state.value.isRunning) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
