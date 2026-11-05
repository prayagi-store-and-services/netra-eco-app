package com.prayagi.netraeco

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope

/** Only known Netra packages are visible. No all-app query or special permission. */
object InstalledApps {
    val knownPackages = setOf(
        "com.aistudio.batterysentinel.ntra", "com.aistudio.reasoningquiz.tark",
        "com.aistudio.netrasensorhub.kxmpzq", "com.aistudio.sensorguard.prvsec",
        "com.prayagi.netraplayer", "com.prayagi.netratrikaal", "com.netracharge.app", "com.prayagi.netraeco"
    )
    data class Result(val known: Boolean, val version: Pair<Long, String>? = null)
    fun read(context: Context, packageName: String): Result {
        if (packageName !in knownPackages) return Result(false)
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            Result(true, PackageInfoCompat.getLongVersionCode(info) to info.versionName.orEmpty())
        } catch (_: PackageManager.NameNotFoundException) { Result(true) }
          catch (_: Exception) { Result(false) }
    }
}

fun storeStatus(row: AppRow): Status = if (!row.deviceStateKnown) Status.Unavailable else
    statusForRelease(row.installed?.first, row.installed?.second, row.latest)

fun storeActionLabel(row: AppRow): String = when (storeStatus(row)) {
    Status.NotInstalled -> "Install"
    Status.UpdateAvailable -> "Update"
    Status.UpToDate, Status.InstalledNewer -> "Open"
    Status.Unavailable -> "Details"
}

/** Resolve only policy paths supplied by the catalog, on the existing trusted host. */
fun catalogPrivacyUrl(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val uri = if (raw.startsWith("../../legal/")) java.net.URI("https://prayagi-store-and-services.github.io/netra-eco/" + raw.removePrefix("../../")) else java.net.URI(raw)
        if (uri.scheme == "https" && uri.host == "prayagi-store-and-services.github.io" && uri.userInfo == null) uri.toString() else ""
    }.getOrDefault("")
}

@Composable
fun StoreReleaseFacts(row: AppRow) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text("Installed", style = MaterialTheme.typography.labelSmall); Text(if (!row.deviceStateKnown) "Unknown" else row.installed?.second ?: "Not installed") }
        Column(Modifier.weight(1f)) { Text("Latest", style = MaterialTheme.typography.labelSmall); Text(row.latest?.versionName ?: "Unavailable") }
        Column(Modifier.weight(1f)) { Text("APK size", style = MaterialTheme.typography.labelSmall); Text(row.latest?.let { formatSize(it.size) } ?: "Unavailable") }
    }
    row.latest?.minSdk?.let { Text("Requires Android API $it or newer", style = MaterialTheme.typography.bodySmall) }
}

@Composable
fun StoreBrowseRow(row: AppRow, scope: CoroutineScope, actions: EcoInstallActions, onDetails: () -> Unit) {
    if (HumanSafetyHold.matches(row.app.repo, row.app.packageName) && !HumanSafetyHold.allowed(row.latest)) return
    val context = LocalContext.current
    val status = storeStatus(row)
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(row.app, 48.dp)
                Column(Modifier.weight(1f).clickable(onClick = onDetails)) {
                    Text(row.app.name, fontWeight = FontWeight.SemiBold)
                    Text(row.app.type, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    if (row.installed != null) Text("Installed", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    row.latest?.let { Text(it.versionName + " · " + formatSize(it.size), style = MaterialTheme.typography.labelSmall) }
                    if (!row.deviceStateKnown) Text("Device status Unknown", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = {
                    when (status) {
                        Status.UpToDate, Status.InstalledNewer -> {
                            val launch = context.packageManager.getLaunchIntentForPackage(row.app.packageName)
                            if (launch != null) runCatching { context.startActivity(launch) } else onDetails()
                        }
                        Status.NotInstalled, Status.UpdateAvailable -> {
                            if (row.latest?.minSdk?.let { !deviceCompatible(it) } == true) onDetails()
                            else actions.update(scope, context, row)
                        }
                        else -> onDetails()
                    }
                }, enabled = row.latest?.let { !actions.state(DownloadCenter.keyFor(row.app, it)).busy } ?: true) {
                    Text(row.latest?.let { if (actions.state(DownloadCenter.keyFor(row.app, it)).busy) "Verifying..." else storeActionLabel(row) } ?: storeActionLabel(row))
                }
            }
            row.latest?.let { actions.state(DownloadCenter.keyFor(row.app, it)).error }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDetails) { Text("App details") }
        }
    }
}

@Composable
fun StoreInstalledActions(row: AppRow) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(modifier = Modifier.weight(1f), onClick = {
            val intent = context.packageManager.getLaunchIntentForPackage(row.app.packageName)
            if (intent == null) error = "Cannot open: Android has no screen to start."
            else runCatching { context.startActivity(intent) }.onFailure { error = "Cannot open this app." }
        }) { Text("Open") }
        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + row.app.packageName))) }
                .onFailure { error = "Cannot start uninstall. Android refused." }
        }) { Text("Uninstall") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
fun ConditionalUpdateSetting() {
    val context=LocalContext.current
    var enabled by remember { mutableStateOf(ConditionalUpdates.enabled(context)) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Optional automatic updates",style=MaterialTheme.typography.titleMedium)
                    Text(if(enabled)"Enabled for eligible Eco-installed apps"else"Off by default",style=MaterialTheme.typography.bodySmall)
                }
                Switch(checked=enabled,onCheckedChange={enabled=it;ConditionalUpdates.setEnabled(context,it)})
            }
            Text("Only eligible Netra apps installed by Eco. Android may still ask for a confirmation tap.", style=MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("What this permission does", style=MaterialTheme.typography.titleSmall)
                Text("UPDATE_PACKAGES_WITHOUT_USER_ACTION lets Eco request an update without another tap where Android allows it. It does not bypass Android's rules.", style=MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Before each install", style=MaterialTheme.typography.titleSmall)
                Text("Verify file hash, package, version and signer. Check Android version and installer ownership. Read back the installed version before saying done.", style=MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Background checks", style=MaterialTheme.typography.titleSmall)
                Text("About every 6 hours, when Android allows it. Automatic downloads use unmetered networks only. Otherwise, a notification offers a verified update.", style=MaterialTheme.typography.bodySmall)
            }
            Text("No root, no accessibility auto-clicks, no all-app scanning. Realme and other battery controls can delay checks.", style=MaterialTheme.typography.bodySmall)
            Text("You can switch this off at any time",style=MaterialTheme.typography.labelMedium)
        }
    }
}
