package com.example.locktest

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Handles screen-state broadcasts.
 *
 *  ACTION_SCREEN_OFF   — primary: launch LockScreenActivity while screen is off so it is
 *                        already in the stack when the keyguard appears on screen-on.
 *                        setShowWhenLocked(true) on the Activity makes it surface above
 *                        the keyguard automatically.
 *
 *  ACTION_SCREEN_ON    — fallback: in case the activity was not launched on SCREEN_OFF
 *                        (e.g. service just started).
 *
 *  ACTION_USER_PRESENT — user dismissed keyguard: send finish signal to the Activity.
 *
 * Requires SYSTEM_ALERT_WINDOW ("Display over other apps") to start Activity from background
 * on Android 10+. Falls back to full-screen intent notification if not granted.
 */
class ScreenOnReceiver : BroadcastReceiver() {

    companion object {
        const val OVERLAY_NOTIFICATION_ID = 2001
        private const val TAG = "LockScreenOverlay"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "ScreenOnReceiver received action: ${intent.action}")
        when (intent.action) {

            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Handling ACTION_SCREEN_OFF")
                if (Settings.canDrawOverlays(context) && !LockScreenActivity.isShowing) {
                    val manufacturer = android.os.Build.MANUFACTURER.lowercase()
                    val isXiaomi = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
                    
                    if (isXiaomi) {
                        Log.d(TAG, "Skipping ACTION_SCREEN_OFF launch for Xiaomi. Waiting for SCREEN_ON to bypass Z-order issues.")
                    } else {
                        Log.d(TAG, "Can draw overlays and activity is not showing. Calling launchActivity.")
                        launchActivity(context)
                    }
                } else {
                    Log.d(TAG, "Skipping launch: canDrawOverlays=${Settings.canDrawOverlays(context)}, isShowing=${LockScreenActivity.isShowing}")
                }
            }

            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Handling ACTION_SCREEN_ON")
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (km.isKeyguardLocked && !LockScreenActivity.isShowing) {
                    if (Settings.canDrawOverlays(context)) {
                        Log.d(TAG, "Keyguard locked, overlays allowed. Delaying launchActivity to out-wait HyperOS Z-ordering.")
                        Handler(Looper.getMainLooper()).postDelayed({
                            Log.d(TAG, "Executing delayed launchActivity.")
                            launchActivity(context)
                        }, 500)
                    } else {
                        Log.d(TAG, "Keyguard locked but no overlay permission. Using full-screen intent fallback.")
                        // Fallback: full-screen intent notification
                        showViaFullScreenIntent(context)
                    }
                } else {
                    Log.d(TAG, "Skipping ACTION_SCREEN_ON: isKeyguardLocked=${km.isKeyguardLocked}, isShowing=${LockScreenActivity.isShowing}")
                }
            }

            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User unlocked screen. Sending finish signal.")
                LockscreenLifecycleTracker.sendFinish()
                LockScreenOverlayManager.hide()
            }
        }
    }

    private fun launchActivity(context: Context) {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val isXiaomi = manufacturer.contains("xiaomi") || 
                       manufacturer.contains("redmi") || 
                       manufacturer.contains("poco")

        Log.d(TAG, "launchActivity called. isXiaomi: $isXiaomi")

        if (isXiaomi) {
            Log.d(TAG, "Using LockScreenOverlayManager (WindowManager method) for Xiaomi on SCREEN_ON.")
            LockScreenOverlayManager.show(context)
        } else {
            Log.d(TAG, "Using standard startActivity for non-Xiaomi device.")
            context.startActivity(
                Intent(context, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
            )
        }
    }

    private fun showViaFullScreenIntent(context: Context) {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, LockScreenService.ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Lock Screen Overlay")
            .setFullScreenIntent(pendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(OVERLAY_NOTIFICATION_ID, notification)
    }
}
