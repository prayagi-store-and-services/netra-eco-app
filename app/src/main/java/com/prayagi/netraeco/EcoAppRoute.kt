package com.prayagi.netraeco

import java.net.URI

/** URI entry is navigation only, never permission to download or install. */
object EcoAppRoute {
    const val MIN_HANDOFF_VERSION = 59L
    private val appIds = mapOf(
        "bspn" to "battery-sentinel", "kbc" to "kbc", "hub" to "netra-hub",
        "privacy" to "prayagi-privacy", "trikaal" to "netra-trikaal",
        "player" to "netra-player", "charge" to "netra-charge", "eco" to "netra-eco"
    )
    fun appId(raw: String?): String? = try {
        val uri = URI(raw ?: "")
        if (uri.scheme != "https" || uri.host != "prayagi-store-and-services.github.io" ||
            uri.rawUserInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) null
        else {
            val match = Regex("^/netra-eco/app/([a-z]+)/?$").matchEntire(uri.rawPath ?: "")
            match?.groupValues?.get(1)?.let(appIds::get)
        }
    } catch (_: Exception) { null }
}
