package com.prayagi.netraeco

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual regression at w320dp with 1.5x font scale (Robolectric fixture, not a live device):
 * the header must grow instead of clipping the version line, and the "Check for update"
 * label must render with readable contrast (it was hard-coded white on a pale outlined button).
 * The version line is matched by its testTag: the app name also contains "Netra Ecosystem",
 * so a plain substring text query is ambiguous. Pixels come from the proven decorView.draw
 * path used by the reviewed CI fixtures; captureToImage hangs in this Robolectric environment,
 * and decorView pixels are window-relative while compose boundsInRoot are content-view-relative,
 * so the scan area is offset by the content view's on-screen origin.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h1000dp-xxhdpi", sdk = [34], application = Application::class)
class EcoHeaderVisualTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun renderOfflineLargeFont() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MaterialTheme { EcoScreen(0, catalogLoader = { null }, textLoader = { null }) }
            }
        }
    }

    private fun shot(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/outputs/eco-header-$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun largeFontVersionLineStaysInsideHeader() {
        renderOfflineLargeFont()
        val header = rule.onNodeWithTag("eco-header").fetchSemanticsNode().boundsInRoot
        val version = rule.onNodeWithTag("eco-header-version").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "version line clipped: bottom ${version.bottom} beyond header bottom ${header.bottom}",
            version.bottom <= header.bottom + 0.5f
        )
        assertTrue("version line must have real height", version.height > 0f)
        shot("version-visible-320-large")
    }

    @Test fun updateButtonLabelHasReadableContrast() {
        renderOfflineLargeFont()
        val bounds = rule.onNodeWithText("Check for update").fetchSemanticsNode().boundsInRoot
        // Draw the content view itself, not the decorView: its pixels align exactly with
        // compose boundsInRoot, so no status-bar/window offset guessing.
        val content = rule.activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(bitmap))
        var dark = 0
        val left = bounds.left.toInt().coerceIn(0, bitmap.width - 1)
        val right = bounds.right.toInt().coerceIn(left + 1, bitmap.width)
        val top = bounds.top.toInt().coerceIn(0, bitmap.height - 1)
        val bottom = bounds.bottom.toInt().coerceIn(top + 1, bitmap.height)
        for (y in top until bottom) for (x in left until right) {
            val p = bitmap.getPixel(x, y)
            // Glyph ink must be meaningfully darker than the pale outlined-button background
            // (near-white surface, channel sum ~765). The theme's label inks are teal 0xFF00796B
            // (sum 228) or festive indigo 0xFF283593 (sum 240); the original bug was white ink
            // (765) on that pale background, which this threshold still rejects.
            if (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p) < 450) dark++
        }
        bitmap.recycle()
        assertTrue("button label is unreadable (white-on-pale) - found only $dark dark pixels", dark > 50)
        shot("button-contrast-320-large")
    }
}
