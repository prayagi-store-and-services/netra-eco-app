package com.prayagi.netraeco

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real rendered pixels for the repo-less Charge path: the full catalog screen, each card state,
 * and the protected-installer refusals, drawn by the production composables in CI.
 * The fixture APKs are the same pinned public archives the platform tests use.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi", sdk = [34], application = Application::class)
class ChargeScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val direct get() = DIRECT_APPS.getValue("netra-charge")
    private val sha = "1119987250ea79e3d2d0c6b8895dd77d25c7078ec9dfd449b0028ee7400204ae"
    private val size = 656862L
    private val foreignSha = "e1349cb3ad8688587b52d7c4125ee67c3472a55cd3fa85229a58734f24671c16"
    private val key = "netra-charge-v0.1.0.apk"

    private fun chargeApp() = CatalogApp(
        "netra-charge", "Netra Charge", "Battery and charger voice announcements",
        "A very small free app that speaks four things: Connected, Disconnected, Full charge and Low battery (15% by default, adjustable). For Android, with a Shortcuts setup guide for iPhone.",
        "", "com.netracharge.app", direct.latestJsonUrl, "", emptyList(), direct.downloadUrl, "charge"
    )

    private fun release(tag: String = "v0.1.0", name: String = "0.1.0", code: Long = 1L, notes: String = "") =
        LatestRelease(tag, name, code, notes, direct.downloadUrl, sha, size)

    private fun resetState() {
        val c = rule.activity
        c.getSharedPreferences("netra_eco_downloads", 0).edit().clear().commit()
        File(c.cacheDir, "updates").deleteRecursively()
        DownloadCenter.items.value = emptyMap()
    }

    private fun placeFixture(srcName: String): File {
        val src = File("build/apk-fixtures/$srcName")
        assertTrue("CI must download $srcName", src.exists())
        val dst = File(File(rule.activity.cacheDir, "updates").apply { mkdirs() }, key)
        src.copyTo(dst, overwrite = true)
        return dst
    }

    private fun saveRecord(recSize: Long, recSha: String) {
        rule.activity.getSharedPreferences("netra_eco_downloads", 0).edit()
            .putString(key, listOf("com.netracharge.app", "0.1.0", 1L, recSize, "Netra Charge", "netra-charge", recSha, "v0.1.0").joinToString("|")).commit()
    }

    private fun doneItem() {
        DownloadCenter.items.value = mapOf(key to DownloadCenter.Item(
            key, "netra-charge", "Netra Charge", "0.1.0", 1L, "com.netracharge.app", size, size, null, DownloadCenter.DONE))
    }

    private fun shot(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/outputs/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun fullScreenShowsChargeCatalogCard() {
        resetState()
        val row = AppRow(chargeApp(), null, release())
        rule.setContent { MaterialTheme { EcoScreen(0, catalogLoader = { listOf(row) }, textLoader = { null }) } }
        rule.onNodeWithText("Netra Charge").assertIsDisplayed()
        shot("charge-fullscreen-available-360")
        // Below-the-fold safe: the full screen is taller than the viewport, so existence is asserted and the pixel is the display evidence.
        rule.onNodeWithText("Download").assertExists()
    }

    @Test fun catalogCardAvailable() {
        resetState()
        rule.setContent { MaterialTheme { AppCard(AppRow(chargeApp(), null, release()), null, false, false) {} } }
        rule.onNodeWithText("Netra Charge").assertIsDisplayed()
        shot("charge-card-available-360")
        rule.onNodeWithText("Not installed").assertIsDisplayed()
        rule.onNodeWithText("Download").assertIsDisplayed()
    }

    @Test fun catalogCardInstalledUpToDate() {
        resetState()
        rule.setContent { MaterialTheme { AppCard(AppRow(chargeApp(), 1L to "0.1.0", release()), null, false, false) {} } }
        rule.onNodeWithText("version code 1 - com.netracharge.app").assertIsDisplayed()
        rule.onNodeWithText("Open").assertIsDisplayed()
        rule.onNodeWithText("Uninstall").assertIsDisplayed()
        rule.onAllNodesWithText("Download").assertCountEquals(0)
        shot("charge-card-installed-360")
    }

    @Test fun catalogCardUpdateAvailable() {
        resetState()
        val newer = release("v0.1.1", "0.1.1", 2L, "Second release notes placeholder for the update state.")
        rule.setContent { MaterialTheme { AppCard(AppRow(chargeApp(), 1L to "0.1.0", newer), null, false, false) {} } }
        rule.onNodeWithText("Update").assertIsDisplayed()
        rule.onNodeWithText("What's new in 0.1.1").assertIsDisplayed()
        shot("charge-card-update-360")
    }

    @Test fun catalogCardDownloadErrorShown() {
        resetState()
        DownloadCenter.items.value = mapOf(key to DownloadCenter.Item(
            key, "netra-charge", "Netra Charge", "0.1.0", 1L, "com.netracharge.app", 0L, size, null,
            DownloadCenter.FAILED, "No internet, or the server did not answer. Check your connection and try again."))
        rule.setContent { MaterialTheme { AppCard(AppRow(chargeApp(), null, release()), null, false, false) {} } }
        rule.onNodeWithText("Download").assertIsDisplayed()
        rule.onNodeWithText("No internet, or the server did not answer. Check your connection and try again.").assertIsDisplayed()
        shot("charge-card-error-360")
    }

    @Test fun protectedRejectionMissingRecordShowsRefusal() {
        resetState()
        val file = placeFixture("other-app.apk")
        doneItem()
        rule.setContent { MaterialTheme { AppCard(AppRow(chargeApp(), null, release()), null, false, false) {} } }
        rule.onNodeWithText("Downloaded - tap to install").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("The installer could not be matched to a verified download. Download it again from its app card.").assertIsDisplayed()
        assertFalse("refused file must be deleted", file.exists())
        shot("charge-protected-rejection-no-record-360")
    }

    @Test fun protectedRejectionSwappedPackageShowsRefusal() {
        resetState()
        val file = placeFixture("other-app.apk")
        saveRecord(file.length(), foreignSha)
        doneItem()
        rule.setContent { MaterialTheme { AppCard(AppRow(chargeApp(), null, release()), null, false, false) {} } }
        rule.onNodeWithText("Downloaded - tap to install").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("The downloaded file is not the expected app, so it was not installed.").assertIsDisplayed()
        assertFalse("refused file must be deleted", file.exists())
        shot("charge-protected-rejection-swapped-360")
    }

    @Test fun downloadsCardListsSavedChargeInstaller() {
        resetState()
        placeFixture("charge.apk")
        saveRecord(size, sha)
        rule.setContent { MaterialTheme { DownloadManagerCard(emptyList()) } }
        rule.waitForIdle()
        rule.onNodeWithText("Netra Charge 0.1.0").assertIsDisplayed()
        rule.onNodeWithText("Deleted automatically after it is installed.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Install").assertIsDisplayed()
        rule.onNodeWithText("Delete").assertIsDisplayed()
        shot("charge-downloads-card-360")
    }
}
