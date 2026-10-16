package com.prayagi.netraeco

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Request a new app": a user tells the developer what app they want. Uses the same free
 * reporting channel as crash reports (FormSubmit email to the developer) - no new backend.
 * Privacy first: the user sees the exact text before anything is sent, a contact detail is
 * optional, and nothing sends by itself. Spam guard: one request per device per day and
 * length caps, same style as the crash reporter.
 */
object AppRequests {
    private const val ENDPOINT = "https://formsubmit.co/ajax/prayagideepak@gmail.com"
    private const val PREFS = "app_requests"
    private const val KEY_LAST_SENT_DAY = "last_sent_day"
    const val MAX_REQUEST = 1000
    const val MAX_CONTACT = 120

    fun todayKey(now: Long = System.currentTimeMillis()): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))

    /** Daily spam guard: one request per device per day. */
    fun canSendToday(lastSentDay: String?, today: String): Boolean = lastSentDay != today

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun canSendToday(context: Context): Boolean =
        canSendToday(prefs(context).getString(KEY_LAST_SENT_DAY, null), todayKey())

    fun buildPayload(request: String, contact: String, appVersion: String): org.json.JSONObject {
        val json = org.json.JSONObject()
        json.put("_subject", "[Netra] New app request")
        json.put("_captcha", "false")
        json.put("_template", "table")
        json.put("request", request.trim().take(MAX_REQUEST))
        val c = contact.trim().take(MAX_CONTACT)
        if (c.isNotEmpty()) json.put("contact_optional", c)
        json.put("app_version", appVersion)
        json.put("device", Build.MODEL ?: "Unknown")
        json.put("android_version", Build.VERSION.RELEASE ?: "Unknown")
        return json
    }

    /** The exact text the user reviews before sending. */
    fun preview(request: String, contact: String, appVersion: String): String {
        val sb = StringBuilder()
        sb.append("Your request:\n").append(request.trim().take(MAX_REQUEST)).append('\n')
        val c = contact.trim().take(MAX_CONTACT)
        if (c.isNotEmpty()) sb.append("\nContact (optional):\n").append(c).append('\n')
        sb.append("\nApp version: ").append(appVersion)
        sb.append("\nDevice: ").append(Build.MODEL ?: "Unknown")
        sb.append("\nAndroid: ").append(Build.VERSION.RELEASE ?: "Unknown")
        return sb.toString()
    }

    /** Sends after the user reviewed the exact text. Returns true only when the service accepted it. */
    fun send(context: Context, request: String, contact: String, appVersion: String): Boolean {
        if (!canSendToday(context)) return false
        val json = buildPayload(request, contact, appVersion)
        return try {
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 15000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Origin", "https://prayagi-store-and-services.github.io")
            c.setRequestProperty("Referer", "https://prayagi-store-and-services.github.io/")
            c.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode in 200..299 && CrashReporter.accepted(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
            if (ok) prefs(context).edit().putString(KEY_LAST_SENT_DAY, todayKey()).apply()
            ok
        } catch (_: Exception) { false }
    }
}

/** Slim entry row directly under the header strip: "Request a new app". */
@Composable
fun RequestAppEntry() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { open = true }) { Text("Request a new app", maxLines = 1) }
    }
    if (open) RequestAppDialog(onClose = { open = false })
}

@Composable
private fun RequestAppDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown" }
        catch (e: Exception) { "Unknown" }
    }
    var request by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var reviewing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val allowedToday = remember { AppRequests.canSendToday(context) }

    if (reviewing) {
        AlertDialog(
            onDismissRequest = { if (!busy) reviewing = false },
            title = { Text("This will be sent") },
            text = { Text(AppRequests.preview(request, contact, appVersion), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        val ok = withContext(Dispatchers.IO) { AppRequests.send(context, request, contact, appVersion) }
                        busy = false
                        if (ok) { status = "Sent. Thank you."; reviewing = false; request = ""; contact = "" }
                        else { status = "Not confirmed: the sending service did not accept it. Nothing was sent."; reviewing = false }
                    }
                }) { Text("Send") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { reviewing = false }) { Text("Back") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Request a new app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tell the developer what app you want and what it should do. If it is possible, the Netra team will try to build it. You review the exact text before anything is sent. A contact detail is optional - without it the team cannot reply to you.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = request,
                    onValueChange = { request = it.take(AppRequests.MAX_REQUEST) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    label = { Text("Which app do you want?") }
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it.take(AppRequests.MAX_CONTACT) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Contact (optional)") }
                )
                if (!allowedToday) {
                    Text("One request per day from this device. You already sent one today.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && allowedToday && request.trim().length >= 10,
                onClick = { status = null; reviewing = true }
            ) { Text("Review and send") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Cancel") } }
    )
}
