package com.prayagi.netraeco

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay

/**
 * The ticking countdown line. It owns its own one-second timer, so only this one line redraws each second
 * and the rest of the card (and the scrolling list around it) stays untouched.
 */
@Composable
fun LiveCountdown(etaMillis: Long, withApprox: Boolean) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val text = if (etaMillis > now) (if (withApprox) "Approximate ETA: " else "") + Roadmap.countdown(etaMillis - now) else Roadmap.countdown(0L)
    Text(text, fontWeight = FontWeight.Medium)
}
