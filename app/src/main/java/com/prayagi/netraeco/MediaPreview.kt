package com.prayagi.netraeco

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Preview media for one app: the promo video and real screenshots, exactly as published in the
 * Eco site's media.json. That file is the single source: when the websites change it, the app
 * shows the change on its own. Only real media is ever listed, so an app with no video and no
 * real screenshots simply shows nothing here.
 */
data class PreviewMedia(
    val videoUrl: String?,
    val videoLabel: String,
    val shots: List<Pair<String, String>>
)

object MediaPreviewStore {
    const val MEDIA_URL = "https://prayagi-store-and-services.github.io/netra-eco/media.json"

    private var cache: Map<String, PreviewMedia>? = null

    /** Preview media may only come from the Netra websites. Anything else is dropped. */
    fun isTrustedMediaUrl(url: String): Boolean =
        url.startsWith("https://prayagi-store-and-services.github.io/")

    /**
     * Parses media.json. Entries with no usable video and no usable screenshots are dropped, and
     * every URL is checked against isTrustedMediaUrl so a bad entry can never point the app at
     * an outside server. Returns an empty map on unreadable JSON.
     */
    fun parse(json: String): Map<String, PreviewMedia> = try {
        val root = JSONObject(json)
        val apps = root.optJSONObject("apps") ?: return emptyMap()
        val out = LinkedHashMap<String, PreviewMedia>()
        for (key in apps.keys()) {
            val entry = apps.optJSONObject(key) ?: continue
            val video = entry.optJSONObject("video")
            val vurl = video?.optString("url").orEmpty()
            val videoUrl = if (vurl.isNotBlank() && isTrustedMediaUrl(vurl)) vurl else null
            val videoLabel = video?.optString("label").orEmpty().ifBlank { "Promo video" }
            val shots = ArrayList<Pair<String, String>>()
            val arr = entry.optJSONArray("screenshots")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    val u = s.optString("url")
                    if (u.isNotBlank() && isTrustedMediaUrl(u)) shots.add(u to s.optString("label"))
                }
            }
            if (videoUrl != null || shots.isNotEmpty()) out[key] = PreviewMedia(videoUrl, videoLabel, shots)
        }
        out
    } catch (e: Exception) {
        emptyMap()
    }

    /** The manifest, from memory when already read this session, else from the website. Null when it cannot be read. */
    suspend fun load(fresh: Boolean = false): Map<String, PreviewMedia>? {
        val held = cache
        if (held != null && !fresh) return held
        val text = withContext(Dispatchers.IO) { Net.fetchText(MEDIA_URL, fresh) } ?: return held
        val parsed = parse(text)
        cache = parsed
        return parsed
    }
}

private val shotCache = HashMap<String, Bitmap?>()

private suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val c = URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        if (c.responseCode != 200) null else c.inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }
}

/**
 * The Play-Store-style preview on an app card: the promo video and a screenshot strip. Rendered
 * only while at least one real medium exists; hidden otherwise, and hidden when the manifest
 * cannot be read (we never invent a preview).
 */
@Composable
fun PreviewBlock(appId: String) {
    var media by remember(appId) { mutableStateOf<PreviewMedia?>(null) }
    LaunchedEffect(appId) {
        media = MediaPreviewStore.load()?.get(appId)
    }
    val m = media ?: return
    if (m.videoUrl == null && m.shots.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text("Preview", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val vurl = m.videoUrl
        if (vurl != null) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val mc = MediaController(ctx)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        setVideoURI(Uri.parse(vurl))
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            )
            Text(m.videoLabel + " - real features only", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
        }
        if (m.shots.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
                items(m.shots, key = { it.first }) { shot ->
                    ShotImage(shot.first, shot.second)
                }
            }
            Text("Real screenshots from the app", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ShotImage(url: String, label: String) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(shotCache[url]) }
    LaunchedEffect(url) {
        if (bmp != null) return@LaunchedEffect
        val got = loadBitmap(url)
        if (got != null) {
            shotCache[url] = got
            bmp = got
        }
    }
    val b = bmp ?: return
    androidx.compose.foundation.Image(
        bitmap = b.asImageBitmap(),
        contentDescription = label.ifBlank { "App screenshot" },
        modifier = Modifier
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    )
}
