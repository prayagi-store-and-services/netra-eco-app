package com.prayagi.netraeco

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pause/restart contract of rememberVisibleTicker: the tick must not advance while the
 * hosting screen is stopped (no off-screen timer work), and it must restart when the screen
 * returns. Callers recompute their displayed time from the real clock on each tick, so a
 * resumed screen shows the corrected time immediately instead of replaying missed seconds.
 */
@RunWith(AndroidJUnit4::class)
class LifecycleTickerTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tickerPausesWhileStoppedAndRestartsOnResume() {
        var observed = 0L
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val tick by rememberVisibleTicker(100L)
            observed = tick
        }
        rule.waitForIdle()
        assertEquals("no tick before the first period elapses", 0L, observed)

        rule.mainClock.advanceTimeBy(350)
        rule.waitForIdle()
        assertTrue("ticker advances while on screen (observed=$observed)", observed >= 2)

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.waitForIdle()
        val stoppedAt = observed
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals("ticker is cancelled while the screen is stopped", stoppedAt, observed)

        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        val resumedAt = observed
        rule.mainClock.advanceTimeBy(350)
        rule.waitForIdle()
        assertTrue("ticker restarts when the screen returns", observed > resumedAt)
    }

    @Test
    fun tickerSurvivesDisposeWithoutLeakingWork() {
        rule.setContent {
            rememberVisibleTicker(50L)
        }
        rule.waitForIdle()
        rule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        // Reaching this point without a crash or leaked-coroutine failure is the assertion.
    }
}
