package com.prayagi.netraeco

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private val iconCache = HashMap<String, Bitmap?>()

private fun drawableToBitmap(d: Drawable): Bitmap {
    val w = d.intrinsicWidth.coerceAtLeast(1)
    val h = d.intrinsicHeight.coerceAtLeast(1)
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    d.setBounds(0, 0, w, h)
    d.draw(c)
    return b
}

/**
 * The app's real launcher icon: from the installed app when it is installed, else the launcher image published in
 * that app's own repository. When neither can be read, a plain letter badge is shown, which is not the app's icon.
 */
@Composable
fun AppIcon(app: CatalogApp, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    var bmp by remember(app.id) { mutableStateOf<Bitmap?>(iconCache[app.id]) }
    LaunchedEffect(app.id) {
        if (bmp != null) return@LaunchedEffect
        val got = withContext(Dispatchers.IO) {
            try {
                drawableToBitmap(context.packageManager.getApplicationIcon(app.packageName))
            } catch (e: Exception) {
                try {
                    if (!isTrustedRepo(app.repo)) null else {
                        val u = "https://raw.githubusercontent.com/" + app.repo + "/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp"
                        val c = URL(u).openConnection() as java.net.HttpURLConnection
                        c.connectTimeout = 8000
                        c.readTimeout = 8000
                        if (c.responseCode != 200) null else c.inputStream.use { BitmapFactory.decodeStream(it) }
                    }
                } catch (e2: Exception) {
                    null
                }
            }
        }
        if (got != null) { iconCache[app.id] = got; bmp = got }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b.asImageBitmap(), contentDescription = app.name + " icon", modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text(app.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
