package com.prayagi.netraeco

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Public editorial counts only. No user identifier, click history, or account tokens. */
object SocialPosts {
    const val URL = "https://prayagi-store-and-services.github.io/netra-eco/data/social-posts.json"
    private val platforms = setOf("youtube", "instagram", "facebook", "x", "telegram")
    private val zone = ZoneId.of("Asia/Kolkata")
    fun day(now: Instant): String = now.atZone(zone).toLocalDate().toString()
    fun parse(raw: String?, now: Instant): Map<String, Int> = try {
        if (raw == null || raw.length > 32768) emptyMap() else {
            val root = JSONObject(raw)
            val generated = Instant.parse(root.getString("generatedAt"))
            if (root.getInt("schemaVersion") != 1 || root.getString("timezone") != "Asia/Kolkata" ||
                root.getString("date") != day(now) || day(generated) != day(now) || generated.isAfter(now.plusSeconds(300))) emptyMap()
            else {
                val counts = root.getJSONObject("counts")
                platforms.mapNotNull { platform ->
                    val value = counts.opt(platform)
                    if (value is Int && value in 1..9999) platform to value else null
                }.toMap()
            }
        }
    } catch (_: Exception) { emptyMap() }
}
