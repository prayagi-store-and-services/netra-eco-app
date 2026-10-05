package com.prayagi.netraeco

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * Decides which app name is shown. Until the cutoff the name stays as it is. After the cutoff the app shows the new
 * name only when BOTH are true: the server clock (the HTTP Date header of the site, never the phone clock) is past the
 * cutoff, and the site publishes a name file. The new name is not stored in this app, so nothing in the app or its
 * source reveals it early. With no network the old name stays. Once switched, the name is kept.
 */
object NameGate {
    const val OLD_NAME = "Netra Eco"
    /** 2026-10-11 00:00 India time, in milliseconds since 1970. */
    const val CUTOFF_MS = 1791657000000L
    const val NAME_URL = "https://prayagi-store-and-services.github.io/netra-eco/name.json"
    private const val PREFS = "eco_name_gate"
    private const val KEY = "name"

    /** Pure rule. serverNowMs is null or 0 when the server time is unknown: then nothing changes. */
    fun decide(serverNowMs: Long?, remoteName: String?, saved: String?): String {
        if (!saved.isNullOrBlank()) return saved
        if (serverNowMs == null || serverNowMs <= 0L || serverNowMs < CUTOFF_MS) return OLD_NAME
        return validName(remoteName) ?: OLD_NAME
    }

    fun validName(raw: String?): String? {
        val n = raw?.trim() ?: return null
        if (n.length !in 3..40) return null
        if (n.any { it.isISOControl() || it == '<' || it == '>' }) return null
        return n
    }

    private val NAME_FIELD = Regex("\"name\"\\s*:\\s*\"([^\"\\\\]*)\"")

    /** Reads the "name" text from the small name.json file (plain text match, no JSON library needed). */
    fun parseName(json: String?): String? {
        if (json == null) return null
        return validName(NAME_FIELD.find(json)?.groupValues?.get(1))
    }

    fun displayName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.isNotBlank() } ?: OLD_NAME

    /** One request per call (the app calls it once per launch). Blocking: run it off the main thread. */
    fun refresh(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getString(KEY, null).isNullOrBlank()) return
        try {
            val c = URL(NAME_URL + "?_=" + System.currentTimeMillis()).openConnection() as HttpURLConnection
            c.useCaches = false
            c.setRequestProperty("Cache-Control", "no-cache")
            c.connectTimeout = 10000
            c.readTimeout = 15000
            val serverNow = c.getHeaderFieldDate("Date", 0L)
            val body = if (c.responseCode == 200) c.inputStream.use { String(it.readBytes().take(4096).toByteArray(), Charsets.UTF_8) } else null
            val name = decide(serverNow, parseName(body), null)
            if (name != OLD_NAME) p.edit().putString(KEY, name).apply()
        } catch (e: Exception) {
            // offline: keep the current name
        }
    }
}
