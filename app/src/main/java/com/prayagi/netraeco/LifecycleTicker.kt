package com.prayagi.netraeco

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * A monotonic tick that advances only while the hosting lifecycle is at least STARTED.
 * In the background the ticker is cancelled, so a per-second UI clock never runs for a
 * user who cannot see it. When the screen returns, the tick restarts and callers
 * recompute from the real clock, so countdowns correct themselves on the next frame
 * instead of replaying missed time. This replaces per-widget `while (true) { delay(...) }`
 * loops that kept recomposing every second with the app in the background.
 */
@Composable
fun rememberVisibleTicker(periodMillis: Long = 1000L): State<Long> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val ticks = remember { mutableLongStateOf(0L) }
    LaunchedEffect(lifecycleOwner, periodMillis) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Re-read wall time immediately on first visibility and every resume.
            ticks.longValue++
            while (true) { delay(periodMillis); ticks.longValue++ }
        }
    }
    return ticks
}
