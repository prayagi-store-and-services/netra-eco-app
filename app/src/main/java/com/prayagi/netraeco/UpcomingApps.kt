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
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One planned app shown as "Coming soon" (no download, no update). Read from the "upcoming" list in projects.json. */
data class UpcomingTarget(val label: String, val etaMillis: Long?, val note: String, val targetDate: LocalDate? = null)
data class UpcomingApp(val id: String, val name: String, val type: String, val summary: String, val etaMillis: Long?, val targets: List<UpcomingTarget> = emptyList())

object Upcoming {
    fun targetText(target: UpcomingTarget, nowMillis: Long): String {
        target.targetDate?.let { date ->
            val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
            val days = ChronoUnit.DAYS.between(today, date)
            return when {
                days > 3 -> "$days days to target"
                days > 0 -> {
                    val instant = java.time.Instant.ofEpochMilli(nowMillis)
                    val start = date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()
                    val end = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()
                    val min = java.time.Duration.between(instant, start).toHours()
                    val max = (java.time.Duration.between(instant, end).seconds + 3599) / 3600
                    "Approx. $min-$max hours to target day"
                }
                days == 0L -> "Target day, release not confirmed"
                else -> "Target passed, check release status"
            }
        }
        return target.etaMillis?.let { Roadmap.countdown(it - nowMillis) } ?: "Unavailable"
    }

    private fun parseEta(value: String): Long? = value.takeIf { it.isNotBlank() }?.let {
        runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun parseTargets(o: JSONObject): List<UpcomingTarget> {
        val values = o.optJSONArray("targets") ?: return emptyList()
        return (0 until minOf(values.length(), 4)).mapNotNull { i ->
            val target = values.optJSONObject(i) ?: return@mapNotNull null
            val label = target.optString("label").trim()
            val date = runCatching { LocalDate.parse(target.optString("targetDate")) }.getOrNull()
            if (label.isBlank()) null else UpcomingTarget(label, parseEta(target.optString("eta")), target.optString("note"), date)
        }
    }

    fun parse(json: String): List<UpcomingApp> = try {
        val arr = JSONObject(json).optJSONArray("upcoming")
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            val name = o.optString("name")
            if (id.isBlank() || name.isBlank()) null else UpcomingApp(
                id, name, o.optString("type"), o.optString("summary"),
                parseEta(o.optString("eta")), parseTargets(o)
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
        val targets = app.targets.ifEmpty { listOf(UpcomingTarget("Coming soon", app.etaMillis, "")) }
        targets.forEach { target ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                Text(target.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                val eta = target.etaMillis
                if (eta != null || target.targetDate != null) {
                    Text(Upcoming.targetText(target, now), fontWeight = FontWeight.Medium)
                    Text("Approximate target, not a release guarantee.", style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("Unavailable", fontWeight = FontWeight.Medium)
                    Text("No release time is set yet.", style = MaterialTheme.typography.labelSmall)
                }
                if (target.note.isNotBlank()) Text(target.note, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
