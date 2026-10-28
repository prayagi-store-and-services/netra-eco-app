package com.prayagi.netraeco

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Production component with a deterministic, explicitly labelled image fixture. No phone claim. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi", sdk = [34], application = android.app.Application::class)
class ScreenshotOnlyPreviewTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun screenshotSurvivesButVideoControlsDoNotExist() {
        val url = "https://prayagi-store-and-services.github.io/fixture.png"
        val media = MediaPreviewStore.parse("""{"apps":{"fixture":{"video":{"url":"https://prayagi-store-and-services.github.io/promo.mp4"},"screenshots":[{"url":"$url","label":"Screenshot layout fixture"}]}}}""")
        val cacheField = MediaPreviewStore::class.java.getDeclaredField("cache").apply { isAccessible = true }
        val previous = cacheField.get(MediaPreviewStore)
        val image = Bitmap.createBitmap(330, 660, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(Color.rgb(13, 35, 57))
            val paint = Paint().apply { color = Color.WHITE; textSize = 24f; isAntiAlias = true }
            drawText("Screenshot layout fixture", 16f, 70f, paint)
            drawText("Not a phone screenshot", 16f, 110f, paint)
        }
        @Suppress("UNCHECKED_CAST")
        val images = Class.forName("com.prayagi.netraeco.MediaPreviewKt")
            .getDeclaredField("shotCache").apply { isAccessible = true }.get(null) as android.util.LruCache<String, Bitmap>
        images.put(url, image)
        try {
            cacheField.set(MediaPreviewStore, media)
            rule.setContent { MaterialTheme { PreviewBlock("fixture") } }
            rule.onNodeWithText("Screenshots").assertIsDisplayed()
            rule.onNodeWithText("Real screenshots from the app").assertIsDisplayed()
            listOf("Play", "Tap to play", "Promo video", "Video preview").forEach {
                rule.onNodeWithText(it).assertDoesNotExist()
            }
            rule.waitForIdle()
            rule.runOnIdle {
                val view = rule.activity.window.decorView
                val result = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(result))
                val file = File("build/outputs/screenshot-only-preview.png").apply { parentFile?.mkdirs() }
                file.outputStream().use { assertTrue(result.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                result.recycle()
            }
        } finally {
            cacheField.set(MediaPreviewStore, previous)
            images.remove(url)
        }
    }
}
