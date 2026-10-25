package com.prayagi.netraeco

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A failed install must never look inert: SelfDownloadButton and DownloadManagerCard surface the
 * factual plain failure (strict-verify reject, unregistered cache, missing unknown-source
 * permission). No installer shortcut and no Hub bypass is added.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h1000dp-xxhdpi", sdk = [34], application = Application::class)
class InstallFailureSurfaceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun failedSelfInstallSurfacesPlainFailure() {
        val context = rule.activity
        val self = Net.selfApp(context)
        val release = LatestRelease("v9.9.9", "9.9.9", 99L, "", "https://example.invalid/x.apk", "00", 3L)
        val key = DownloadCenter.keyFor(self, release)
        File(File(context.cacheDir, "updates").apply { mkdirs() }, key).writeBytes(byteArrayOf(1, 2, 3))
        DownloadCenter.items.value = mapOf(key to DownloadCenter.Item(
            key, self.id, self.name, release.versionName, release.versionCode, self.packageName, 3L, 3L, null, DownloadCenter.DONE))
        rule.setContent { MaterialTheme { SelfDownloadButton(release) } }
        rule.onNodeWithText("Downloaded - tap to install").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Install failed.", substring = true).assertIsDisplayed()
    }

    @Test fun failedSavedInstallSurfacesPlainFailure() {
        val context = rule.activity
        val key = "kbc-v1.1.37.apk"
        val payload = byteArrayOf(1, 2, 3)
        File(File(context.cacheDir, "updates").apply { mkdirs() }, key).writeBytes(payload)
        // refresh() deletes any cached installer whose bytes fail the registry size/hash check (strict
        // updates), so the registry must declare the payload's real SHA-256 for the entry to survive.
        // Install still fails later - the payload is not a parseable APK - which is the failure surfaced.
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        context.getSharedPreferences("netra_eco_downloads", 0).edit().clear()
            .putString(key, listOf("com.aistudio.reasoningquiz.tark", "1.1.37", "37", "3", "KBC", "kbc",
                sha).joinToString("|")).commit()
        DownloadCenter.refresh(context)
        rule.setContent { MaterialTheme { DownloadManagerCard(emptyList()) } }
        rule.onNodeWithText("Install").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Install failed.", substring = true).assertIsDisplayed()
    }
}
