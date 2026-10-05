package com.prayagi.netraeco

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** One permission row: its name, the plain reason it is needed, its live status and what a tap opens. */
data class PermItem(
    val name: String,
    val reason: String,
    val status: (Context) -> String,
    val open: ((Context) -> Unit)?
)

internal fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun runtimeStatus(context: Context, permission: String): String =
    try {
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) "Allowed" else "Not allowed"
    } catch (e: Exception) {
        "Unavailable"
    }

internal fun installStatus(context: Context): String =
    try { if (context.packageManager.canRequestPackageInstalls()) "Allowed" else "Not allowed" } catch (e: Exception) { "Unavailable" }

internal fun openInstallSettings(context: Context) {
    val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(i) } catch (e: Exception) { context.startActivity(appSettingsIntent(context)) }
}

internal fun ecoPermissions(): List<PermItem> = listOf(
    PermItem(
        "Internet",
        "Used to read the list of Netra apps and their latest versions, and to download an app or update you ask for. Nothing about you is sent except the anonymous usage count if you leave it on.",
        { "Always allowed (normal permission)" },
        null
    ),
    PermItem(
        "Install apps",
        "Used only when you tap Install or Update on an app card, so Android can install that Netra app. Tap to open the Android page where you can allow or stop it.",
        { installStatus(it) },
        { openInstallSettings(it) }
    ),
    PermItem(
        "Uninstall apps",
        "Used only when you tap Uninstall on an app card; Android still asks you to confirm. This is a normal permission, always allowed.",
        { "Always allowed (normal permission)" },
        null
    )
)

/** Settings card listing every permission the app uses. Status is read from Android each time the app comes back to the screen; no timer. */
@Composable
fun PermissionsCard(items: List<PermItem>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val lc = (context as? ComponentActivity)?.lifecycle
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lc?.addObserver(obs)
        onDispose { lc?.removeObserver(obs) }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Permissions", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("What the app uses and why. Tap a row to open its Android page.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { item ->
                val st = remember(tick) { item.status(context) }
                val m = if (item.open != null) Modifier.fillMaxWidth().clickable { item.open.invoke(context) } else Modifier.fillMaxWidth()
                Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name + "  -  " + st, fontWeight = FontWeight.SemiBold)
                    Text(item.reason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
