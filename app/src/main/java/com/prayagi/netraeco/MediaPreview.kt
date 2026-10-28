package com.prayagi.netraeco

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/** Screenshot-only app previews. Website video metadata is deliberately ignored. */
data class PreviewMedia(val shots: List<Pair<String, String>>)

object MediaPreviewStore {
    const val MEDIA_URL = "https://prayagi-store-and-services.github.io/netra-eco/media.json"

    private var cache: Map<String, PreviewMedia>? = null

    /** Preview media may only come from the Netra websites. Anything else is dropped. */
    fun isTrustedMediaUrl(url: String): Boolean =
        url.startsWith("https://prayagi-store-and-services.github.io/")

    /** Ignore all video metadata. Only trusted screenshots can create a preview entry. */
    fun parse(json: String): Map<String, PreviewMedia> = try {
        val root = JSONObject(json)
        val apps = root.optJSONObject("apps") ?: return emptyMap()
        val out = LinkedHashMap<String, PreviewMedia>()
        for (key in apps.keys()) {
            val entry = apps.optJSONObject(key) ?: continue
            val shots = ArrayList<Pair<String, String>>()
            val arr = entry.optJSONArray("screenshots")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    val u = s.optString("url")
                    if (u.isNotBlank() && isTrustedMediaUrl(u)) shots.add(u to s.optString("label"))
                }
            }
            if (shots.isNotEmpty()) out[key] = PreviewMedia(shots)
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

/** Small memory-bounded cache: full-res shots would cost tens of MB each and stall the list. */
private val shotCache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun open(url: String): java.net.HttpURLConnection? = try {
    val c = URL(url).openConnection() as java.net.HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 8000
    if (c.responseCode == 200) c else null
} catch (e: Exception) {
    null
}

/** Decodes at roughly [targetHeightPx] instead of full camera resolution; two passes, bounds first. */
private suspend fun loadBitmap(url: String, targetHeightPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(url)?.inputStream?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = PreviewFocus.sampleSize(bounds.outWidth, bounds.outHeight, targetHeightPx)
        }
        open(url)?.inputStream?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }
}

/** Screenshot-only preview. There is no video player, poster or autoplay path. */
@Composable
fun PreviewBlock(appId: String) {
    var media by remember(appId) { mutableStateOf<PreviewMedia?>(null) }
    LaunchedEffect(appId) { media = MediaPreviewStore.load()?.get(appId) }
    val m = media ?: return
    if (m.shots.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text("Screenshots", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items(m.shots, key = { it.first }) { shot -> ShotImage(shot.first, shot.second) }
        }
        Text("Real screenshots from the app", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ShotImage(url: String, label: String) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(shotCache.get(url)) }
    val density = LocalDensity.current
    LaunchedEffect(url) {
        if (bmp != null) return@LaunchedEffect
        val targetPx = with(density) { 220.dp.roundToPx() }
        val got = loadBitmap(url, targetPx)
        if (got != null) {
            shotCache.put(url, got)
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
