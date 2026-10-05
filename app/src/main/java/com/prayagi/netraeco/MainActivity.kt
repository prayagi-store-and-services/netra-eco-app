package com.prayagi.netraeco

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row of the screen: the app, what is installed, and the newest release (null = could not be read). */
data class AppRow(
    val app: CatalogApp,
    val installed: Pair<Long, String>?,
    val latest: LatestRelease?
)

class MainActivity : ComponentActivity() {
    private val resumeCount = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        setContent {
            NetraTheme { EcoScreen(resumeCount.intValue) }
        }
    }

    // Refresh the list the moment an app is installed, updated or removed, even if Eco stays on screen.
    private val packageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            resumeCount.intValue = resumeCount.intValue + 1
        }
    }

    override fun onStart() {
        super.onStart()
        val f = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, f)
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(packageReceiver) } catch (e: IllegalArgumentException) { }
    }

    override fun onResume() {
        super.onResume()
        // Re-read installed versions when the user comes back from the system installer.
        // Delete installer files left from a finished or cancelled install (not while a download runs).
        Thread {
            Net.cleanLeftovers(applicationContext)
            UsagePing.pingIfDue(applicationContext)
        }.start()
        resumeCount.intValue = resumeCount.intValue + 1
    }
}

private suspend fun loadRows(context: Context): List<AppRow>? = withContext(Dispatchers.IO) {
    val catalogJson = Net.fetchText(Net.CATALOG_URL) ?: return@withContext null
    val apps = Net.parseCatalog(catalogJson)
    val rows = coroutineScope {
        (listOf(Net.selfApp(context)) + apps).map { app ->
            async {
                AppRow(app, Net.installed(context, app.packageName), Net.fetchLatest(app))
            }
        }.awaitAll()
    }
    try {
        val statuses = rows.map { statusForRelease(it.installed?.first, it.installed?.second, it.latest) }
        val lines = rows.mapIndexed { i, r -> EcoWidgetStore.lineFor(r.app.name, r.installed?.second, r.latest?.versionName, statuses[i]) }
        EcoWidgetStore.save(context, lines, statuses.count { it == Status.UpdateAvailable })
        EcoWidgetProvider.refresh(context)
    } catch (_: Exception) {}
    rows
}

private val Teal = Color(0xFF00796B)
private val TealDark = Color(0xFF004D40)

@Composable
fun NetraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF4DB6AC), onPrimary = Color(0xFF00201C),
        background = Color(0xFF101414), surface = Color(0xFF182020), onSurface = Color(0xFFE0E6E4),
        surfaceVariant = Color(0xFF22302E)
    ) else lightColorScheme(
        primary = Teal, onPrimary = Color.White,
        background = Color(0xFFF3F7F6), surface = Color.White, onSurface = Color(0xFF16201E),
        surfaceVariant = Color(0xFFE0EEEB)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun EcoScreen(resumeKey: Int) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<AppRow>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var roadmap by remember { mutableStateOf<List<RoadmapItem>?>(null) }

    LaunchedEffect(resumeKey, reload) {
        roadmap = withContext(Dispatchers.IO) { Net.fetchText(Roadmap.URL)?.let { Roadmap.parse(it) } }
        loading = true
        val result = loadRows(context)
        if (result == null) failed = true else { rows = result; failed = false }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Standard header (all Netra apps): 56 dp content height, only app name, version and date/time. Nothing else lives here.
        var clockNow by remember { mutableStateOf(java.util.Date()) }
        LaunchedEffect(Unit) { while (true) { clockNow = java.util.Date(); delay(30_000) } }
        var ecoName by remember { mutableStateOf(NameGate.displayName(context)) }
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { NameGate.refresh(context) }; ecoName = NameGate.displayName(context) }
        val ownVersion = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { null } ?: "Unavailable" }
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Teal, TealDark)))
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(ecoName, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("v$ownVersion", fontSize = 12.sp, color = Color(0xFFD0ECE8), maxLines = 1)
                }
                Text(java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault()).format(clockNow), fontSize = 12.sp, color = Color(0xFFD0ECE8), maxLines = 1)
            }
        }
        val publishedVersions = (rows ?: emptyList()).mapNotNull { r -> r.latest?.let { r.app.id to it.versionName } }.toMap()
        val pendingRoadmap = roadmap?.let { Roadmap.pending(it, publishedVersions) }
        TickerStrip(pendingRoadmap)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (failed && rows == null) {
                Text("Unavailable: could not load the app list. Check your internet connection and tap Check again.", color = MaterialTheme.colorScheme.error)
            }
            if (failed && rows != null) {
                Text("Could not refresh. Showing the last list that loaded.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item(key = "intro") {
                    Column {
                        Text("All Netra apps in one place. Netra by Prayagi Team.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        SelfUpdateButton(onRefresh = { reload++ })
                    }
                }
                items(rows ?: emptyList(), key = { it.app.id }) { row -> AppCard(row, pendingRoadmap) { reload++ } }
                item(key = "usage") { UsageCard() }
                item(key = "crash") { CrashReportCard() }
                item(key = "perms") { PermissionsCard(ecoPermissions()) }
            }
        }
    }
}

@Composable
private fun StatusPill(status: Status) {
    val good = status == Status.UpToDate
    val warn = status == Status.UpdateAvailable
    val bg = when { good -> Color(0xFFD7F0DD); warn -> Color(0xFFFFE9B8); else -> MaterialTheme.colorScheme.surfaceVariant }
    val fg = when { good -> Color(0xFF1B5E20); warn -> Color(0xFF6D4C00); else -> MaterialTheme.colorScheme.onSurface }
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(statusLabel(status), color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppCard(row: AppRow, pendingRoadmap: List<RoadmapItem>?, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    val status = statusForRelease(row.installed?.first, row.installed?.second, row.latest)

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(row.app, 48.dp)
                Spacer(Modifier.padding(start = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (row.app.type.isNotBlank()) Text(row.app.type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                StatusPill(status)
            }
            Spacer(Modifier.height(10.dp))
            Text(row.app.summary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Installed", style = MaterialTheme.typography.labelSmall)
                    Text(row.installed?.second?.ifBlank { "Unavailable" } ?: "Not installed", fontWeight = FontWeight.Medium)
                    Text(
                        if (row.installed != null) "version code " + row.installed.first + " - " + row.app.packageName else "Eco looked for " + row.app.packageName,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Latest", style = MaterialTheme.typography.labelSmall)
                    Text(row.latest?.let { it.versionName + " - " + formatSize(it.size) } ?: "Unavailable", fontWeight = FontWeight.Medium)
                }
            }
            // Next planned release for this app, from the roadmap. Live countdown, approximate, may come earlier or later.
            var tickNow by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { tickNow = System.currentTimeMillis(); delay(1000) } }
            val nextPlan = pendingRoadmap?.let { Roadmap.nextFor(it, row.app.id) }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                Text("Next planned release", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                val planEta = nextPlan?.etaMillis
                if (nextPlan != null && planEta != null) {
                    Text(nextPlan.title, style = MaterialTheme.typography.bodySmall)
                    Text(if (planEta > tickNow) "Approximate ETA: " + Roadmap.countdown(planEta - tickNow) else Roadmap.countdown(0L), fontWeight = FontWeight.Medium)
                    Text("Approximate, it may arrive a little earlier or later.", style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("Unavailable", fontWeight = FontWeight.Medium)
                    Text(if (pendingRoadmap == null) "The roadmap could not be read." else "No planned release with an ETA is set for this app.", style = MaterialTheme.typography.labelSmall)
                }
            }
            val notes = usefulNotes(row.latest?.notes.orEmpty())
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)).padding(10.dp)
                ) {
                    Text("What's new in " + (row.latest?.versionName ?: ""), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(notes.take(600), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            when (status) {
                Status.NotInstalled, Status.UpdateAvailable -> Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        val latest = row.latest ?: return@Button
                        scope.launch {
                            busy = true
                            message = null
                            try {
                                val file = withContext(Dispatchers.IO) { Net.download(context, row.app, latest) { d, t, e -> progress = DownloadText.line(d, t, e) } }
                                Net.install(context, file)
                            } catch (e: Exception) {
                                message = e.message ?: "Download failed."
                            }
                            busy = false
                            progress = null
                            onChanged()
                        }
                    }
                ) {
                    Text(
                        when {
                            busy -> "Downloading..."
                            status == Status.NotInstalled -> "Download and install"
                            else -> "Update"
                        }
                    )
                }
                Status.InstalledNewer -> Text("Installed version is newer than the published one", style = MaterialTheme.typography.bodySmall)
                Status.Unavailable -> Text("Latest version Unavailable right now", style = MaterialTheme.typography.bodySmall)
                Status.UpToDate -> {}
            }
            if (row.installed != null) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            val launch = context.packageManager.getLaunchIntentForPackage(row.app.packageName)
                            if (launch == null) {
                                message = "Cannot open " + row.app.name + ": Android has no screen to start for it."
                            } else {
                                try {
                                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (e: Exception) {
                                    message = "Cannot open " + row.app.name + ": " + (e.message ?: "Android refused.")
                                }
                            }
                        }
                    ) { Text("Open") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + row.app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: Exception) {
                                message = "Cannot start uninstall: " + (e.message ?: "Android refused.")
                            }
                        }
                    ) { Text("Uninstall") }
                }
            }
            message?.let { Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            progress?.let { Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SelfUpdateButton(onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<LatestRelease?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    OutlinedButton(enabled = !busy, onClick = {
        scope.launch {
            busy = true
            message = null
            release = null
            val self = Net.selfApp(context)
            val (installed, latest) = withContext(Dispatchers.IO) {
                Net.installed(context, self.packageName) to Net.fetchLatest(self)
            }
            onRefresh()
            val status = statusForRelease(installed?.first, installed?.second, latest)
            message = selfUpdateMessage(installed?.second.orEmpty().ifBlank { "Unavailable" }, status, latest?.versionName)
            if (status == Status.UpdateAvailable) release = latest
            busy = false
        }
    }) { Text(if (busy) "Checking..." else "Check for update", color = Color.White) }
    message?.let { Text(it, color = Color(0xFFD0ECE8), style = MaterialTheme.typography.bodySmall) }
    progress?.let { Text(it, color = Color(0xFFD0ECE8), style = MaterialTheme.typography.bodySmall) }
    release?.let { r ->
        Button(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                try {
                    val file = withContext(Dispatchers.IO) { Net.download(context, Net.selfApp(context), r) { d, t, e -> progress = DownloadText.line(d, t, e) } }
                    Net.install(context, file)
                } catch (e: Exception) {
                    message = e.message ?: "Update failed."
                }
                busy = false
                progress = null
            }
        }) { Text("Update to " + r.versionName) }
    }
    }
}

@Composable
private fun UsageCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(UsagePing.isEnabled(context)) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Share anonymous usage count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Once a day the app adds 1 to a public counter so the Netra site can show roughly how many people use it. No ID, no location, no files.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = on, onCheckedChange = { on = it; UsagePing.setEnabled(context, it) })
        }
    }
}
