package com.prayagi.netraeco

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.material3.MaterialTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi", sdk = [34], application = android.app.Application::class)
class BrandScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun ecosystemNameFitsProductionHeader() {
        rule.setContent { MaterialTheme { EcoHeader(NameGate.PUBLIC_NAME, "1.2.26", java.util.Date(1792067400000L)) } }
        rule.onNodeWithText("Netra Ecosystem").assertIsDisplayed()
        rule.onNodeWithText("v1.2.26 | Netra Ecosystem").assertIsDisplayed()
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/outputs/ecosystem-header.png").apply { parentFile.mkdirs() }
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
