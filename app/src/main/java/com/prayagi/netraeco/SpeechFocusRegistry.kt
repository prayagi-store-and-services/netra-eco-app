package com.prayagi.netraeco

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the audio-focus handle and finish callback of each spoken line, keyed by utterance id.
 *
 * ConcurrentHashMap rejects null keys and values, and Android may refuse audio focus (the
 * handle is then null). A null handle is therefore stored as an internal marker, so a refused
 * focus can never crash the speaker (Time Speak Out crash, v1.2.35). Pure Kotlin, unit-tested.
 */
class SpeechFocusRegistry {

    /** What a finished line gives back: the focus handle to abandon (null when none) and the callback. */
    class Released(val handle: Any?, val onFinished: (() -> Unit)?)

    private object NoFocus

    private class Entry(val handle: Any, val onFinished: (() -> Unit)?)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun register(id: String, handle: Any?, onFinished: (() -> Unit)?) {
        entries[id] = Entry(handle ?: NoFocus, onFinished)
    }

    /** Removes the line once; returns null when it was already released or never registered. */
    fun release(id: String): Released? {
        val e = entries.remove(id) ?: return null
        return Released(if (e.handle === NoFocus) null else e.handle, e.onFinished)
    }

    fun size(): Int = entries.size
}
