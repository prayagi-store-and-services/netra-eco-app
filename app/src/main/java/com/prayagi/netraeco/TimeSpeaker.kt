package com.prayagi.netraeco

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Speaks the time. Follows the ecosystem rule (Deepak, 19 Oct 2026): while the app speaks it
 * holds transient audio focus, so other media pauses (not mutes) and resumes afterwards.
 * A watchdog releases the focus even if the speech callbacks never arrive.
 * Everything runs on the phone; no recording, no network, nothing stored.
 */
class TimeSpeaker private constructor(private val appContext: Context) {

    companion object {
        @Volatile private var instance: TimeSpeaker? = null
        fun get(context: Context): TimeSpeaker =
            instance ?: synchronized(this) {
                instance ?: TimeSpeaker(context.applicationContext).also { instance = it }
            }

        /** "The time is 9:30 PM." - pure, unit-tested. */
        fun englishTime(nowMillis: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            var hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val ampm = if (hour < 12) "AM" else "PM"
            hour %= 12
            if (hour == 0) hour = 12
            return "The time is " + hour + ":" + "%02d".format(minute) + " " + ampm + "."
        }

        private fun hindiPartOfDay(hour: Int): String = when {
            hour in 4..11 -> "सुबह के"
            hour in 12..16 -> "दोपहर के"
            hour in 17..19 -> "शाम के"
            else -> "रात के"
        }

        /** "समय है, रात के 9 बजकर 30 मिनट।" - pure, unit-tested. */
        fun hindiTime(nowMillis: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            var hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val part = hindiPartOfDay(hour)
            hour %= 12
            if (hour == 0) hour = 12
            val hm = if (minute == 0) "$hour बजे" else "$hour बजकर $minute मिनट"
            return "समय है, $part $hm।"
        }
    }

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var hindi = false
    private val pendingFocus = ConcurrentHashMap<String, Any?>()

    init {
        try {
            tts = TextToSpeech(appContext) { status -> onInit(status) }
        } catch (_: Exception) {
            ready = false
        }
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) { ready = false; return }
        val engine = tts ?: return
        // Prefer Hindi; when the phone has no Hindi voice, honestly fall back to English.
        val hi = engine.setLanguage(Locale("hi", "IN"))
        if (hi != TextToSpeech.LANG_MISSING_DATA && hi != TextToSpeech.LANG_NOT_SUPPORTED) {
            hindi = true
        } else {
            val en = engine.setLanguage(Locale.US)
            if (en == TextToSpeech.LANG_MISSING_DATA || en == TextToSpeech.LANG_NOT_SUPPORTED) {
                ready = false
                return
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) { utteranceId?.let { releaseFocusFor(it) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { utteranceId?.let { releaseFocusFor(it) } }
        })
        ready = true
    }

    /** True once a usable voice is set up; false means speech is Unavailable on this phone. */
    fun isReady(): Boolean = ready

    /**
     * Speaks the time line for [nowMillis]. When [isTest] the line is clearly labelled as a test.
     * [onFinished] runs when the line is done, failed, or could not start.
     */
    fun speakTime(nowMillis: Long, isTest: Boolean, onFinished: (() -> Unit)? = null) {
        val line = if (hindi) hindiTime(nowMillis) else englishTime(nowMillis)
        val text = if (isTest) {
            if (hindi) "परीक्षण घोषणा। $line" else "Test announcement. $line"
        } else line
        speak(text, onFinished)
    }

    private fun speak(text: String, onFinished: (() -> Unit)?) {
        val engine = tts
        if (!ready || engine == null) { onFinished?.invoke(); return }
        // Never speak into a phone call.
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null && (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION)) {
                onFinished?.invoke(); return
            }
        } catch (_: Exception) { }
        val id = "TimeSpeak-" + System.nanoTime()
        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        val focus = requestSpeechFocus()
        pendingFocus[id] = focus
        if (onFinished != null) finishedCallbacks[id] = onFinished
        // Watchdog: focus is released and the finisher runs even if the speech callbacks never arrive.
        Handler(Looper.getMainLooper()).postDelayed({ releaseFocusFor(id) }, 60_000L)
        try {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        } catch (_: Exception) {
            releaseFocusFor(id)
        }
    }

    private val finishedCallbacks = ConcurrentHashMap<String, () -> Unit>()

    private fun requestSpeechFocus(): Any? {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) req else null
            } else {
                @Suppress("DEPRECATION")
                if (am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) Any() else null
            }
        } catch (_: Exception) { null }
    }

    private fun releaseFocusFor(id: String) {
        val cb = finishedCallbacks.remove(id)
        val handle = pendingFocus.remove(id)
        if (handle != null) {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && handle is AudioFocusRequest) {
                    am?.abandonAudioFocusRequest(handle)
                } else {
                    @Suppress("DEPRECATION")
                    am?.abandonAudioFocus(null)
                }
            } catch (_: Exception) { }
        }
        cb?.invoke()
    }
}
