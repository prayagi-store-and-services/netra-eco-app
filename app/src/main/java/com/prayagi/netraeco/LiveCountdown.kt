package com.prayagi.netraeco

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight

/**
 * The ticking countdown line. It owns its own one-second ticker, so only this one line redraws each second
 * and the rest of the card (and the scrolling list around it) stays untouched. The ticker pauses while the
 * screen is in the background (rememberVisibleTicker) and the countdown corrects itself from the real clock
 * on resume, so no off-screen recompositions and no replayed time.
 */
@Composable
fun LiveCountdown(etaMillis: Long, withApprox: Boolean) {
    val tick by rememberVisibleTicker()
    val now = remember(tick) { System.currentTimeMillis() }
    val text = if (etaMillis > now) (if (withApprox) "Approximate ETA: " else "") + Roadmap.countdown(etaMillis - now) else Roadmap.countdown(0L)
    Text(text, fontWeight = FontWeight.Medium)
}
