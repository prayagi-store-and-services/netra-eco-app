package com.prayagi.netraeco

import android.app.Application
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UpdateActionTest {
    @Test fun explicitTapDownloadsOnceThenHandsToInstallerAndFailureDoesNotInstall() = runBlocking {
        val c = RuntimeEnvironment.getApplication()
        val release = LatestRelease("v1.0.1", "1.0.1", 2L, "", "", "a".repeat(64), 1)
        val row = AppRow(CatalogApp("fixture", "Netra Fixture", "", "", "", "fixture", "", ""), 1L to "1.0.0", release)
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val file = File(c.cacheDir, "fixture.apk")
        val actions = EcoInstallActions(updateFile = { _, _ -> events += "download"; gate.await(); file }, installer = { _, f -> assertEquals(file, f); events += "install" })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        actions.update(scope, c, row); actions.update(scope, c, row)
        assertEquals(listOf("download"), events)
        gate.complete(Unit); yield()
        assertEquals(listOf("download", "install"), events)
        val fail = EcoInstallActions(updateFile = { _, _ -> error("Checksum mismatch") }, installer = { _, _ -> events += "bad-install" })
        fail.update(scope, c, row); yield()
        assertNotNull(fail.state(DownloadCenter.keyFor(row.app, release)).error)
        assertFalse(events.contains("bad-install"))
        scope.cancel()
    }
}
