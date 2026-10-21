package com.prayagi.netraeco

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * Anonymous download count for the Ecosystem site (the same public counter the website page of each app uses).
 * Only when the user has switched the anonymous usage count ON in Settings (default OFF), and only after a download
 * finished and its SHA-256 matched, the app adds +1 to dl-<code>. The request carries no device id, install id,
 * account or location, and a failed request never affects the download.
 */
object DownloadCount {
    private const val BASE = "https://abacus.jasoncameron.dev/hit/netra-pages/dl-"

    /** The website code of an app from its repo name (same codes as the Ecosystem app pages), or null when unknown. */
    fun codeFor(repo: String): String? {
        val r = repo.substringAfterLast('/').lowercase()
        return when {
            r.contains("battery-sentinel") -> "bspn"
            r == "kbc" -> "kbc"
            r == "netra-hub" -> "hub"
            r.contains("privacy") -> "privacy"
            r == "netra-trikaal" -> "trikaal"
            r == "netra-player" -> "player"
            r == "netra-eco-app" -> "eco"
            else -> null
        }
    }

    fun urlFor(repo: String): String? = codeFor(repo)?.let { BASE + it }

    fun countFinishedDownload(context: Context, repo: String) {
        if (!UsagePing.isEnabled(context)) return
        val url = urlFor(repo) ?: return
        Thread {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "NetraEco-download-count")
                c.responseCode
                c.disconnect()
            } catch (_: Exception) { }
        }.start()
    }
}
