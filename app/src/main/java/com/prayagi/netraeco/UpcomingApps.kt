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
data class UpcomingApp(val id: String, val name: String, val type: String, val summary: String, val etaMillis: Long?)

object Upcoming {
    fun parse(json: String): List<UpcomingApp> = try {
        val arr = JSONObject(json).optJSONArray("upcoming")
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            val name = o.optString("name")
            if (id.isBlank() || name.isBlank()) null else UpcomingApp(
                id, name, o.optString("type"), o.optString("summary"),
                o.optString("eta").takeIf { it.isNotBlank() }?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
fun UpcomingCard(app: UpcomingApp) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (app.type.isNotBlank()) Text(app.type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        if (app.summary.isNotBlank()) Text(app.summary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
            Text("Coming soon", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            val eta = app.etaMillis
            if (eta != null) {
                Text(Roadmap.countdown(eta - now), fontWeight = FontWeight.Medium)
                Text("Approximate, it may arrive a little earlier or later.", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Unavailable", fontWeight = FontWeight.Medium)
                Text("No release time is set yet.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
