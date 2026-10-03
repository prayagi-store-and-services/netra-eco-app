package com.prayagi.netraeco

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.OffsetDateTime

/** One roadmap entry, read from roadmap.json on the Netra Eco site (the same file the websites use). */
data class RoadmapItem(val title: String, val status: String, val etaMillis: Long?, val appNames: String, val released: Boolean)

object Roadmap {
    const val URL = "https://prayagi-store-and-services.github.io/netra-eco/roadmap.json"

    fun parse(json: String): List<RoadmapItem> = try {
        val root = JSONObject(json)
        val names = HashMap<String, String>()
        val apps = root.getJSONArray("apps")
        for (i in 0 until apps.length()) apps.getJSONObject(i).let { names[it.getString("id")] = it.getString("name") }
        val arr = root.getJSONArray("items")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val title = o.optString("title")
            if (title.isBlank()) return@mapNotNull null
            val ids = o.optJSONArray("apps")
            val appNames = (0 until (ids?.length() ?: 0)).joinToString(", ") { names[ids!!.getString(it)] ?: ids.getString(it) }
            val eta = try { OffsetDateTime.parse(o.optString("eta")).toInstant().toEpochMilli() } catch (e: Exception) { null }
            val status = o.optString("status")
            RoadmapItem(title, status, eta, appNames, status.startsWith("Released", ignoreCase = true))
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** The unreleased item with the earliest date that is still in the future; null when there is none. */
    fun next(items: List<RoadmapItem>, nowMillis: Long): RoadmapItem? =
        items.filter { !it.released && it.etaMillis != null && it.etaMillis > nowMillis }.minByOrNull { it.etaMillis!! }

    fun countdown(ms: Long): String {
        if (ms <= 0L) return "Estimate passed, still being finished"
        val s = ms / 1000
        return "%dd %02dh %02dm %02ds".format(s / 86400, s % 86400 / 3600, s % 3600 / 60, s % 60)
    }
}

@Composable
fun RoadmapSection(items: List<RoadmapItem>?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Coming soon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (items == null || items.isEmpty()) {
                Text("Roadmap Unavailable right now.", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            val next = Roadmap.next(items, now)
            if (next != null) {
                Spacer(Modifier.height(4.dp))
                Text("Next release: " + next.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Estimated in " + Roadmap.countdown(next.etaMillis!! - now), style = MaterialTheme.typography.bodyMedium)
            }
            val upcoming = items.filter { !it.released }.sortedBy { it.etaMillis ?: Long.MAX_VALUE }
            val shown = if (showAll) upcoming else upcoming.take(4)
            shown.forEach { it ->
                Spacer(Modifier.height(8.dp))
                Text(it.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val left = it.etaMillis?.let { e -> Roadmap.countdown(e - now) } ?: "Unavailable"
                Text(it.appNames + " | " + it.status + " | estimated: " + left, style = MaterialTheme.typography.bodySmall)
            }
            if (upcoming.size > 4) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show less" else "Show all (" + upcoming.size + ")") }
            }
            Text("All dates are our own estimates and can change.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
