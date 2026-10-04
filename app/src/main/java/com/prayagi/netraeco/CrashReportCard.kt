package com.prayagi.netraeco

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings card: manual "Send crash report". Shows exactly what will be sent first. Real data only: the last saved crash. */
@Composable
fun CrashReportCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Crash report", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("If the app crashed, you can send the last crash to the developer. You see the exact text before anything is sent. It holds no name, email, location or files.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(enabled = !busy, onClick = {
                val p = CrashReporter.manualPreview(context)
                if (p == null) status = "Unavailable: no crash is saved on this device, so there is nothing to send." else { status = null; preview = p }
            }) { Text("Send crash report") }
            status?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
    preview?.let { text ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("This will be sent") },
            text = { Text(text, fontSize = 12.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        val ok = withContext(Dispatchers.IO) { CrashReporter.sendManual(context) }
                        status = if (ok) "Sent. Thank you." else "Not confirmed: the sending service did not accept it. Use Share instead."
                        busy = false
                        preview = null
                    }
                }) { Text(if (busy) "Sending..." else "Send") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, "Netra crash report")
                        context.startActivity(Intent.createChooser(i, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        preview = null
                    }) { Text("Share instead") }
                    TextButton(onClick = { preview = null }) { Text("Cancel") }
                }
            }
        )
    }
}
