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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h1000dp-xxhdpi", sdk = [34], application = Application::class)
class HumanSafetyHoldScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun shot(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/outputs/human-safety-hold-$name.png").apply { parentFile.mkdirs() }
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    @Test fun offlineCatalogMissingFullScreenLargeFontHasWarning() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MaterialTheme { EcoScreen(0, catalogLoader = { null }, textLoader = { null }) }
            }
        }
        rule.onNodeWithText("Human Safety download and update paused. " + HumanSafetyHold.MESSAGE).assertIsDisplayed()
        rule.onAllNodesWithText("Open", useUnmergedTree = true).assertCountEquals(0)
        shot("offline-full-320-large")
    }
    @Test fun savedDownloadHasWarningDeleteButNoInstallOrOpen() {
        val context = rule.activity
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val key = "netra-hub-v1.5.0.apk"
        File(dir, key).writeBytes(byteArrayOf(0))
        context.getSharedPreferences("netra_eco_downloads", 0).edit().clear()
            .putString(key, listOf(HumanSafetyHold.PACKAGE, "1.5.0", "76", "1", "Human Safety", "netra-hub", "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d").joinToString("|")).commit()
        DownloadCenter.refresh(context)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MaterialTheme { DownloadManagerCard(emptyList()) }
            }
        }
        rule.onNodeWithText(HumanSafetyHold.MESSAGE).assertIsDisplayed()
        rule.onNodeWithText("Delete").assertIsDisplayed()
        rule.onAllNodesWithText("Install").assertCountEquals(0)
        rule.onAllNodesWithText("Open").assertCountEquals(0)
        shot("saved-320-large")
    }
    @Test fun otherSavedAppStillHasInstallAndDeleteControls() {
        val context = rule.activity
        val key = "kbc-v1.1.37.apk"
        File(File(context.cacheDir, "updates").apply { mkdirs() }, key).writeBytes(byteArrayOf(0))
        context.getSharedPreferences("netra_eco_downloads", 0).edit().clear()
            .putString(key, "com.aistudio.reasoningquiz.tark|1.1.37|46|1|KBC|kbc|6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d").commit()
        DownloadCenter.items.value = emptyMap()
        DownloadCenter.refresh(context)
        rule.setContent { MaterialTheme { DownloadManagerCard(emptyList()) } }
        rule.onNodeWithText("Install").assertIsDisplayed()
        rule.onNodeWithText("Delete").assertIsDisplayed()
        rule.onAllNodesWithText(HumanSafetyHold.MESSAGE).assertCountEquals(0)
        shot("otherapp-control-320")
    }
}
