package com.prayagi.netraeco

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A planned Trikaal feature. The date is a plan estimate (date only, no invented hour). If work slips, change the date in a new release. */
private data class SoonFeature(val title: String, val note: String, val eta: LocalDate)

private val SOON = listOf(
    SoonFeature("Muhurat alarm", "Pick a muhurat such as Brahma or Abhijit. The alarm moves by itself as that muhurat changes each day.", LocalDate.of(2026, 10, 15)),
    SoonFeature("Tithi food guide", "For each tithi: what to avoid eating, what to eat, and its phal.", LocalDate.of(2026, 10, 18)),
    SoonFeature("Face and palm reading", "Not built yet. This one is the largest of the four.", LocalDate.of(2026, 11, 5))
)

private fun countdownText(eta: LocalDate, today: LocalDate): String {
    val d = ChronoUnit.DAYS.between(today, eta)
    return when {
        d > 1 -> "Estimated in $d days"
        d == 1L -> "Estimated tomorrow"
        d == 0L -> "Estimated today"
        else -> "Estimate passed, still being finished"
    }
}

/** Download button for the kundli view. The PDF export is not built yet, so it opens an honest Coming Soon note with a live countdown. */
@Composable fun KundliPdfSoon() {
    val zone = remember { ZoneId.of("Asia/Kolkata") }
    var today by remember { mutableStateOf(LocalDate.now(zone)) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(zone); delay(30_000) } }
    var show by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.End) {
        androidx.compose.material3.ExtendedFloatingActionButton(onClick = { show = !show }) { Text("Download PDF (Coming Soon)") }
        if (show) Text("Kundli PDF is not available in this version. ${countdownText(LocalDate.of(2026, 10, 22), today)} (target 2026-10-22, moves if work slips).", style = MaterialTheme.typography.bodySmall)
    }
}

/** Coming Soon sections. Nothing here works yet; each shows a live countdown to an honest estimate. */
@Composable fun TrikaalSoon() {
    val zone = remember { ZoneId.of("Asia/Kolkata") }
    var today by remember { mutableStateOf(LocalDate.now(zone)) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(zone); delay(30_000) } }
    var open by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Coming Soon", style = MaterialTheme.typography.titleMedium)
        SOON.forEach { f ->
            OutlinedCard(Modifier.fillMaxWidth().clickable { open = if (open == f.title) null else f.title }) {
                Column(Modifier.padding(12.dp)) {
                    Text(f.title + " (Coming Soon)", style = MaterialTheme.typography.titleSmall)
                    Text(countdownText(f.eta, today), style = MaterialTheme.typography.bodyMedium)
                    if (open == f.title) {
                        Text(f.note, style = MaterialTheme.typography.bodySmall)
                        Text("Target date ${f.eta}. This is an estimate and the date is moved, not hidden, if the work slips.", style = MaterialTheme.typography.bodySmall)
                        Text("Not available in this version.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
