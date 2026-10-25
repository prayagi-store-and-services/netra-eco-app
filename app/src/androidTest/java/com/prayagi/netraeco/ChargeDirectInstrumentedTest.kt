package com.prayagi.netraeco

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof for the repo-less Charge path. The REAL signed public v0.1.0 APK reaches the
 * device two sealed ways, both pinned to the same SHA-256/size:
 *  - CI bundles it as a GENERATED androidTest asset before the test APK build; each test copies it
 *    from instrumentation assets into the TARGET app's cacheDir/updates and re-verifies hash+size
 *    (the verifyDirectFile cases; Android 34 scoped storage never touches this path);
 *  - CI pushes a second copy to /data/local/tmp/eco-fixture-charge.apk solely for the shell-side
 *    `pm install` in installedSameOrNewerRejected, guarded by sha256sum/stat; the target app never
 *    reads that path.
 * apksigner/aapt identity is checked in a separate CI step. These tests run the PRODUCTION
 * Net.verifyDirectFile and Net.install against the real PackageManager, real archive parsing, real
 * signing certificates and the real FileProvider - no shadowed signing info. JVM tests keep
 * parser/dispatch/refusal coverage.
 *
 * Coverage limit: verifiedInstallFiresRealProviderInstallerIntent intercepts startActivity, so it
 * proves the real content:// URI, MIME type and FLAG_GRANT_READ_URI_PERMISSION that production
 * hands to the system - it does NOT prove the external system-installer process actually opens the
 * stream. That external read-grant stays untested even when all tests here pass.
 */
@RunWith(AndroidJUnit4::class)
class ChargeDirectInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val direct get() = DIRECT_APPS.getValue("netra-charge")
    private val sha = "1119987250ea79e3d2d0c6b8895dd77d25c7078ec9dfd449b0028ee7400204ae"
    private val size = 656862L
    private val code = 1L
    private val assetName = "eco-fixture-charge.apk"
    private val localTmpFixture = "/data/local/tmp/eco-fixture-charge.apk"

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { inp ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fixture(name: String = "netra-charge-v0.1.0.apk"): File {
        val dst = File(File(context.cacheDir, "updates").apply { mkdirs() }, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetName).use { inp ->
            dst.outputStream().use { inp.copyTo(it) }
        }
        assertEquals("pinned fixture size", size, dst.length())
        assertEquals("pinned fixture sha-256", sha, sha256(dst))
        return dst
    }

    private fun registerRecord() = DownloadCenter.register(
        context,
        CatalogApp("netra-charge", "Netra Charge", "", "", "", "com.netracharge.app",
            direct.latestJsonUrl, "", emptyList(), direct.downloadUrl, "charge"),
        LatestRelease("v0.1.0", "0.1.0", code, "", direct.downloadUrl, sha, size)
    )

    @Test fun realSignedArchivePassesFullVerification() {
        // The real certificate is parsed on a real device and must match the pinned signer.
        val file = fixture()
        Net.verifyDirectFile(context, file, direct, size, sha, code)
        assertTrue(file.exists())
    }

    @Test fun wrongExpectedShaRejected() {
        val file = fixture()
        try {
            Net.verifyDirectFile(context, file, direct, size, "0".repeat(64), code)
            fail("must refuse a wrong SHA-256")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("file changed"))
        }
        assertFalse(file.exists())
    }

    @Test fun wrongExpectedVersionCodeRejected() {
        val file = fixture()
        try {
            Net.verifyDirectFile(context, file, direct, size, sha, code + 1)
            fail("must refuse a wrong version code")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("version did not match"))
        }
        assertFalse(file.exists())
    }

    @Test fun installedSameOrNewerRejected() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.executeShellCommand("pm uninstall com.netracharge.app").close()
        // The shell-side copy is a separate pinned artifact; the target app never reads /data/local/tmp.
        val shellSha = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("sha256sum $localTmpFixture")).bufferedReader().readText()
        assertTrue("local-tmp fixture sha-256 must match the pin: $shellSha", shellSha.startsWith(sha))
        val shellSize = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("stat -c %s $localTmpFixture")).bufferedReader().readText().trim()
        assertEquals("pinned fixture size", size.toString(), shellSize)
        val out = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("pm install -r $localTmpFixture")).bufferedReader().readText()
        assertTrue("fixture must install on the emulator: $out", out.contains("Success"))
        try {
            val file = fixture()
            try {
                Net.verifyDirectFile(context, file, direct, size, sha, code)
                fail("must refuse the same version")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!, e.message!!.contains("same or newer"))
            }
            assertFalse(file.exists())
        } finally {
            ui.executeShellCommand("pm uninstall com.netracharge.app").close()
        }
    }

    @Test fun verifiedInstallFiresRealProviderInstallerIntent() {
        // CI grants unknown-sources for this app first; the real FileProvider must build the system installer intent.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow").close()
        assertTrue(context.packageManager.canRequestPackageInstalls())
        registerRecord()
        val file = fixture()
        val intents = mutableListOf<Intent>()
        val spy = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                intents.add(intent)
            }
        }
        Net.install(spy, file)
        assertTrue("installer intent must fire once bindings verify", file.exists())
        assertEquals(1, intents.size)
        val i = intents.single()
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals("application/vnd.android.package-archive", i.type)
        assertTrue(i.dataString!!, i.dataString!!.startsWith("content://" + context.packageName + ".updates/"))
        assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
