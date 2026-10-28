package com.prayagi.netraeco

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

/** A released app or a coming-soon app in one shared alphabetical list; a coming-soon card keeps its place when the app goes live. */
sealed interface EcoEntry {
    val displayName: String
    val itemKey: String
    data class Live(val row: AppRow) : EcoEntry {
        override val displayName: String get() = row.app.name
        override val itemKey: String get() = "l-" + row.app.id
    }
    data class Soon(val up: UpcomingApp) : EcoEntry {
        override val displayName: String get() = up.name
        override val itemKey: String get() = "up-" + up.id
    }
}

/** Released and coming-soon apps merged into one A-Z list (Netra prefix ignored, letter by letter). */
private fun mergedEcoEntries(rows: List<AppRow>, upcoming: List<UpcomingApp>, pendingRoadmap: List<RoadmapItem>? = null): List<EcoEntry> =
    AppSort.current(rows).map { EcoEntry.Live(it) } + Upcoming.sorted(upcoming, pendingRoadmap).map { EcoEntry.Soon(it) }

class MainActivity : ComponentActivity() {
    private val resumeCount = mutableIntStateOf(0)
    private val currentRequest = mutableIntStateOf(0)
    private val routeAppId = mutableStateOf<String?>(null)

    internal val requestedAppForTest: String? get() = routeAppId.value
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("eco_requested_app", routeAppId.value)
        super.onSaveInstanceState(outState)
    }
    private fun closeAppPage() {
        routeAppId.value = null
        intent?.data = null
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeAppId.value = EcoAppRoute.appId(intent.dataString)
        if (intent.action == CatalogReleaseAlert.ACTION_CURRENT) currentRequest.intValue++
        if (intent.data == null) UpdateAlert.handle(this, intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == CatalogReleaseAlert.ACTION_CURRENT) currentRequest.intValue++
        routeAppId.value = if (savedInstanceState?.containsKey("eco_requested_app") == true) savedInstanceState.getString("eco_requested_app") else EcoAppRoute.appId(intent?.dataString)
        if (com.prayagi.netraeco.geo.GeoGuard.isBlocked(this)) {
            val pad = (24 * resources.displayMetrics.density).toInt()
            val box = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(pad, pad, pad, pad)
            }
            box.addView(android.widget.TextView(this).apply {
                text = "This app is not available in your region."
                textSize = 20f
                gravity = android.view.Gravity.CENTER
            })
            box.addView(android.widget.Button(this).apply {
                text = "Close"
                setOnClickListener { finishAffinity() }
            })
            setContentView(box)
            return
        }
        com.prayagi.netraeco.festival.JayMataDi.greetIfDue(this)
        if (intent?.data != null) intent.removeExtra(UpdateAlert.EXTRA_AUTO_UPDATE)
        UpdateAlert.start(this)
        // Keep the half-hour time announcements scheduled across app updates and process restarts.
        TimeSpeakOut.scheduleNext(this)
        // Warm the voice engine while the app is open so a slot announcement starts the instant
        // the alarm fires instead of waiting for the engine to start first.
        if (TimeSpeakOut.isEnabled(this)) TimeSpeaker.get(this)
        CrashReporter.install(this)
        setContent {
            NetraTheme { EcoScreen(resumeCount.intValue, currentRequestKey = currentRequest.intValue, requestedAppId = routeAppId.value, onCloseApp = { closeAppPage() }) }
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
    val self = Net.selfApp(context)
    val rows = coroutineScope {
        // The catalog may list Netra Ecosystem itself; this phone's own entry comes from selfApp only.
        (listOf(self) + apps.filter { it.id != self.id && it.packageName != self.packageName }).map { app ->
            async {
                AppRow(app, Net.installed(context, app.packageName), Net.fetchLatest(app))
            }
        }.awaitAll()
    }
    val sortedRows = AppSort.current(rows)
    CatalogReleaseAlert.observe(context, catalogJson, rows)
    try {
        val statuses = sortedRows.map { statusForRelease(it.installed?.first, it.installed?.second, it.latest) }
        val lines = sortedRows.mapIndexed { i, r -> EcoWidgetStore.lineFor(r.app.name, r.installed?.second, r.latest?.versionName, statuses[i]) }
        EcoWidgetStore.save(context, lines, statuses.count { it == Status.UpdateAvailable })
        EcoWidgetProvider.refresh(context)
    } catch (_: Exception) {}
    sortedRows
}

private val Teal = Color(0xFF00796B)
private val TealDark = Color(0xFF004D40)

@Composable
fun NetraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (RedesignGate.isOn()) {
        if (dark) darkColorScheme(
            primary = Color(0xFFFFB74D), onPrimary = Color(0xFF2B1700),
            background = Color(0xFF0E1224), surface = Color(0xFF171C33), onSurface = Color(0xFFE8EAF6),
            surfaceVariant = Color(0xFF232A47)
        ) else lightColorScheme(
            primary = Color(0xFF283593), onPrimary = Color.White,
            background = Color(0xFFF4F5FB), surface = Color.White, onSurface = Color(0xFF1A1C2E),
            surfaceVariant = Color(0xFFE3E6F5)
        )
    } else if (dark) darkColorScheme(
        primary = Color(0xFF4DB6AC), onPrimary = Color(0xFF00201C),
        background = Color(0xFF101414), surface = Color(0xFF182020), onSurface = Color(0xFFE0E6E4),
        surfaceVariant = Color(0xFF22302E)
    ) else lightColorScheme(
        primary = Teal, onPrimary = Color.White,
        background = Color(0xFFF3F7F6), surface = Color.White, onSurface = Color(0xFF16201E),
        surfaceVariant = Color(0xFFE0EEEB)
    )
    MaterialTheme(colorScheme = com.prayagi.netraeco.festival.festiveScheme(colors), content = content)
}

@Composable
fun EcoScreen(
    resumeKey: Int,
    catalogLoader: suspend (Context) -> List<AppRow>? = { loadRows(it) },
    textLoader: (String) -> String? = { Net.fetchText(it) },
    selfChecker: suspend (Context) -> Pair<String, LatestRelease?> = { checkSelfUpdate(it) },
    requestedAppId: String? = null,
    onCloseApp: () -> Unit = {},
    currentRequestKey: Int = 0
) {
    val context = LocalContext.current
    // Keeps an explicit update check or installer verification alive across section changes.
    val screenScope = rememberCoroutineScope()
    val installActions = remember { EcoInstallActions() }
    val updateCheck = remember { EcoUpdateCheck() }
    var rows by remember { mutableStateOf<List<AppRow>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var roadmap by remember { mutableStateOf<List<RoadmapItem>?>(null) }
    var upcoming by remember { mutableStateOf<List<UpcomingApp>>(emptyList()) }
    var futureSelected by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(currentRequestKey) { if (currentRequestKey > 0) futureSelected = false }
    var currentQuery by rememberSaveable { mutableStateOf("") }
    var futureQuery by rememberSaveable { mutableStateOf("") }
    var currentCat by rememberSaveable { mutableStateOf<AppCategory?>(null) }
    var futureCat by rememberSaveable { mutableStateOf<AppCategory?>(null) }
    val query = if (futureSelected) futureQuery else currentQuery
    val cat = if (futureSelected) futureCat else currentCat
    val setQuery: (String) -> Unit = { if (futureSelected) futureQuery = it else currentQuery = it }
    val setCat: (AppCategory?) -> Unit = { if (futureSelected) futureCat = it else currentCat = it }
    val currentListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val futureListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(resumeKey, reload) {
        upcoming = withContext(Dispatchers.IO) { textLoader(Net.CATALOG_URL)?.let { Upcoming.parse(it) } ?: emptyList() }
        roadmap = withContext(Dispatchers.IO) { textLoader(Roadmap.URL)?.let { Roadmap.parse(it) } }
        loading = true
        val result = catalogLoader(context)
        if (result == null) failed = true else { rows = result; failed = false }
        loading = false
    }

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Standard header (all Netra apps): 56 dp content height, only app name, version and date/time. Nothing else lives here.
        var clockNow by remember { mutableStateOf(java.util.Date()) }
        LaunchedEffect(Unit) { while (true) { clockNow = java.util.Date(); delay(30_000) } }
        var ecoName by remember { mutableStateOf(NameGate.displayName(context)) }
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { NameGate.refresh(context) }; ecoName = NameGate.displayName(context) }
        val ownVersion = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { null } ?: "Unavailable" }
        EcoHeader(ecoName, ownVersion, clockNow)
        val publishedVersions = (rows ?: emptyList()).mapNotNull { r -> r.latest?.let { r.app.id to it.versionName } }.toMap()
        val pendingRoadmap = roadmap?.let { Roadmap.pending(it, publishedVersions) }
        TickerStrip(pendingRoadmap)
        // Deepak (16 Oct): "Request a new app" sits next to the header. The header itself stays standard (name, version, date/time only), so this slim row lives directly under it.
        RequestAppEntry()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (failed && rows == null) {
                Text("Unavailable: could not load the app list. Check your internet connection and tap Check again.", color = MaterialTheme.colorScheme.error)
            }
            if (failed && rows != null) {
                Text("Could not refresh. Showing the last list that loaded.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            if (requestedAppId == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!futureSelected) Button(onClick = { futureSelected = false }, modifier = Modifier.weight(1f)) { Text("Current", maxLines = 1) }
                else OutlinedButton(onClick = { futureSelected = false }, modifier = Modifier.weight(1f)) { Text("Current", maxLines = 1) }
                if (futureSelected) Button(onClick = { futureSelected = true }, modifier = Modifier.weight(1f)) { Text("Future", maxLines = 1) }
                else OutlinedButton(onClick = { futureSelected = true }, modifier = Modifier.weight(1f)) { Text("Future", maxLines = 1) }
            }
            val sectionRows = if (futureSelected) emptyList() else rows ?: emptyList()
            val sectionUpcoming = if (futureSelected) upcoming else emptyList()
            val searching = query.isNotBlank()
            val shownRows = sectionRows.filter { matchesQuery(query, it.app.name, it.app.type, it.app.summary) }
            val shownUpcoming = sectionUpcoming.filter { matchesQuery(query, it.name, it.type, it.summary) }
            val catRows = sectionRows.map { it to AppCategories.classify(it.app.name, it.app.type, it.app.summary, it.app.categories) }
            val catUp = sectionUpcoming.map { it to AppCategories.classify(it.name, it.type, it.summary) }
            val usedCats = AppCategory.values().filter { c -> catRows.any { c in it.second } || catUp.any { c in it.second } }
            val listState = if (futureSelected) futureListState else currentListState
            if (requestedAppId != null) {
                androidx.activity.compose.BackHandler(onBack = onCloseApp)
                LazyColumn(Modifier.weight(1f).testTag("handoff-app"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { OutlinedButton(onClick = onCloseApp) { Text("Back to all apps") } }
                    item { Text("App update page", style = MaterialTheme.typography.titleLarge) }
                    val requestedRow = rows?.firstOrNull { it.app.id == requestedAppId }
                    when {
                        loading -> item { Text("Loading app details...") }
                        failed -> item { Text("Unavailable: could not refresh app details. No download started."); OutlinedButton(onClick = { reload++ }) { Text("Check again") } }
                        requestedRow == null -> item { Text("Unavailable: this app is not in the current catalog. No download started.") }
                        else -> item { AppCard(requestedRow, pendingRoadmap, actionScope = screenScope, installActions = installActions) { reload++ } }
                    }
                }
            } else {
            LazyColumn(modifier = Modifier.weight(1f).testTag(if (futureSelected) "future-list" else "current-list"), state = listState, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item(key = "section-controls") {
                    Column {
            Text(if (futureSelected) "Planned apps. Not released; no download or install. Dates are estimates, not promises." else "Released apps. Download availability is checked for each app.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (rows != null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { setQuery(it.take(60)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search apps") },
                    trailingIcon = { if (searching) TextButton(onClick = { setQuery("") }) { Text("Clear") } }
                )
                Spacer(Modifier.height(8.dp))
            }
            if (rows != null && !searching && usedCats.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cat == null) Button(onClick = { setCat(null) }) { Text("All", maxLines = 1) } else OutlinedButton(onClick = { setCat(null) }) { Text("All", maxLines = 1) }
                    usedCats.forEach { c ->
                        if (cat == c) Button(onClick = { setCat(null) }) { Text(c.label, maxLines = 1) } else OutlinedButton(onClick = { setCat(c) }) { Text(c.label, maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
                    }
                }
                if (searching) {
                    if (shownRows.isEmpty() && shownUpcoming.isEmpty()) {
                        item(key = "nomatch") { Text("No app matches \"" + query.trim() + "\".", style = MaterialTheme.typography.bodyMedium) }
                    }
                    items(mergedEcoEntries(shownRows, shownUpcoming, pendingRoadmap), key = { it.itemKey }) { e ->
                        when (e) {
                            is EcoEntry.Live -> AppCard(e.row, pendingRoadmap, actionScope = screenScope, installActions = installActions) { reload++ }
                            is EcoEntry.Soon -> UpcomingCard(e.up, pendingRoadmap)
                        }
                    }
                    return@LazyColumn
                }
                val picked = cat
                if (picked != null) {
                    val inRows = catRows.filter { picked in it.second }.map { it.first }
                    val inUp = catUp.filter { picked in it.second }.map { it.first }
                    item(key = "cat-head") { Text(picked.label + ": " + (inRows.size + inUp.size) + (if (inRows.size + inUp.size == 1) " app" else " apps") + ". An app can be in more than one group, based on what it does.", style = MaterialTheme.typography.bodyMedium) }
                    items(mergedEcoEntries(inRows, inUp, pendingRoadmap), key = { it.itemKey }) { e ->
                        when (e) {
                            is EcoEntry.Live -> AppCard(e.row, pendingRoadmap, actionScope = screenScope, installActions = installActions) { reload++ }
                            is EcoEntry.Soon -> UpcomingCard(e.up, pendingRoadmap)
                        }
                    }
                    return@LazyColumn
                }
                if (!futureSelected) {
                item(key = "intro") {
                    Column {
                        Text("All Netra apps in one place. Netra by Prayagi Team.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Updates are checked automatically when the app list loads.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item(key = "downloads") { DownloadManagerCard(rows ?: emptyList(), actionScope = screenScope, installActions = installActions) }
                item(key = "community") { CommunityCard() }
                item(key = "trikaal") { TrikaalCard() }
                item(key = "festival") { FestivalBannerCard(modifier = Modifier.fillMaxWidth()) }
                }
                if (futureSelected && sectionUpcoming.isEmpty()) item(key = "future-empty") { Text("No planned apps are listed right now.") }
                items(mergedEcoEntries(sectionRows, sectionUpcoming, pendingRoadmap), key = { it.itemKey }) { e ->
                    when (e) {
                        is EcoEntry.Live -> AppCard(e.row, pendingRoadmap, actionScope = screenScope, installActions = installActions) { reload++ }
                        is EcoEntry.Soon -> UpcomingCard(e.up, pendingRoadmap)
                    }
                }
                if (!futureSelected) {
                item(key = "timespeak") { TimeSpeakCard() }
                item(key = "usage") { UsageCard() }
                item(key = "crash") { CrashReportCard() }
                item(key = "perms") { PermissionsCard(ecoPermissions()) }
                }
            }
            }
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
fun AppCard(row: AppRow, pendingRoadmap: List<RoadmapItem>?, actionScope: kotlinx.coroutines.CoroutineScope? = null, installActions: EcoInstallActions? = null, onChanged: () -> Unit) {
    val context = LocalContext.current
    val localScope = rememberCoroutineScope()
    val scope = actionScope ?: localScope
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
                    Text(AppCategories.classify(row.app.name, row.app.type, row.app.summary, row.app.categories).joinToString(" · ") { it.label }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                StatusPill(status)
            }
            Spacer(Modifier.height(10.dp))
            Text(row.app.summary, style = MaterialTheme.typography.bodyMedium)
            AppCommunityLinks(row.app.id)
            Spacer(Modifier.height(10.dp))
            if (HumanSafetyHold.matches(row.app.repo, row.app.packageName) && !HumanSafetyHold.allowed(row.latest)) {
                Text("Human Safety release unavailable", style = MaterialTheme.typography.titleMedium)
                Text(HumanSafetyHold.MESSAGE, color = MaterialTheme.colorScheme.error)
                Button(enabled = false, onClick = {}) { Text("Download and update paused") }
                return@Column
            }
            PreviewBlock(row.app.id)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Installed", style = MaterialTheme.typography.labelSmall)
                    Text(row.installed?.second?.ifBlank { "Unavailable" } ?: "Not installed", fontWeight = FontWeight.Medium)
                    Text(
                        if (row.installed != null) "version code " + row.installed.first + " - " + row.app.packageName else "Eco looked for " + row.app.packageName,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Latest", style = MaterialTheme.typography.labelSmall)
                    Text(row.latest?.let { it.versionName + " - " + formatSize(it.size) } ?: "Unavailable", fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
            // Next planned release for this app, from the roadmap. Live countdown, approximate, may come earlier or later.
            val nextPlan = pendingRoadmap?.let { Roadmap.nextFor(it, row.app.id) }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                Text("Next planned release", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                val planEta = nextPlan?.etaMillis
                if (nextPlan != null && planEta != null) {
                    Text(nextPlan.title, style = MaterialTheme.typography.bodySmall)
                    LiveCountdown(planEta, true)
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
                Status.NotInstalled, Status.UpdateAvailable -> AppDownloadButton(row, status, actionScope = scope, installActions = installActions)
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
                                    message = "Cannot open " + row.app.name + ". " + plainFailure(e, "Android refused.")
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
                                message = "Cannot start uninstall. " + plainFailure(e, "Android refused.")
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

private suspend fun checkSelfUpdate(context: Context): Pair<String, LatestRelease?> = withContext(Dispatchers.IO) {
    val self = Net.selfApp(context)
    val installed = Net.installed(context, self.packageName)
    val latest = Net.fetchLatest(self)
    val status = statusForRelease(installed?.first, installed?.second, latest)
    selfUpdateMessage(installed?.second.orEmpty().ifBlank { "Unavailable" }, status, latest?.versionName) to
        latest.takeIf { status == Status.UpdateAvailable }
}

@Composable
private fun UsageCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(UsagePing.isEnabled(context)) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Share anonymous usage count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Once a day the app adds 1 to a public counter so the Netra site can show roughly how many people use it. No ID, no location, no files. Off by default: nothing is sent unless you turn it on.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = on, onCheckedChange = { on = it; UsagePing.setEnabled(context, it) })
        }
    }
}

/** Shared production header, rendered by the brand regression test at a narrow phone width. */
@Composable
fun EcoHeader(ecoName: String, ownVersion: String, clockNow: java.util.Date) {
    Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Teal, TealDark)))
                .statusBarsPadding()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.1f
                val date = java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault()).format(clockNow)
                if (stacked) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ecoName, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("v$ownVersion | Netra Ecosystem", fontSize = 12.sp, color = Color(0xFFD0ECE8))
                        Text(date, fontSize = 12.sp, color = Color(0xFFD0ECE8))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(ecoName, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("v$ownVersion | Netra Ecosystem", fontSize = 12.sp, color = Color(0xFFD0ECE8), maxLines = 1)
                        }
                        Text(date, fontSize = 12.sp, color = Color(0xFFD0ECE8), maxLines = 1)
                    }
                }
            }
        }
}
