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
@Config(sdk = [34], application = Application::class, shadows = [ShadowApkDataSource::class])
class HumanSafetyHoldPlatformTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun app() = CatalogApp("netra-hub", "Human Safety", "", "", HumanSafetyHold.REPO,
        HumanSafetyHold.PACKAGE, "", "")
    private fun release() = LatestRelease("v1.5.0", "1.5.0", 76L, "",
        "https://github.com/" + HumanSafetyHold.REPO + "/releases/download/v1.5.0/app-release.apk", "a".repeat(64), 1L)
    @Test fun exactTagLookupOfflineCannotFallBack() {
        val urls = mutableListOf<String>()
        assertNull(Net.fetchLatest(app()) { url, fresh -> assertTrue(fresh); urls += url; null })
        assertEquals(listOf("https://api.github.com/repos/${HumanSafetyHold.REPO}/releases/tags/v1.5.2"), urls)
    }
    @Test fun malformedExactTagCannotFallBack() {
        val urls = mutableListOf<String>()
        assertNull(Net.fetchLatest(app()) { url, _ -> urls += url; "{}" })
        assertEquals(1, urls.size)
        assertTrue(urls.single().endsWith("/tags/v1.5.2"))
    }
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
        assertFalse(kotlinx.coroutines.runBlocking { DownloadCenter.hasFile(context, a, r) })
    }
    @Test fun malformedRenamedInstallerDoesNotLaunchIntent() {
        val file = File(context.cacheDir, "renamed.apk").apply { writeText("not an APK") }
        try { kotlinx.coroutines.runBlocking { Net.install(context, file) }; fail("invalid archive") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("could not be verified")) }
    }
    @Test fun realRenamedHubArchiveIsIdentifiedAndBlocked() {
        val apk = File(context.cacheDir, "old-hub-copy.apk").apply { File("build/apk-fixtures/renamed-hub.apk").copyTo(this, overwrite = true) }
        assertTrue("CI must download public Hub fixture", apk.exists())
        val pkg = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
        assertEquals(HumanSafetyHold.PACKAGE, pkg)
        try { kotlinx.coroutines.runBlocking { Net.install(context, apk) }; fail("Hub archive must be quarantined") }
        catch (e: IllegalStateException) { assertEquals(HumanSafetyHold.MESSAGE, e.message) }
    }
    @Test fun validOtherAppArchivePassesQuarantine() {
        val apk = File(context.cacheDir, "other-copy.apk").apply { File("build/apk-fixtures/other-app.apk").copyTo(this, overwrite = true) }
        assertTrue("CI must download public KBC fixture", apk.exists())
        val pkg = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
        assertNotNull(pkg)
        assertFalse(HumanSafetyHold.matches(packageName = pkg!!))
        HumanSafetyHold.requireAllowed(packageName = pkg)
        // Robolectric denies unknown-source install by default; reaching that check proves hold did not block KBC.
        try { kotlinx.coroutines.runBlocking { Net.install(context, apk) } }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("could not be verified")) }
    }
}
