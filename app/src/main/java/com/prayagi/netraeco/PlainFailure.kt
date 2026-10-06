package com.prayagi.netraeco

import java.io.IOException

/**
 * Turns an exception into one plain sentence for the user.
 * Our own messages (IllegalStateException from Net.kt) are already plain and are kept.
 * Network errors and Android refusals get a fixed sentence instead of the raw system text
 * (for example "Unable to resolve host ..."). Anything else uses the given fallback.
 */
fun plainFailure(e: Throwable, fallback: String): String = when {
    e is IllegalStateException && !e.message.isNullOrBlank() -> e.message!!
    e is IOException -> "No internet, or the server did not answer. Check your connection and try again."
    e is SecurityException || e is android.content.ActivityNotFoundException ->
        "Android did not allow this. Check the Android settings for this app."
    else -> fallback
}
