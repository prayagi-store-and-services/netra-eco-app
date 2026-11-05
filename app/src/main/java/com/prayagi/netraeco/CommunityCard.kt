package com.prayagi.netraeco

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Logo artwork from Simple Icons (CC0), kept locally: no remote image requests. */
private val socialLogoResources = listOf(
    R.drawable.social_youtube, R.drawable.social_instagram, R.drawable.social_facebook,
    R.drawable.social_x, R.drawable.social_telegram
)

@Composable
fun SocialLogoRow(open: ((String) -> Unit)? = null, counts: Map<String, Int> = emptyMap(), modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("social-logo-row"),
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            Spacer(Modifier.width(4.dp))
            CommunityLinks.social.forEachIndexed { index, link ->
                FilledTonalIconButton(
                    modifier = Modifier.size(58.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White),
                    onClick = {
                        failed = false
                        try {
                            if (open != null) open(link.url) else CommunityBrowser.open(context, link.url)
                        } catch (_: Exception) { failed = true }
                    }
                ) {
                    val count = counts[link.label.lowercase()] ?: 0
                    BadgedBox(badge = { if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) } }) {
                        Icon(painterResource(socialLogoResources[index]), contentDescription = if (count > 0) "${link.label}: $count posts today" else link.label,
                            modifier = Modifier.size(28.dp), tint = Color.Unspecified)
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
        }
        if (failed) Text("Could not open this link. Check that a browser is available.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun CommunityCard(open: ((String) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SocialLogoRow(open)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { AppCommunityLinks("netra-eco", open) }
        }
    }
}
