package com.prayagi.netraeco

/** Fixed public destinations only. No account, device data or automatic request is attached. */
object CommunityLinks {
    data class Link(val label: String, val icon: String, val url: String)
    val social = listOf(
        Link("YouTube - subscribe", "▶", "https://www.youtube.com/@NetraEcosystem"),
        Link("Instagram - follow", "◎", "https://www.instagram.com/netraecosystem/"),
        Link("Facebook - follow", "f", "https://www.facebook.com/netraecosystem"),
        Link("X - follow", "𝕏", "https://x.com/netraecosystem"),
        Link("Telegram - join", "➤", "https://t.me/Netraecosystem")
    )
    private val sites = mapOf(
        "battery-sentinel" to "https://prayagi-store-and-services.github.io/-Battery-Sentinel-Pro-Netra/",
        "kbc" to "https://prayagi-store-and-services.github.io/KBC/",
        "netra-hub" to "https://prayagi-store-and-services.github.io/netra-hub/",
        "prayagi-privacy" to "https://prayagi-store-and-services.github.io/prayagi-Privacy-/",
        "netra-trikaal" to "https://prayagi-store-and-services.github.io/netra-eco/app/trikaal/",
        "netra-player" to "https://prayagi-store-and-services.github.io/netra-eco/app/player/",
        "netra-eco" to "https://prayagi-store-and-services.github.io/netra-eco/app/eco/",
        "netra-charge" to "https://prayagi-store-and-services.github.io/netra-eco/app/charge/"
    )
    fun website(appId: String): String? = sites[appId]
    fun reviews(appId: String): String? = sites[appId]?.let { "https://prayagi-store-and-services.github.io/netra-eco/embed.html?app=$appId" }
    fun contact(appId: String): String? = sites[appId]?.let { "https://prayagi-store-and-services.github.io/netra-eco/contact.html?app=$appId" }
}
