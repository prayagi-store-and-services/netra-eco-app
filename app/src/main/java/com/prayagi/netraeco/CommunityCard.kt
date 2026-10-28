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
            else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        Text("Opens a web page. Ratings are real community submissions, not Google Play ratings. No reward or required follow.", style = MaterialTheme.typography.bodySmall)
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
