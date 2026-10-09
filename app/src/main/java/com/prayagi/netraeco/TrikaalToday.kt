package com.prayagi.netraeco

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prayagi.trikaal.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TodayHm = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/**
 * First-screen card for people with no saved profile, so the first screen is never just an empty form.
 * Everything is calculated on the phone for the chosen place (Prayagraj if no location was shared). Nothing is sent anywhere.
 */
@Composable fun TrikaalToday(place: PanchangPlace) {
    var day by remember { mutableStateOf<Computation<VedicDay>?>(null) }
    var moon by remember { mutableStateOf<Computation<List<Position>>?>(null) }
    LaunchedEffect(place) {
        day = null
        val now = Instant.now()
        day = withContext(Dispatchers.Default) { Panchang.day(now, place) }
        moon = withContext(Dispatchers.Default) { AstroCore.currentPositions(now) }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1D6), contentColor = Color(0xFF5E1724))) {
        Column(Modifier.padding(16.dp)) {
            Text("Today / आज · ${place.label}", style = MaterialTheme.typography.titleMedium, color = Color(0xFF5E1724))
            when (val d = day) {
                null -> Text("Calculating on this phone...")
                is Computation.Unavailable -> Text("Unavailable: ${d.reason}")
                is Computation.Available -> {
                    val v = d.value
                    val now = Instant.now()
                    val t = v.tithis.firstOrNull { it.contains(now) }
                    Text("Tithi / तिथि: " + (t?.let { "${it.name} (${it.paksha}), ends ${TodayHm.format(it.end.atZone(place.zone))}" } ?: "Unavailable"))
                    val m = (moon as? Computation.Available)?.value?.firstOrNull { it.graha == Graha.MOON }
                    Text("Nakshatra / नक्षत्र: " + (m?.let { NAKSHATRA_NAMES[it.nakshatra] } ?: "Unavailable"))
                    listOf("Rahu Kaal", "Yamaganda", "Gulika Kaal", "Abhijit").forEach { n ->
                        val s = v.spans.firstOrNull { it.name.startsWith(n) }
                        Text("$n: " + (s?.let { "${TodayHm.format(it.start.atZone(place.zone))} to ${TodayHm.format(it.end.atZone(place.zone))}" } ?: "Unavailable"))
                    }
                    Text("Sunrise ${TodayHm.format(v.sunrise.atZone(place.zone))} · Sunset ${TodayHm.format(v.sunset.atZone(place.zone))}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Rashifal / राशिफल: tap your Moon sign (rashi)", style = MaterialTheme.typography.titleSmall, color = Color(0xFF5E1724))
            var sel by remember { mutableStateOf<Int?>(null) }
            SIGN_NAMES.withIndex().toList().chunked(4).forEach { row ->
                Row { row.forEach { (i, n) -> TextButton(onClick = { sel = i }) { Text((if (sel == i) "• " else "") + n) } } }
            }
            val s0 = sel
            val sky = (moon as? Computation.Available)?.value
            if (s0 != null && sky != null) {
                var good = 0; var counted = 0
                val lines = Rashifal.FAVOURABLE_FROM_MOON.mapNotNull { (g, houses) ->
                    val t = sky.firstOrNull { it.graha == g } ?: return@mapNotNull null
                    val house = (t.rashi - s0 + 12) % 12 + 1
                    counted++
                    val ok = house in houses
                    if (ok) good++
                    "${g.label} in ${SIGN_NAMES[t.rashi]}, house $house from ${SIGN_NAMES[s0]}: " + (if (ok) "favourable" else "not favourable")
                }
                Text("$good of $counted planets are favourable from ${SIGN_NAMES[s0]} today (gochara rule, from the Moon sign).")
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Sign-based only. Enter birth details below for the full reading with Tarabala and dasha. Vedic reading, not certainty.", style = MaterialTheme.typography.bodySmall)
            } else if (s0 != null) Text("Unavailable: planet positions not calculated.")
            Text("Enter your birth details below to see your own kundli.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
