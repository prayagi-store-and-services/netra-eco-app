package com.prayagi.netraeco

/** Groups for the app list. One app can belong to several groups. */
enum class AppCategory(val label: String) {
    MEDIA("Media"), SAFETY("Safety & Privacy"), DEVICE("Battery & Device"),
    GAMES("Games & Learning"), LIFESTYLE("Lifestyle"), UTILITY("Utility")
}

/**
 * Works out the groups of an app from what the app says it does (its name, type and summary), not from a fixed list per app.
 * Every matching group is returned, so an app whose features grow can appear in more groups. Utility is used only when nothing else matches.
 * An app (or its catalog entry) can also name groups itself with a "categories" list; those are added to what the text shows.
 */
object AppCategories {
    private val RULES: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.MEDIA to listOf("video", "mp3", "music", "audio", "media player", "player", "podcast", "radio"),
        AppCategory.SAFETY to listOf("safety", "emergency", "alert", "security", "privacy", "microphone", "camera", "vpn", "earthquake", "disaster", "firewall"),
        AppCategory.DEVICE to listOf("battery", "charging", "hardware", "device health", "sensor", "storage", "performance"),
        AppCategory.GAMES to listOf("game", "quiz", "learning", "reasoning", "study", "exam"),
        AppCategory.LIFESTYLE to listOf("astrology", "kundli", "panchang", "rashifal", "horoscope", "festival", "recipe", "fitness")
    )

    fun classify(name: String, type: String, summary: String, declared: List<String> = emptyList()): List<AppCategory> {
        val text = "$name $type $summary".lowercase()
        val found = linkedSetOf<AppCategory>()
        for ((cat, words) in RULES) if (words.any { text.contains(it) }) found.add(cat)
        for (d in declared) AppCategory.values().firstOrNull { it.name.equals(d.trim(), true) || it.label.equals(d.trim(), true) }?.let { found.add(it) }
        if (found.isEmpty()) found.add(AppCategory.UTILITY)
        return found.toList()
    }
}
