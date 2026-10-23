package com.example.locktest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service fallback — used only when the user has NOT granted
 * Notification Listener access. When notification access IS granted,
 * [NotificationListenerServiceImpl] acts as the process anchor instead,
 * and [MainActivity] will stop this service to remove the persistent notification.
 *
 * Listens for ACTION_SCREEN_OFF / ACTION_SCREEN_ON / ACTION_USER_PRESENT
 * and delegates handling to [ScreenOnReceiver].
 */
class LockScreenService : Service() {

    private val screenReceiver = ScreenOnReceiver()
    private var receiverRegistered = false

    companion object {
        const val SERVICE_CHANNEL_ID = "lock_screen_service_channel"
        const val ALERT_CHANNEL_ID   = "lock_screen_alert_channel"
        const val NOTIFICATION_ID    = 1001
        private const val TAG = "LockScreenOverlay"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LockScreenService.onCreate() called. Foreground service starting.")
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildServiceNotification())
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "LockScreenService.onDestroy() called. Foreground service stopping.")
        super.onDestroy()
        unregisterScreenReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun registerScreenReceiver() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, filter)
            receiverRegistered = true
        }
    }

    private fun unregisterScreenReceiver() {
        if (receiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (_: IllegalArgumentException) { }
            receiverRegistered = false
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Lock Screen Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the lock-screen overlay active"
                setShowBadge(false)
            }

            // High-importance required for full-screen intent fallback to fire
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Lock Screen Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows overlay when screen turns on while locked"
                setShowBadge(false)
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildServiceNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("Lock Screen Overlay")
            .setContentText("Active – grant Notification Access to remove this notification")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
