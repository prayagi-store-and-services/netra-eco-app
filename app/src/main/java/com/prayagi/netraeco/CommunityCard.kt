package com.prayagi.netraeco

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
private fun PublicLink(label: String, url: String, open: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
        failed = false
        try {
            if (open != null) open(url)
            else CommunityBrowser.open(context, url)
        } catch (_: Exception) { failed = true }
    }) { Text(label) }
    if (failed) Text("Could not open this link. Check that a browser is available.", color = MaterialTheme.colorScheme.error)
}

@Composable
fun AppCommunityLinks(appId: String, open: ((String) -> Unit)? = null) {
    val site = CommunityLinks.website(appId) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("App website and feedback", style = MaterialTheme.typography.titleSmall)
        PublicLink("Open this app's website", site, open)
        PublicLink("Rate this app or read reviews", CommunityLinks.reviews(appId)!!, open)
        PublicLink("Private complaint or feedback", CommunityLinks.contact(appId)!!, open)
        Text("Reviews open a public web form: phone model and any name or area you enter are public, and a copy is emailed through FormSubmit. Use the separate private contact form for private feedback. Not Google Play ratings; no reward or required follow.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CommunityCard(open: ((String) -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connect with Netra Ecosystem", style = MaterialTheme.typography.titleLarge)
            Text("Open our official profiles. Nothing is followed, liked or posted automatically.", style = MaterialTheme.typography.bodySmall)
            CommunityLinks.social.forEach { PublicLink(it.icon + "  " + it.label, it.url, open) }
            AppCommunityLinks("netra-eco", open)
        }
    }
}
