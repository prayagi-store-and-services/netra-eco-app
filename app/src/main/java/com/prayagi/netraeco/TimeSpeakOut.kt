package com.prayagi.netraeco

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import java.util.Calendar

/**
 * Time Speak Out: speaks the time every 30 minutes, on the hour and half hour.
 * Silent from 11 PM to 6 AM, and follows the phone ringer (stays quiet on silent or vibrate).
 * All state lives in one private preferences file on the phone; nothing is sent anywhere.
 */
object TimeSpeakOut {
    const val ACTION_SPEAK = "com.prayagi.netraeco.action.TIME_SPEAK"
    private const val PREFS = "time_speak_out"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_SPOKE = "last_spoke_at"
    private const val KEY_LAST_SKIP = "last_skip_reason"

    // Silent window: 11 PM (23:00) to 6 AM (06:00) local time. Nothing speaks inside it.
    const val SILENT_START_HOUR = 23
    const val SILENT_END_HOUR = 6

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // On by default: Deepak asked for this feature. The switch in the app turns it off any time.
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        if (on) scheduleNext(context) else cancel(context)
    }

    fun lastSpokeAt(context: Context): Long = prefs(context).getLong(KEY_LAST_SPOKE, 0L)
    fun lastSkipReason(context: Context): String? = prefs(context).getString(KEY_LAST_SKIP, null)

    internal fun markFired(context: Context, spokeAt: Long?, skipReason: String?) {
        val e = prefs(context).edit()
        if (spokeAt != null) e.putLong(KEY_LAST_SPOKE, spokeAt)
        if (skipReason != null) e.putString(KEY_LAST_SKIP, skipReason) else e.remove(KEY_LAST_SKIP)
        e.apply()
    }

    /** True when the given moment is inside the silent window (23:00-06:00 local). */
    fun isSilentAt(nowMillis: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour >= SILENT_START_HOUR || hour < SILENT_END_HOUR
    }

    /**
     * The next announcement slot strictly after [from]: the next :00 or :30 boundary,
     * moved past the silent window when it lands inside it. Pure, unit-tested.
     */
    fun nextSlotMillis(from: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.get(Calendar.MINUTE) < 30) {
            cal.set(Calendar.MINUTE, 30)
        } else {
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, 0)
        }
        var guard = 0
        while (isSilentAt(cal.timeInMillis) && guard < 48) {
            cal.add(Calendar.MINUTE, 30)
            guard++
        }
        return cal.timeInMillis
    }

    /**
     * Whether an announcement may speak right now. Returns null when speaking is allowed,
     * otherwise the honest reason it was skipped. Pure, unit-tested.
     */
    fun skipReason(nowMillis: Long, ringerMode: Int): String? {
        if (isSilentAt(nowMillis)) return "Silent hours (11 PM to 6 AM)"
        if (ringerMode != AudioManager.RINGER_MODE_NORMAL) return "Phone on silent or vibrate"
        return null
    }

    /** Exact alarms need Android's OK on Android 12+. Below that they always work. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return try { am.canScheduleExactAlarms() } catch (_: Exception) { false }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimeSpeakReceiver::class.java).setAction(ACTION_SPEAK)
        return PendingIntent.getBroadcast(
            context, 7331, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Schedules the next single alarm and returns when it will fire, or null when disabled.
     * Uses an exact alarm when Android allows it; otherwise an inexact one (the card says so).
     */
    fun scheduleNext(context: Context, from: Long = System.currentTimeMillis()): Long? {
        if (!isEnabled(context)) return null
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        val at = nextSlotMillis(from)
        val pi = pendingIntent(context)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            // Android took the exact-alarm permission away between the check and the call.
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (_: Exception) { return null }
        } catch (_: Exception) {
            return null
        }
        return at
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try { am.cancel(pendingIntent(context)) } catch (_: Exception) { }
    }
}
