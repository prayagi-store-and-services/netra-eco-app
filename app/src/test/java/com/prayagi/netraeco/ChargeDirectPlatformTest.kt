package com.prayagi.netraeco

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Archive-level checks for the repo-less Charge path, against the real public v0.1.0 APK
 * (CI downloads it as build/apk-fixtures/charge.apk with a pinned SHA-256).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChargeDirectPlatformTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val direct get() = DIRECT_APPS.getValue("netra-charge")

    private val sha = "1119987250ea79e3d2d0c6b8895dd77d25c7078ec9dfd449b0028ee7400204ae"
    private val size = 656862L
    private val code = 1L

    private fun freshFixture(name: String = "netra-charge-v0.1.0.apk"): File {
        val src = File("build/apk-fixtures/charge.apk")
        assertTrue("CI must download the public Charge fixture", src.exists())
        val dst = File(File(context.cacheDir, "updates").apply { mkdirs() }, name)
        src.copyTo(dst, overwrite = true)
        return dst
    }

    private fun chargeApp() = CatalogApp("netra-charge", "Netra Charge", "", "", "", "com.netracharge.app",
        direct.latestJsonUrl, "", emptyList(), direct.downloadUrl, "charge")

    private fun chargeRelease() = LatestRelease("v0.1.0", "0.1.0", code, "", direct.downloadUrl, sha, size)

    private fun expectFail(file: File, part: String, block: () -> Unit) {
        try { block(); fail("must refuse: $part") }
        catch (e: IllegalStateException) { assertTrue(part, e.message!!.contains(part)) }
        assertFalse("failed file must be deleted", file.exists())
    }

    @Test fun realChargeArchiveMatchesAllowlistFacts() {
        val apk = File("build/apk-fixtures/charge.apk")
        assertTrue("CI must download the public Charge fixture", apk.exists())
        assertEquals(size, apk.length())
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(apk.absolutePath, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)!!
        assertEquals("com.netracharge.app", info.packageName)
        assertEquals(code, androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info))
    }

    @Test fun validChargeFilePassesFullVerification() {
        val file = freshFixture()
        Net.verifyDirectFile(context, file, direct, size, sha, code)
        assertTrue(file.exists())
    }

    @Test fun wrongSizeRejected() {
        val file = freshFixture()
        expectFail(file, "file changed") { Net.verifyDirectFile(context, file, direct, size + 1, sha, code) }
    }

    @Test fun wrongHashRejected() {
        val file = freshFixture()
        expectFail(file, "file changed") { Net.verifyDirectFile(context, file, direct, size, "0".repeat(64), code) }
    }

    @Test fun tamperedFileRejected() {
        val file = freshFixture()
        val bytes = file.readBytes()
        bytes[100] = (bytes[100] + 1).toByte()
        file.writeBytes(bytes)
        expectFail(file, "file changed") { Net.verifyDirectFile(context, file, direct, size, sha, code) }
    }

    @Test fun wrongVersionCodeRejected() {
        val file = freshFixture()
        expectFail(file, "version did not match") { Net.verifyDirectFile(context, file, direct, size, sha, code + 1) }
    }

    @Test fun sameOrOlderThanInstalledRejected() {
        val pi = PackageInfo().apply {
            packageName = "com.netracharge.app"
            versionName = "0.1.0"
            longVersionCode = code
        }
        shadowOf(context.packageManager).installPackage(pi)
        val file = freshFixture()
        expectFail(file, "same or newer") { Net.verifyDirectFile(context, file, direct, size, sha, code) }
    }

    @Test fun installWithoutSavedRecordRefused() {
        val file = freshFixture("netra-charge-v0.9.9.apk")
        expectFail(file, "verified download") { Net.install(context, file) }
    }

    @Test fun swappedRepoApkUnderChargeKeyRejected() {
        // A different app's APK placed under the Charge key must hit full validation and fail,
        // never sail through the repo install path. Dispatch follows the trusted record, not the archive.
        DownloadCenter.register(context, chargeApp(), chargeRelease())
        val kbc = File("build/apk-fixtures/other-app.apk")
        assertTrue("CI must download public KBC fixture", kbc.exists())
        val file = File(File(context.cacheDir, "updates").apply { mkdirs() }, "netra-charge-v0.1.0.apk")
        kbc.copyTo(file, overwrite = true)
        expectFail(file, "file changed") { Net.install(context, file) }
    }

    @Test fun installWithVerifiedRecordPassesToSystemGate() {
        DownloadCenter.register(context, chargeApp(), chargeRelease())
        val file = freshFixture()
        // Robolectric denies unknown-source installs by default; reaching that check proves revalidation passed.
        try { Net.install(context, file); fail("system gate expected") }
        catch (e: IllegalStateException) { assertEquals("Allow installs from Netra Ecosystem, then tap the button again.", e.message) }
        assertTrue(file.exists())
    }

    private fun putRawRecord(key: String, value: String) {
        context.getSharedPreferences("netra_eco_downloads", Context.MODE_PRIVATE).edit().putString(key, value).commit()
    }

    private fun rawRecord(appId: String = "netra-charge", pkg: String = "com.netracharge.app", tag: String = "v0.1.0") =
        listOf(pkg, "0.1.0", code.toString(), size.toString(), "Netra Charge", appId, sha, tag).joinToString("|")

    @Test fun mismatchedRecordPackageRefused() {
        // Record claims the Charge key but names a different package: refuse, no generic fallback.
        putRawRecord("netra-charge-v0.1.0.apk", rawRecord(pkg = "com.example.other"))
        val file = freshFixture()
        expectFail(file, "verified download") { Net.install(context, file) }
    }

    @Test fun mismatchedRecordAppIdRefused() {
        // Record names an app id outside the allowlist: treated as no direct record; the Charge
        // package in the archive then forces a refusal rather than a generic grant.
        putRawRecord("netra-charge-v0.1.0.apk", rawRecord(appId = "some-other-app"))
        val file = freshFixture()
        expectFail(file, "verified download") { Net.install(context, file) }
    }

    @Test fun recordKeyTagMismatchRefused() {
        // File key says v0.9.9 while the record inside says v0.1.0: not the canonical name for this record.
        putRawRecord("netra-charge-v0.9.9.apk", rawRecord())
        val file = freshFixture("netra-charge-v0.9.9.apk")
        expectFail(file, "verified download") { Net.install(context, file) }
    }

    @Test fun fileOutsideCanonicalDirRefused() {
        // Same record and same file name, but the file sits outside the updates cache dir.
        putRawRecord("netra-charge-v0.1.0.apk", rawRecord())
        val file = File(context.cacheDir, "netra-charge-v0.1.0.apk")
        File("build/apk-fixtures/charge.apk").copyTo(file, overwrite = true)
        expectFail(file, "verified download") { Net.install(context, file) }
    }

    @Test fun noRecordChargeKeyDifferentPackageRefused() {
        // Root review combo 1: no saved record at all, Charge cache key, different app's APK inside.
        val kbc = File("build/apk-fixtures/other-app.apk")
        assertTrue("CI must download public KBC fixture", kbc.exists())
        val file = File(File(context.cacheDir, "updates").apply { mkdirs() }, "netra-charge-v0.1.0.apk")
        kbc.copyTo(file, overwrite = true)
        expectFail(file, "verified download") { Net.install(context, file) }
    }

    @Test fun foreignRecordChargeKeyDifferentPackageRefused() {
        // Root review combo 2: record under the Charge key names a foreign app id; a different
        // app's APK sits at that key. Conflicting signals must refuse, never fall back.
        putRawRecord("netra-charge-v0.1.0.apk", rawRecord(appId = "some-other-app", pkg = "com.example.other"))
        val kbc = File("build/apk-fixtures/other-app.apk")
        assertTrue("CI must download public KBC fixture", kbc.exists())
        val file = File(File(context.cacheDir, "updates").apply { mkdirs() }, "netra-charge-v0.1.0.apk")
        kbc.copyTo(file, overwrite = true)
        expectFail(file, "verified download") { Net.install(context, file) }
    }

    @Test fun repoAppInstallPathUnchanged() {
        val apk = File("build/apk-fixtures/other-app.apk")
        assertTrue("CI must download public KBC fixture", apk.exists())
        // No saved record exists for this file either, but repo apps skip the direct revalidation entirely.
        try { Net.install(context, apk); fail("system gate expected") }
        catch (e: IllegalStateException) { assertEquals("Allow installs from Netra Ecosystem, then tap the button again.", e.message) }
    }
}
