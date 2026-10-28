package com.prayagi.netraeco

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject

/** One planned app shown as "Coming soon" (no download, no update). Read from the "upcoming" list in projects.json. */
data class UpcomingApp(val id: String, val name: String, val type: String, val summary: String, val etaMillis: Long?, val features: List<String> = emptyList())

object Upcoming {
    /**
     * The ETA to show on a Coming soon card: the app's own eta when the site sets one, otherwise
     * the earliest still-pending roadmap item that names the app (roadmap.json is the same source
     * the websites use). Null when neither exists, and null when the roadmap could not be read.
     */
    fun etaFor(app: UpcomingApp, pendingRoadmap: List<RoadmapItem>?): Long? =
        app.etaMillis ?: pendingRoadmap?.let { Roadmap.nextFor(it, app.id)?.etaMillis }

    /** Upcoming nearest first, passed targets next, undated last. Never change published dates. */
    fun sorted(apps: List<UpcomingApp>, pendingRoadmap: List<RoadmapItem>?, nowMillis: Long = System.currentTimeMillis()): List<UpcomingApp> =
        apps.sortedWith(compareBy<UpcomingApp> {
            val eta = etaFor(it, pendingRoadmap)
            when { eta == null -> 2; eta > nowMillis -> 0; else -> 1 }
        }.thenBy { etaFor(it, pendingRoadmap) ?: Long.MAX_VALUE })

    fun targetLabel(etaMillis: Long): String = "Release target: " +
        java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", java.util.Locale.ENGLISH)
            .withZone(java.time.ZoneId.of("Asia/Kolkata"))
            .format(java.time.Instant.ofEpochMilli(etaMillis)) + " IST"

    fun parse(json: String): List<UpcomingApp> = try {
        val arr = JSONObject(json).optJSONArray("upcoming")
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            val name = o.optString("name")
            if (id.isBlank() || name.isBlank()) null else UpcomingApp(
                id, name, o.optString("type"), o.optString("summary"),
                o.optString("eta").takeIf { it.isNotBlank() }?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
                o.optJSONArray("features")?.let { fa -> (0 until fa.length()).mapNotNull { fa.optString(it).takeIf(String::isNotBlank) } } ?: emptyList()
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
fun UpcomingCard(app: UpcomingApp, pendingRoadmap: List<RoadmapItem>?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (app.type.isNotBlank()) Text(app.type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        if (app.summary.isNotBlank()) Text(app.summary, style = MaterialTheme.typography.bodyMedium)
        if (app.features.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Planned features (planned, may change):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            app.features.forEach { Text("- $it", style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
            Text("Coming soon", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            val eta = Upcoming.etaFor(app, pendingRoadmap)
            if (eta != null) {
                Text(Upcoming.targetLabel(eta), style = MaterialTheme.typography.labelMedium)
                LiveCountdown(eta, false)
                Text("Approximate, it may arrive a little earlier or later.", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Unavailable", fontWeight = FontWeight.Medium)
                Text(if (pendingRoadmap == null) "The roadmap could not be read." else "No release time is set yet.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
