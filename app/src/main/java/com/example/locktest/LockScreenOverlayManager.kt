package com.example.locktest

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers

/**
 * Manages a TYPE_APPLICATION_OVERLAY window that draws **directly over the lock screen**
 * at the WindowManager level — no Activity launch needed, no background-start restrictions,
 * no MIUI/HyperOS interference.
 *
 * Flow:
 *   ACTION_SCREEN_OFF  → show() — overlay is added while screen is off, ready for screen-on
 *   ACTION_USER_PRESENT → hide() — user unlocked, remove overlay
 *   ✕ button / swipe   → hide() — user manually dismissed
 */
object LockScreenOverlayManager {

    private const val TAG = "LockScreenOverlay"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    val isShowing: Boolean get() = overlayView != null

    fun show(context: Context) {
        Log.d(TAG, "LockScreenOverlayManager.show() called.")
        if (overlayView != null) {
            Log.d(TAG, "Overlay is already showing. Ignoring request.")
            return   // already showing — prevent duplicate
        }

        try {
            Log.d(TAG, "Retrieving WindowManager service.")
            val wm = context.applicationContext
                .getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            Log.d(TAG, "Creating ComposeView for overlay.")
            // Create a ComposeView to host our LockScreenOverlay composable
            val composeView = androidx.compose.ui.platform.ComposeView(context).apply {
                setContent {
                    com.example.locktest.ui.theme.LockTestTheme {
                        LockScreenOverlay(onDismiss = { 
                            Log.d(TAG, "onDismiss callback triggered from Compose overlay.")
                            hide() 
                        })
                    }
                }
                
                // Required for ComposeView to work outside an Activity
                setViewTreeLifecycleOwner(ComposeLifecycleOwner)
                setViewTreeSavedStateRegistryOwner(ComposeLifecycleOwner)
                setViewTreeViewModelStoreOwner(ComposeLifecycleOwner)
            }

            overlayView = composeView

            // Listen for user unlock signal to hide overlay
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                LockscreenLifecycleTracker.finishSignal.collect {
                    Log.d(TAG, "finishSignal received. Hiding overlay.")
                    hide()
                }
            }

            // Flags exactly as recommended by the HyperOS article
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            Log.d(TAG, "Adding ComposeView to WindowManager with TYPE_APPLICATION_OVERLAY.")
            wm.addView(composeView, params)
            Log.d(TAG, "Overlay successfully added to WindowManager.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception while adding overlay to WindowManager: ${e.message}", e)
            overlayView = null
            windowManager = null
        }
    }

    fun hide() {
        Log.d(TAG, "LockScreenOverlayManager.hide() called. overlayView=$overlayView")
        overlayView?.let { v ->
            try { 
                windowManager?.removeView(v) 
                Log.d(TAG, "Removed overlayView from WindowManager.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception while removing overlayView: ${e.message}")
            }
        }
        overlayView = null
        windowManager = null
    }
}

/**
 * Provides the necessary architecture components so Jetpack Compose can render
 * outside of an Activity or Fragment (i.e., directly in WindowManager).
 */
object ComposeLifecycleOwner : androidx.lifecycle.LifecycleOwner,
    androidx.savedstate.SavedStateRegistryOwner,
    androidx.lifecycle.ViewModelStoreOwner {

    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateRegistryController = androidx.savedstate.SavedStateRegistryController.create(this)
    private val store = androidx.lifecycle.ViewModelStore()

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
    }

    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: androidx.lifecycle.ViewModelStore get() = store
}
