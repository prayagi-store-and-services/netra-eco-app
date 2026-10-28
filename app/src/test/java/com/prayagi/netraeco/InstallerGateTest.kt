package com.prayagi.netraeco

import android.app.Application
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, shadows = [ShadowApkDataSource::class])
class InstallerGateTest {
    private val pkg = "com.aistudio.reasoningquiz.tark"
    private fun release(file: File) = LatestRelease("v1.1.37", "1.1.37", 0L, "", "", InstallerGate.hash(file), file.length())
    @Test fun sameLengthTamperedCachedBytesAreRejectedAndDeleted() = kotlinx.coroutines.runBlocking {
        val c = RuntimeEnvironment.getApplication()
        val app = CatalogApp("kbc", "KBC", "", "", "$ORG/KBC", pkg, "", "")
        val f = File(File(c.cacheDir, "updates").apply { mkdirs() }, "kbc-v1.1.37.apk").apply { writeText("good") }
        val r = release(f)
        assertTrue(DownloadCenter.hasFile(c, app, r))
        f.writeText("evil")
        assertFalse(DownloadCenter.hasFile(c, app, r)); assertFalse(f.exists())
    }
    @Test fun wrongPackageWrongVersionAndWrongSignerFailClosed() {
        val signer = InstallerGate.expectedSigner(pkg)!!
        assertTrue(InstallerGate.identityMatches(pkg, "1.0.0", 1, pkg, "1.0.0", 1, setOf(signer)))
        assertFalse(InstallerGate.identityMatches(pkg, "1.0.0", 1, "wrong.pkg", "1.0.0", 1, setOf(signer)))
        assertFalse(InstallerGate.identityMatches(pkg, "1.0.0", 1, pkg, "1.0.0", 1, setOf("b".repeat(64))))
        assertFalse(InstallerGate.identityMatches(pkg, "1.0.0", 1, pkg, "1.0.1", 2, setOf(signer)))
        assertNull(InstallerGate.expectedSigner("unknown.app"))
    }
    @Test fun commonFinalGateAcceptsPinnedFixtureAndRejectsUnboundInstaller() {
        val c = RuntimeEnvironment.getApplication()
        val original = File("build/apk-fixtures/other-app.apk")
        assertTrue(original.exists())
        val f = File(c.cacheDir, "kbc-v1.1.37.apk").apply { original.copyTo(this, overwrite = true) }
        val app = CatalogApp("kbc", "KBC", "", "", "$ORG/KBC", pkg, "", "")
        val archive = InstallerGate.archive(c, f)!!
        InstallerGate.remember(c, app, release(f).copy(versionName = archive.versionName!!))
        InstallerGate.requireValid(c, f, archive)
        f.writeBytes(f.readBytes().also { it[50] = (it[50].toInt() xor 1).toByte() })
        try { InstallerGate.requireValid(c, f, archive); fail("tampered") } catch (_: IllegalStateException) { }
        assertFalse(f.exists())
        val unbound = File(c.cacheDir, "unbound.apk").apply { original.copyTo(this, overwrite = true) }
        try { InstallerGate.requireValid(c, unbound, archive); fail("no expected metadata") } catch (_: IllegalStateException) { }
        assertFalse(unbound.exists())
    }
}
