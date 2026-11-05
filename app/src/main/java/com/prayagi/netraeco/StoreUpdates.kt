package com.prayagi.netraeco

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

fun storeUpdateRows(rows: List<AppRow>): List<AppRow> = AppSort.current(rows).filter {
    it.deviceStateKnown && storeStatus(it) == Status.UpdateAvailable &&
        it.latest != null && DownloadCenter.allowedForDownload(it.app, it.latest)
}

fun storeReleaseDate(millis: Long?): String = millis?.let {
    java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
} ?: "Unavailable"

/** Update all queues downloads; Android's install confirmation remains explicit for every APK. */
@Composable
fun StoreUpdatesPanel(rows: List<AppRow>, scope: CoroutineScope, actions: EcoInstallActions) {
    val context = LocalContext.current
    val items by DownloadCenter.items.collectAsState()
    val updates = storeUpdateRows(rows)
    var queueBusy by remember { mutableStateOf(false) }
    var queueMessage by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Available updates (" + updates.size + ")", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Button(enabled = updates.isNotEmpty() && !queueBusy, onClick = {
                queueBusy = true
                queueMessage = "Preparing verified downloads. Confirm each install with Android."
                scope.launch {
                    try {
                        for (row in updates) {
                            val release = row.latest ?: continue
                            if (!deviceCompatible(release.minSdk)) continue
                            val key = DownloadCenter.keyFor(row.app, release)
                            if (DownloadCenter.hasFile(context, row.app, release)) continue
                            DownloadCenter.start(context, row.app, release)
                            DownloadCenter.items.first { map -> map[key]?.state?.let { it != DownloadCenter.DOWNLOADING } == true }
                        }
                        queueMessage = "Downloads checked. Tap each ready item to install; Android asks for confirmation."
                    } finally { queueBusy = false }
                }
            }) { Text(if (queueBusy) "Queuing..." else "Update all") }
        }
        Text("Only installed Netra apps with a verified newer release. No silent installs.", style = MaterialTheme.typography.bodySmall)
        queueMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (updates.isEmpty()) Text("No verified updates found. Unknown device or release status is not counted.")
        updates.forEach { row ->
            val release = row.latest!!
            val key = DownloadCenter.keyFor(row.app, release)
            val item = items[key]
            var expanded by remember(row.app.id) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(row.app, 42.dp)
                        Column(Modifier.weight(1f)) {
                            Text(row.app.name, style = MaterialTheme.typography.titleSmall)
                            Text(release.versionName + " · " + formatSize(release.size) + " APK", style = MaterialTheme.typography.bodySmall)
                            Text("Released " + storeReleaseDate(release.publishedAtMillis), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "More") }
                    }
                    if (expanded) Text(usefulNotes(release.notes).ifBlank { "Release notes unavailable." }, style = MaterialTheme.typography.bodySmall)
                    if (!deviceCompatible(release.minSdk)) Text("Incompatible with this Android version.", color = MaterialTheme.colorScheme.error)
                    else if (item?.state == DownloadCenter.FAILED) {
                        Text(item.error ?: "Could not download.", color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { DownloadCenter.start(context, row.app, release) }) { Text("Retry") }
                    } else AppDownloadButton(row, Status.UpdateAvailable, scope, actions)
                    Text("Installed version is refreshed after Android returns. Downloaded does not mean installed.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
