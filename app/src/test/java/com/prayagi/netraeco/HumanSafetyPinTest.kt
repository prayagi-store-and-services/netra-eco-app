package com.prayagi.netraeco

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HumanSafetyPinTest {
    private fun payload(): JSONObject = JSONObject().put("tag_name", HumanSafetyHold.TAG)
        .put("draft", false).put("prerelease", false).put("published_at", "2026-10-26T16:04:51Z")
        .put("assets", JSONArray().put(JSONObject().put("name", "app-release.apk").put("state", "uploaded")
            .put("size", HumanSafetyHold.SIZE).put("digest", "sha256:" + HumanSafetyHold.SHA)
            .put("browser_download_url", HumanSafetyHold.URL)))
    @Test fun exactAuditedAssetAllowed() {
        val release = HumanSafetyHold.parseRelease(payload().toString())!!
        assertTrue(HumanSafetyHold.allowed(release))
        HumanSafetyHold.requireAllowed(HumanSafetyHold.REPO, HumanSafetyHold.PACKAGE, release)
    }
    @Test fun wrongReleaseStateOrPublicationFailsClosed() {
        for ((k, v) in listOf("tag_name" to "v1.5.1", "draft" to true, "prerelease" to true,
                "published_at" to "invalid")) {
            assertNull(k, HumanSafetyHold.parseRelease(payload().put(k, v).toString()))
        }
        assertNull(HumanSafetyHold.parseRelease(payload().put("published_at", JSONObject.NULL).toString()))
        assertNull(HumanSafetyHold.parseRelease(payload().put("assets", JSONArray()).toString()))
    }
    @Test fun changedAssetCannotPass() {
        for ((k, v) in listOf("name" to "other.apk", "state" to "starter", "size" to 1,
                "digest" to "sha256:bad", "browser_download_url" to "https://github.com/x/latest.apk")) {
            val p = payload(); p.getJSONArray("assets").getJSONObject(0).put(k, v)
            assertNull(k, HumanSafetyHold.parseRelease(p.toString()))
        }
    }
    @Test fun directMetadataCannotChangeAnyPin() {
        val r = HumanSafetyHold.parseRelease(payload().toString())!!
        listOf(r.copy(tag="v1.5.1"),r.copy(versionName="1.5.1"),r.copy(versionCode=77),
            r.copy(size=1),r.copy(sha256="a".repeat(64)),r.copy(apkUrl="https://x.invalid/a.apk")).forEach {
            assertFalse(HumanSafetyHold.allowed(it))
        }
    }
    @Test fun checkedActualApkPassesIdentityAndByteGateButTamperFails() {
        val file = File("build/apk-fixtures/checked-hub.apk")
        assertTrue("positive fixture required", file.exists())
        assertTrue(HumanSafetyHold.fileAllowed(file))
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val apk = context.packageManager.getPackageArchiveInfo(file.absolutePath,0)!!
        assertEquals(HumanSafetyHold.PACKAGE, apk.packageName)
        HumanSafetyHold.requireInstaller(file,apk.packageName,apk.versionName,androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(apk))
        val altered = File(context.cacheDir,"altered-hub.apk")
        file.copyTo(altered,overwrite=true)
        java.io.RandomAccessFile(altered,"rw").use { it.seek(1); val original = it.read(); it.seek(1); it.write(original xor 1) }
        assertFalse(HumanSafetyHold.fileAllowed(altered))
        try { HumanSafetyHold.requireInstaller(altered,apk.packageName,apk.versionName,HumanSafetyHold.CODE); fail("tamper") }
        catch (e: IllegalStateException) { assertEquals(HumanSafetyHold.MESSAGE,e.message) }
    }
    @Test fun cacheRefreshCannotAuthorizeSameSizeTamperedHubOrOldVersion() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val a = CatalogApp("netra-hub","Human Safety","","",HumanSafetyHold.REPO,HumanSafetyHold.PACKAGE,"","")
        val release = HumanSafetyHold.parseRelease(payload().toString())!!
        val key = DownloadCenter.keyFor(a,release)
        val cached = DownloadCenter.file(context,key)
        File("build/apk-fixtures/checked-hub.apk").copyTo(cached,overwrite=true)
        DownloadCenter.register(context,a,release)
        DownloadCenter.items.value = emptyMap()
        DownloadCenter.refresh(context)
        assertEquals(DownloadCenter.DONE,DownloadCenter.items.value[key]?.state)
        assertTrue(DownloadCenter.hasFile(context,a,release))
        java.io.RandomAccessFile(cached,"rw").use { it.seek(1); val b=it.read(); it.seek(1); it.write(b xor 1) }
        DownloadCenter.refresh(context)
        assertFalse(DownloadCenter.hasFile(context,a,release))
        assertFalse(DownloadCenter.hasFile(context,a,release.copy(tag="v1.5.1")))
        DownloadCenter.delete(context,key)
        Unit
    }
    @Test fun bulkEligibilitySharesTheDirectGate() {
        val a = CatalogApp("netra-hub","Human Safety","","",HumanSafetyHold.REPO,HumanSafetyHold.PACKAGE,"","")
        val r = HumanSafetyHold.parseRelease(payload().toString())!!
        assertTrue(DownloadCenter.allowedForDownload(a,r))
        assertFalse(DownloadCenter.allowedForDownload(a,r.copy(tag="v1.5.1")))
        assertFalse(DownloadCenter.allowedForDownload(a,null))
        assertFalse(DownloadCenter.allowedForDownload(a.copy(packageName="wrong"),r))
        assertTrue(DownloadCenter.allowedForDownload(a.copy(repo="prayagi-store-and-services/KBC",packageName="other"),r))
    }
    @Test fun oldRenamedAndWrongBytesStayBlocked() {
        val old = File("build/apk-fixtures/renamed-hub.apk")
        assertTrue(old.exists())
        assertFalse(HumanSafetyHold.fileAllowed(old))
        try { HumanSafetyHold.requireInstaller(old, HumanSafetyHold.PACKAGE, "1.5.0", 76); fail("old") }
        catch (e: IllegalStateException) { assertEquals(HumanSafetyHold.MESSAGE,e.message) }
    }
}
