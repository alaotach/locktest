package com.example.locktest

import android.service.notification.NotificationListenerService
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * NotificationListenerService used as a **process anchor**.
 *
 * When the user grants Notification Access, Android's NotificationManagerService
 * (a system-level service running in system_server) creates and holds a persistent
 * binding to this service. The OS will NOT kill a process that has an active service
 * binding held by the system — the same guarantee that protects foreground services,
 * but WITHOUT requiring a persistent status-bar notification.
 *
 * Result: even after the user swipes the app from recents, the process stays alive,
 * [ScreenOnReceiver] stays registered, and the lockscreen appears on every screen-off.
 *
 * When notification access is granted, [MainActivity] stops [LockScreenService]
 * (the foreground service fallback) since it is no longer needed.
 */
class NotificationListenerServiceImpl : NotificationListenerService() {

    private val screenReceiver = ScreenOnReceiver()
    private var receiverRegistered = false

    companion object {
        private const val TAG = "LockScreenOverlay"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationListenerServiceImpl.onCreate() called. Process anchored by system.")
        registerScreenReceiver()
    }

    override fun onDestroy() {
        Log.d(TAG, "NotificationListenerServiceImpl.onDestroy() called. System released anchor.")
        unregisterScreenReceiver()
        super.onDestroy()
    }

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
}
