package com.prayagi.netraeco

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * Fires on each scheduled half-hour alarm and after a phone restart. Speaks the time when the
 * rules allow it (not in the 11 PM-6 AM silent window, ringer not silent/vibrate, no call),
 * honestly records what happened, then schedules the next slot.
 */
class TimeSpeakReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!TimeSpeakOut.isEnabled(context)) return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                TimeSpeakOut.scheduleNext(context)
            }
            TimeSpeakOut.ACTION_SPEAK -> {
                val now = System.currentTimeMillis()
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val ringer = try { am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL } catch (_: Exception) { AudioManager.RINGER_MODE_NORMAL }
                val skip = TimeSpeakOut.skipReason(now, ringer)
                if (skip == null) {
                    // The line for this slot was computed when the alarm was scheduled, so it
                    // starts speaking immediately - nothing to fetch or work out first.
                    val slotAt = intent.getLongExtra(TimeSpeakOut.EXTRA_SLOT_AT, now)
                    val hi = intent.getStringExtra(TimeSpeakOut.EXTRA_LINE_HI)
                        ?: TimeSpeaker.hindiTime(slotAt)
                    val en = intent.getStringExtra(TimeSpeakOut.EXTRA_LINE_EN)
                        ?: TimeSpeaker.englishTime(slotAt)
                    // Keep the process alive until the line finishes (or the watchdog ends it).
                    val pending = goAsync()
                    val accepted = TimeSpeaker.get(context).speakSlot(hi, en) { pending.finish() }
                    if (accepted) {
                        TimeSpeakOut.markFired(context, spokeAt = slotAt, skipReason = null)
                    } else {
                        pending.finish()
                        TimeSpeakOut.markFired(context, spokeAt = null, skipReason = "Voice engine Unavailable on this phone")
                    }
                } else {
                    TimeSpeakOut.markFired(context, spokeAt = null, skipReason = skip)
                }
                // Always schedule the next slot, spoken or skipped.
                TimeSpeakOut.scheduleNext(context, now)
            }
        }
    }
}
