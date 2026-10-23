package com.example.locktest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Starts [LockScreenService] on device boot so the lock-screen overlay
 * is active from the very first screen-on, before the user opens the app.
 *
 * LOCKED_BOOT_COMPLETED fires before the user's first post-reboot unlock
 * (Direct Boot mode), ensuring we are active immediately after a reboot.
 * BOOT_COMPLETED fires after the user's first unlock — serves as a fallback.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val serviceIntent = Intent(context, LockScreenService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
