package com.prayagi.netraeco

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Downloads that live outside any screen: several apps can download at the same time, the list survives the
 * screen being redrawn, a finished file waits here until the user taps Install, and it is deleted once the
 * installed version matches (or when the user deletes it).
 */
object DownloadCenter {
    const val DOWNLOADING = 0
    const val DONE = 1
    const val FAILED = 2

    data class Item(
        val key: String, val appId: String, val name: String, val versionName: String, val versionCode: Long,
        val packageName: String, val done: Long, val total: Long, val eta: Long?, val state: Int, val error: String? = null
    )

    val items = MutableStateFlow<Map<String, Item>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val lastEta = ConcurrentHashMap<String, Pair<Long, Long>>()
    private const val PREFS = "netra_eco_downloads"

    fun keyFor(app: CatalogApp, release: LatestRelease) = "${app.id}-${release.tag}.apk"
    private fun dir(c: Context) = File(c.cacheDir, "updates").apply { mkdirs() }
    private fun put(i: Item) { items.value = items.value + (i.key to i) }

    /** The time left never goes up while a download runs (it only goes down, or stays Unavailable). */
    internal fun smoothEta(key: String, raw: Long?, nowMs: Long): Long? {
        if (raw == null) return lastEta[key]?.let { (v, at) -> maxOf(1L, v - (nowMs - at) / 1000L) }
        val prev = lastEta[key]
        val shown = if (prev == null) raw else minOf(raw, maxOf(1L, prev.first - (nowMs - prev.second) / 1000L))
        lastEta[key] = shown to nowMs
        return shown
    }

    fun start(context: Context, app: CatalogApp, release: LatestRelease) {
        val c = context.applicationContext
        val key = keyFor(app, release)
        synchronized(this) {
            if (jobs[key]?.isActive == true) return
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key, listOf(app.packageName, release.versionName, release.versionCode, release.size, app.name, app.id).joinToString("|")).apply()
            lastEta.remove(key)
            put(Item(key, app.id, app.name, release.versionName, release.versionCode, app.packageName, 0L, release.size, null, DOWNLOADING))
            jobs[key] = scope.launch {
                try {
                    Net.download(c, app, release) { d, t, e ->
                        put(Item(key, app.id, app.name, release.versionName, release.versionCode, app.packageName, d, t, smoothEta(key, e, System.currentTimeMillis()), DOWNLOADING))
                    }
                    put(Item(key, app.id, app.name, release.versionName, release.versionCode, app.packageName, release.size, release.size, null, DONE))
                } catch (e: Exception) {
                    put(Item(key, app.id, app.name, release.versionName, release.versionCode, app.packageName, 0L, release.size, null, FAILED, plainFailure(e, "Download failed.")))
                }
            }
        }
    }

    /** Lists finished files left on the phone (kept until installed) and drops partial or unknown files. */
    fun refresh(c: Context) {
        if (Net.downloadsRunning() && jobs.values.none { it.isActive }) return
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val map = items.value.toMutableMap()
        dir(c).listFiles()?.forEach { f ->
            if (jobs[f.name]?.isActive == true) return@forEach
            val p = prefs.getString(f.name, null)?.split("|")
            val size = p?.getOrNull(3)?.toLongOrNull()
            if (p == null || p.size < 6 || size == null || f.length() != size) { f.delete(); prefs.edit().remove(f.name).apply(); map.remove(f.name); return@forEach }
            if (map[f.name]?.state != DONE) {
                map[f.name] = Item(f.name, p[5], p[4], p[1], p[2].toLongOrNull() ?: 0L, p[0], size, size, null, DONE)
            }
        }
        // Items whose file is gone (not downloading, not failed) disappear.
        map.entries.removeAll { (k, v) -> v.state == DONE && !File(dir(c), k).exists() }
        items.value = map
    }

    /** Deletes finished files whose app is now installed at that version or newer, and returns how many were removed. */
    fun cleanFinished(c: Context): Int {
        refresh(c)
        var n = 0
        items.value.values.filter { it.state == DONE }.forEach { i ->
            val inst = Net.installed(c, i.packageName)
            if (inst != null && (inst.second == i.versionName || (i.versionCode > 0L && inst.first >= i.versionCode))) {
                if (delete(c, i.key)) n++
            }
        }
        return n
    }

    fun delete(c: Context, key: String): Boolean {
        val ok = try { File(dir(c), key).let { !it.exists() || it.delete() } } catch (_: Exception) { false }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
        items.value = items.value - key
        return ok
    }

    fun file(c: Context, key: String) = File(dir(c), key)
}

private fun mb(b: Long) = String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)

/** The button on one app card: download (several can run at once), then Install from the finished file. */
@Composable
fun AppDownloadButton(row: AppRow, status: Status) {
    val context = LocalContext.current
    val latest = row.latest ?: return
    val key = DownloadCenter.keyFor(row.app, latest)
    val all by DownloadCenter.items.collectAsState()
    val item = all[key]
    var note by remember { mutableStateOf<String?>(null) }
    Column {
        when (item?.state) {
            DownloadCenter.DOWNLOADING -> {
                Button(enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Downloading...") }
                Spacer(Modifier.height(6.dp))
                ProgressBlock(item)
            }
            DownloadCenter.DONE -> Button(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), onClick = {
                try { Net.install(context, DownloadCenter.file(context, key)) } catch (e: Exception) { note = plainFailure(e, "Install failed.") }
            }) { Text("Downloaded - tap to install") }
            else -> {
                Button(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), onClick = { DownloadCenter.start(context, row.app, latest) }) {
                    Text(if (status == Status.NotInstalled) "Download" else "Update")
                }
                if (item?.state == DownloadCenter.FAILED) { Spacer(Modifier.height(4.dp)); Text(item?.error ?: "Download failed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        note?.let { Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ProgressBlock(i: DownloadCenter.Item) {
    val frac = if (i.total > 0L) (i.done.toFloat() / i.total).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(progress = frac, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(4.dp))
    Text(DownloadText.line(i.done, i.total, i.eta) + (if (i.total > 0L) " - " + mb(i.done) + " of " + mb(i.total) else ""), style = MaterialTheme.typography.bodySmall)
}

/** Self update: start the download here, the finished file then shows in Downloads with an Install button. */
@Composable
fun SelfDownloadButton(release: LatestRelease) {
    val context = LocalContext.current
    val all by DownloadCenter.items.collectAsState()
    val self = remember { Net.selfApp(context) }
    val k = DownloadCenter.keyFor(self, release)
    val item = all[k]
    Button(enabled = item?.state != DownloadCenter.DOWNLOADING, onClick = {
        if (item?.state == DownloadCenter.DONE) {
            try { Net.install(context, DownloadCenter.file(context, k)) } catch (_: Exception) {}
        } else DownloadCenter.start(context, self, release)
    }) {
        Text(when (item?.state) {
            DownloadCenter.DOWNLOADING -> "Downloading..."
            DownloadCenter.DONE -> "Downloaded - tap to install"
            else -> "Update to " + release.versionName
        })
    }
}

/** Downloads screen section: running downloads with progress, finished files with Install and Delete, and Update all. */
@Composable
fun DownloadManagerCard(rows: List<AppRow>) {
    val context = LocalContext.current
    val all by DownloadCenter.items.collectAsState()
    LaunchedEffect(Unit) { DownloadCenter.refresh(context) }
    val updatable = rows.filter {
        it.latest != null && statusForRelease(it.installed?.first, it.installed?.second, it.latest) == Status.UpdateAvailable &&
            all[DownloadCenter.keyFor(it.app, it.latest)]?.state.let { s -> s != DownloadCenter.DOWNLOADING && s != DownloadCenter.DONE }
    }
    if (all.isEmpty() && updatable.isEmpty()) return
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Downloads", style = MaterialTheme.typography.titleMedium)
            if (updatable.size > 1) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = { updatable.forEach { DownloadCenter.start(context, it.app, it.latest!!) } }) {
                    Text("Download all updates (" + updatable.size + ")")
                }
            }
            all.values.sortedBy { it.name }.forEach { i ->
                Column {
                    Text(i.name + " " + i.versionName, style = MaterialTheme.typography.bodyMedium)
                    when (i.state) {
                        DownloadCenter.DOWNLOADING -> ProgressBlock(i)
                        DownloadCenter.DONE -> {
                            Text("Downloaded, " + mb(i.total) + ". Deleted automatically after it is installed.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { try { Net.install(context, DownloadCenter.file(context, i.key)) } catch (_: Exception) {} }) { Text("Install") }
                                OutlinedButton(onClick = { DownloadCenter.delete(context, i.key) }) { Text("Delete") }
                            }
                        }
                        else -> {
                            Text(i.error ?: "Download failed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { DownloadCenter.delete(context, i.key) }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}
