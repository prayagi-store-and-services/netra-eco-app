package com.prayagi.netraeco

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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


/** Colour of one ticker item. Orange (in progress) wins over the time colours. */
enum class Tone { RED, ORANGE, BLUE, GREEN, GREY }

object Ticker {
    const val TWO_HOURS = 2 * 3600 * 1000L
    const val THIRTY_MIN = 30 * 60 * 1000L

    fun inProgress(item: RoadmapItem) = item.status.trim().equals("In progress", ignoreCase = true)

    fun tone(item: RoadmapItem, nowMillis: Long): Tone {
        if (inProgress(item)) return Tone.ORANGE
        val eta = item.etaMillis ?: return Tone.GREY
        val left = eta - nowMillis
        return when {
            left <= THIRTY_MIN -> Tone.GREEN
            left <= TWO_HOURS -> Tone.BLUE
            else -> Tone.RED
        }
    }

    /** Short time left, minute precision so the line does not jitter every second. */
    fun left(ms: Long): String {
        val m = ms / 60000
        return when {
            m >= 24 * 60 -> "%d d %d h".format(m / (24 * 60), m % (24 * 60) / 60)
            m >= 60 -> "%d h %d m".format(m / 60, m % 60)
            else -> "%d m".format(m.coerceAtLeast(1))
        }
    }

    fun label(item: RoadmapItem, nowMillis: Long): String {
        val prefix = if (item.appNames.isNotBlank() && !item.appNames.contains(",")) item.appNames.substringBefore(" (") + ": " else ""
        val eta = item.etaMillis
        val tail = when {
            inProgress(item) -> if (eta != null && eta > nowMillis) "IN PROGRESS, est. in " + left(eta - nowMillis) else "IN PROGRESS"
            eta == null -> "Unavailable"
            eta <= nowMillis -> "estimate passed, check for update"
            else -> "in " + left(eta - nowMillis)
        }
        return prefix + item.title + " - " + tail
    }

    fun upcoming(items: List<RoadmapItem>): List<RoadmapItem> =
        items.filter { !it.released }.sortedBy { it.etaMillis ?: Long.MAX_VALUE }

    fun color(t: Tone): Color = when (t) {
        Tone.RED -> Color(0xFFFF4646)
        Tone.ORANGE -> Color(0xFFFF9800)
        Tone.BLUE -> Color(0xFF409CFF)
        Tone.GREEN -> Color(0xFF3CE66E)
        Tone.GREY -> Color(0xFFAAAAAA)
    }
}

/** One black strip at the top, one line, scrolling left to right without stopping. */
@Composable
fun TickerStrip(items: List<RoadmapItem>?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val list = if (items == null) emptyList() else Ticker.upcoming(items)
    Box(Modifier.fillMaxWidth().height(40.dp).background(Color.Black).clipToBounds(), contentAlignment = Alignment.CenterStart) {
        if (list.isEmpty()) {
            Text("Roadmap Unavailable right now.", color = Ticker.color(Tone.GREY), fontSize = 14.sp, modifier = Modifier.offset(x = 16.dp))
            return@Box
        }
        val text: AnnotatedString = buildAnnotatedString {
            list.forEach { it ->
                withStyle(SpanStyle(color = Ticker.color(Ticker.tone(it, now)), fontWeight = FontWeight.Bold)) { append(Ticker.label(it, now)) }
                withStyle(SpanStyle(color = Color(0xFF888888))) { append("     |     ") }
            }
        }
        var unit by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val screenPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
        val durationMs = if (unit > 0) ((unit / density.density) / 60f * 1000f).toInt().coerceAtLeast(4000) else 12000
        val p by rememberInfiniteTransition(label = "ticker").animateFloat(
            0f, 1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart), label = "p"
        )
        val copies = if (unit > 0) (kotlin.math.ceil(screenPx / unit).toInt() + 2) else 3
        Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset { IntOffset((-unit + p * unit).toInt(), 0) }) {
            repeat(copies) { i ->
                Text(
                    text, fontSize = 15.sp, maxLines = 1, softWrap = false,
                    modifier = if (i == 0) Modifier.onSizeChanged { unit = it.width } else Modifier
                )
            }
        }
    }
}
