package com.prayagi.netraeco

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prayagi.trikaal.*
import com.prayagi.trikaal.Tone as PTone
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val RedTone = Color(0xFFC62828)
private val GreenTone = Color(0xFF2E7D32)
private val BlueTone = Color(0xFF1565C0)
private val VAARA = mapOf(DayOfWeek.SUNDAY to "Ravivar", DayOfWeek.MONDAY to "Somvar", DayOfWeek.TUESDAY to "Mangalvar",
    DayOfWeek.WEDNESDAY to "Budhvar", DayOfWeek.THURSDAY to "Guruvar", DayOfWeek.FRIDAY to "Shukravar", DayOfWeek.SATURDAY to "Shanivar")
private val HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private fun toneColor(t: PTone) = when (t) { PTone.RED -> RedTone; PTone.GREEN -> GreenTone; PTone.BLUE -> BlueTone }
private fun left(from: Instant, to: Instant): String {
    val s = Duration.between(from, to).seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

/** Always-visible, continuously scrolling panchang bar. Place: Prayagraj (owner's choice). Everything is calculated live on the phone. */
@Composable fun TrikaalTicker(place: com.prayagi.trikaal.PanchangPlace = PRAYAGRAJ) {
    var now by remember { mutableStateOf(Instant.now()) }
    var day by remember { mutableStateOf<Computation<VedicDay>?>(null) }
    var sky by remember { mutableStateOf<Computation<List<Position>>?>(null) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(1000) } }
    val d = (day as? Computation.Available)?.value
    LaunchedEffect(place) { day = null }
    val stale = d == null || now >= d.nextSunrise || now >= d.tithis.last().start
    LaunchedEffect(place, stale, if (stale) 0L else now.epochSecond / 3600) {
        if (stale) day = withContext(Dispatchers.Default) { Panchang.day(Instant.now(), place) }
    }
    LaunchedEffect(now.epochSecond / 300) { sky = withContext(Dispatchers.Default) { AstroCore.currentPositions(Instant.now()) } }
    val zone = place.zone
    val text = buildAnnotatedString {
        fun item(color: Color, s: String, bold: Boolean = false) {
            withStyle(SpanStyle(color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)) { append(s) }
            append("      •      ")
        }
        when (val c = day) {
            is Computation.Available -> {
                val v = c.value
                val dt = v.date
                item(BlueTone, "Vikram Samvat ${dt.samvat} · ${if (dt.adhik) "Adhik " else ""}${dt.month} ${dt.paksha} ${dt.tithiInPaksha} (${dt.tithiName}) · ${VAARA[v.weekday]} · ${place.label}")
                val (g, p) = Panchang.ghatiPal(now, v.sunrise)
                item(BlueTone, "Vaidik samay: Ghati $g, Pal $p since sunrise ${HM.format(v.sunrise.atZone(zone))}")
                v.tithis.firstOrNull { it.contains(now) }?.let { t ->
                    item(BlueTone, "Tithi: ${t.name} (${t.paksha}) ends in ${left(now, t.end)}", true)
                }
                val active = v.spans.filter { it.contains(now) }
                active.forEach { s -> item(toneColor(s.tone), "${s.name} now: ends in ${left(now, s.end)}", true) }
                if (active.isEmpty()) item(BlueTone, "No Rahu Kaal, Yamaganda, Gulika or Abhijit running now")
                v.spans.filter { it.start > now }.minByOrNull { it.start }?.let { s ->
                    item(toneColor(s.tone), "Next: ${s.name} ${HM.format(s.start.atZone(zone))}-${HM.format(s.end.atZone(zone))} IST, starts in ${left(now, s.start)}")
                }
                val soon = v.tithis.filter { it.start > now && it.start < now.plusSeconds(24 * 3600L) }
                if (soon.isNotEmpty()) item(BlueTone, "Upcoming tithi: " + soon.joinToString(", ") { "${it.name} (${it.paksha}) from ${HM.format(it.start.atZone(zone))}" + (if (it.start.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()) " tomorrow" else "") })
                else item(BlueTone, "No new tithi starts in the next 24 hours")
            }
            is Computation.Unavailable -> item(RedTone, "Unavailable: ${c.reason}")
            null -> item(BlueTone, "Calculating panchang on this phone...")
        }
        when (val s = sky) {
            is Computation.Available -> item(BlueTone, "Gochar: " + s.value.joinToString(", ") { "${it.graha.label} ${SIGN_NAMES[it.rashi]} ${String.format(Locale.ROOT, "%.0f", it.degreeInSign)}°" + if (it.retrograde && it.graha != Graha.RAHU && it.graha != Graha.KETU) " (R)" else "" })
            is Computation.Unavailable -> item(RedTone, "Gochar unavailable: ${s.reason}")
            null -> item(BlueTone, "Gochar: calculating...")
        }
    }
    val density = LocalDensity.current
    var boxW by remember { mutableIntStateOf(0) }
    var textW by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(boxW) { if (boxW > 0 && !started) { offset = boxW.toFloat(); started = true } }
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        var last = 0L
        val speed = with(density) { 56.dp.toPx() }
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last != 0L) { offset -= (t - last) / 1e9f * speed; if (textW > 0 && offset < -textW) offset = boxW.toFloat() }
                last = t
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFFFF8E1)).border(1.dp, Color(0xFF5E1724)).clipToBounds().onSizeChanged { boxW = it.width },
        contentAlignment = Alignment.CenterStart) {
        Text(text, maxLines = 1, softWrap = false, style = MaterialTheme.typography.bodyMedium,
            onTextLayout = { textW = it.size.width },
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset { IntOffset(offset.roundToInt(), 0) })
    }
}
