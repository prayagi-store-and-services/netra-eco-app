package com.prayagi.netraeco

import android.content.Context
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
        setContent {
            MaterialTheme { EcoScreen(resumeCount.intValue) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read installed versions when the user comes back from the system installer.
        resumeCount.intValue = resumeCount.intValue + 1
    }
}

private suspend fun loadRows(context: Context): List<AppRow>? = withContext(Dispatchers.IO) {
    val catalogJson = Net.fetchText(Net.CATALOG_URL) ?: return@withContext null
    val apps = Net.parseCatalog(catalogJson)
    coroutineScope {
        apps.map { app ->
            async {
                val latest = Net.fetchText(app.latestJsonUrl)?.let { Net.parseLatest(it, app.repo) }
                AppRow(app, Net.installed(context, app.packageName), latest)
            }
        }.awaitAll()
    }
}

@Composable
fun EcoScreen(resumeKey: Int) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<AppRow>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(resumeKey, reload) {
        loading = true
        val result = loadRows(context)
        if (result == null) failed = true else { rows = result; failed = false }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Netra Eco", style = MaterialTheme.typography.headlineMedium)
        Text("All Netra apps in one place. Netra by Prayagi Team.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { reload++ }, enabled = !loading) { Text(if (loading) "Checking..." else "Check again") }
        Spacer(Modifier.height(8.dp))
        if (failed && rows == null) {
            Text("Unavailable: could not load the app list. Check your internet connection and tap Check again.")
        }
        if (failed && rows != null) {
            Text("Could not refresh. Showing the last list that loaded.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows ?: emptyList(), key = { it.app.id }) { row -> AppCard(row) { reload++ } }
        }
    }
}

@Composable
fun AppCard(row: AppRow, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val status = statusFor(row.installed?.first, row.latest?.versionCode)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(row.app.name, style = MaterialTheme.typography.titleMedium)
            if (row.app.type.isNotBlank()) Text(row.app.type, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(row.app.summary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("Installed: " + (row.installed?.second?.ifBlank { "version unavailable" } ?: "not installed"))
            Text("Latest: " + (row.latest?.let { it.versionName + " (" + formatSize(it.size) + ")" } ?: "Unavailable"))
            val notes = row.latest?.notes.orEmpty()
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("What changed: " + notes.take(400), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    Status.UpToDate -> Text("Up to date")
                    Status.InstalledNewer -> Text("Installed version is newer than the published one")
                    Status.Unavailable -> Text("Latest version Unavailable right now")
                    Status.NotInstalled, Status.UpdateAvailable -> Button(enabled = !busy, onClick = {
                        val latest = row.latest ?: return@Button
                        scope.launch {
                            busy = true
                            message = null
                            try {
                                val file = withContext(Dispatchers.IO) { Net.download(context, row.app, latest) }
                                Net.install(context, file)
                            } catch (e: Exception) {
                                message = e.message ?: "Download failed."
                            }
                            busy = false
                            onChanged()
                        }
                    }) {
                        Text(
                            when {
                                busy -> "Downloading..."
                                status == Status.NotInstalled -> "Download and install"
                                else -> "Update"
                            }
                        )
                    }
                }
            }
            message?.let { Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
