package com.prayagi.netraeco

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HumanSafetyHoldPlatformTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun app() = CatalogApp("netra-hub", "Human Safety", "", "", HumanSafetyHold.REPO,
        HumanSafetyHold.PACKAGE, "", "")
    private fun release() = LatestRelease("v1.5.0", "1.5.0", 76L, "",
        "https://github.com/" + HumanSafetyHold.REPO + "/releases/download/v1.5.0/app-release.apk", "a".repeat(64), 1L)
    @Test fun hubReleaseLookupCannotFallBackToGitHubOffline() { assertNull(Net.fetchLatest(app())) }
    @Test fun hubDownloadFailsBeforeNetwork() {
        try { Net.download(context, app(), release()); fail("quarantine") }
        catch (e: IllegalStateException) { assertEquals(HumanSafetyHold.MESSAGE, e.message) }
        assertFalse(Net.downloadsRunning())
    }
    @Test fun hubCannotStartDownloadCenterJob() {
        val a = app(); val r = release()
        DownloadCenter.start(context, a, r)
        val item = DownloadCenter.items.value[DownloadCenter.keyFor(a, r)]!!
        assertEquals(DownloadCenter.FAILED, item.state)
        assertEquals(HumanSafetyHold.MESSAGE, item.error)
    }
    @Test fun savedHubFileCannotBeReused() {
        val a = app(); val r = release()
        val file = File(File(context.cacheDir, "updates").apply { mkdirs() }, DownloadCenter.keyFor(a, r))
        file.writeBytes(byteArrayOf(0))
        assertFalse(DownloadCenter.hasFile(context, a, r))
    }
    @Test fun malformedRenamedInstallerDoesNotLaunchIntent() {
        val file = File(context.cacheDir, "renamed.apk").apply { writeText("not an APK") }
        try { Net.install(context, file); fail("invalid archive") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("could not be verified")) }
    }
}
