package com.prayagi.netraeco

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(millis: Long): String =
    SimpleDateFormat("h:mm a, E d MMM", Locale.getDefault()).format(Date(millis))

/** Settings card for Time Speak Out: switch, honest status, and a clearly labelled test button. */
@Composable
fun TimeSpeakCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(TimeSpeakOut.isEnabled(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var testNote by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Time Speak Out", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Speaks the time every 30 minutes, on the hour and half hour. Silent from 11 PM to 6 AM. Follows the phone ringer: stays quiet on silent or vibrate, and pauses other media while it speaks.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = on, onCheckedChange = {
                    on = it
                    TimeSpeakOut.setEnabled(context, it)
                    refresh++
                })
            }
            @Suppress("UNUSED_EXPRESSION") refresh
            if (on) {
                Spacer(Modifier.height(8.dp))
                val next = TimeSpeakOut.nextSlotMillis(System.currentTimeMillis())
                Text("Next announcement: " + fmt(next) + " (approximate - Android may run a little early or late)", style = MaterialTheme.typography.bodySmall)
                val last = TimeSpeakOut.lastSpokeAt(context)
                Text("Last announced: " + (if (last > 0) fmt(last) else "Not announced yet"), style = MaterialTheme.typography.bodySmall)
                TimeSpeakOut.lastSkipReason(context)?.let {
                    Text("Last skipped: " + it, style = MaterialTheme.typography.bodySmall)
                }
                if (!TimeSpeakOut.canScheduleExact(context)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Exact timing Unavailable: Android has not allowed exact alarms for this app, so announcements may arrive a little late.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        TextButton(onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            } catch (_: Exception) { }
                        }) { Text("Allow exact alarms") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Some phones stop background alarms to save battery. If announcements stop, set battery to \"No restrictions\" for this app in Android Settings.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = !testing,
                onClick = {
                    testing = true
                    testNote = "Playing a clearly labelled test announcement..."
                    TimeSpeaker.get(context).speakTime(System.currentTimeMillis(), isTest = true) {
                        testing = false
                        testNote = "Test announcement finished."
                    }
                }
            ) { Text(if (testing) "Testing..." else "Test announcement") }
            Text("Plays a labelled test line through the same speaker path as a real announcement. It is only a test, never a real alert.", style = MaterialTheme.typography.bodySmall)
            testNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
