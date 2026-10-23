package com.example.locktest

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton signal bus used to tell [LockScreenActivity] to finish itself
 * without holding a direct reference to the Activity (which would leak memory).
 *
 * Emitted by [ScreenOnReceiver] when ACTION_USER_PRESENT fires (user unlocked).
 * Collected by [LockScreenActivity] via lifecycleScope.
 */
object LockscreenLifecycleTracker {
    private val _finishSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finishSignal: SharedFlow<Unit> = _finishSignal.asSharedFlow()

    fun sendFinish() {
        _finishSignal.tryEmit(Unit)
    }
}
