package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Repo-less catalog support: only the exact allowlisted id, package and URLs.
 * Runs under Robolectric because Net.parseCatalog/parseLatest use org.json, which plain JVM unit tests stub out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ChargeDirectTest {

    private val apk = "https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/app-release.apk"
    private val meta = "https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/latest.json"

    private fun chargeEntry(download: String = apk, latestJson: String = meta,
                            pkg: String = "com.netracharge.app", id: String = "netra-charge") =
        """{"id":"$id","package":"$pkg","name":"Netra Charge","type":"t","summary":"s","code":"charge","download":"$download","latestJson":"$latestJson"}"""

    private fun catalog(vararg entries: String) = """{"projects":[${entries.joinToString(",")}]}"""

    private fun latestJson(tag: String = "v0.1.1", code: Long = 2, sha: String = "1".repeat(64), size: Long = 657000) =
        """{"tag":"$tag","versionName":"${tag.removePrefix("v")}","versionCode":$code,"sha256":"$sha","size":$size,"notes":"n"}"""

    @Test fun allowlistedDirectEntryParses() {
        val apps = Net.parseCatalog(catalog(chargeEntry()))
        assertEquals(1, apps.size)
        val app = apps[0]
        assertEquals("", app.repo)
        assertEquals("com.netracharge.app", app.packageName)
        assertEquals(apk, app.directApkUrl)
        assertEquals(meta, app.latestJsonUrl)
        assertEquals("charge", app.counterCode)
    }

    @Test fun repoPathUnchanged() {
        val repoEntry = """{"id":"kbc","package":"com.aistudio.reasoningquiz.tark","name":"KBC","repo":"prayagi-store-and-services/KBC"}"""
        val apps = Net.parseCatalog(catalog(repoEntry, chargeEntry()))
        assertEquals(2, apps.size)
        assertEquals("prayagi-store-and-services/KBC", apps[0].repo)
        assertEquals("", apps[0].directApkUrl)
        assertEquals("", apps[0].counterCode)
        assertEquals("https://github.com/prayagi-store-and-services/KBC/releases/latest/download/latest.json", apps[0].latestJsonUrl)
    }

    @Test fun unknownRepolessIdDropped() {
        assertTrue(Net.parseCatalog(catalog(chargeEntry(id = "other-app", pkg = "com.example.other"))).isEmpty())
        // Even the real package under a different id is not enough.
        assertTrue(Net.parseCatalog(catalog(chargeEntry(id = "netra-charge-2"))).isEmpty())
    }

    @Test fun wrongPackageDropped() {
        assertTrue(Net.parseCatalog(catalog(chargeEntry(pkg = "com.netracharge.evil"))).isEmpty())
        assertTrue(Net.parseCatalog(catalog(chargeEntry(pkg = ""))).isEmpty())
    }

    @Test fun nearMissUrlsDropped() {
        // A different path on the same trusted site is not the allowlisted URL.
        assertTrue(Net.parseCatalog(catalog(chargeEntry(download = "https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/app-release-2.apk"))).isEmpty())
        assertTrue(Net.parseCatalog(catalog(chargeEntry(download = "https://prayagi-store-and-services.github.io/netra-eco/downloads/other/app-release.apk"))).isEmpty())
        assertTrue(Net.parseCatalog(catalog(chargeEntry(latestJson = "https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/latest-2.json"))).isEmpty())
        assertTrue(Net.parseCatalog(catalog(chargeEntry(latestJson = "https://prayagi-store-and-services.github.io/netra-eco/app/charge/latest.json"))).isEmpty())
        // Same words, wrong host or scheme.
        assertTrue(Net.parseCatalog(catalog(chargeEntry(download = "https://prayagi-store-and-services.github.io.evil.com/netra-eco/downloads/netra-charge/app-release.apk"))).isEmpty())
        assertTrue(Net.parseCatalog(catalog(chargeEntry(download = "http://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/app-release.apk"))).isEmpty())
        // Trailing junk or missing file is not the exact URL.
        assertTrue(Net.parseCatalog(catalog(chargeEntry(download = "$apk?x=1"))).isEmpty())
        assertTrue(Net.parseCatalog(catalog(chargeEntry(download = ""))).isEmpty())
    }

    @Test fun directLatestUsesSiteApkUrl() {
        val release = Net.parseLatest(latestJson(), "", apk)!!
        assertEquals(apk, release.apkUrl)
        assertEquals(2L, release.versionCode)
        assertEquals("0.1.1", release.versionName)
    }

    @Test fun corruptLatestRejected() {
        assertNull(Net.parseLatest(latestJson(sha = "not-a-sha"), "", apk))
        assertNull(Net.parseLatest(latestJson(sha = "1".repeat(63)), "", apk))
        assertNull(Net.parseLatest(latestJson(size = 0), "", apk))
        assertNull(Net.parseLatest(latestJson(size = -5), "", apk))
        assertNull(Net.parseLatest(latestJson(code = 0), "", apk))
        assertNull(Net.parseLatest(latestJson(tag = "0.1.1"), "", apk))
        assertNull(Net.parseLatest("""{"tag":"v0.1.1"}""", "", apk))
        assertNull(Net.parseLatest("not json at all", "", apk))
    }

    @Test fun repoLatestStillUsesGitHubUrl() {
        val release = Net.parseLatest(latestJson(tag = "v1.2.3", code = 5), "prayagi-store-and-services/KBC")!!
        assertEquals("https://github.com/prayagi-store-and-services/KBC/releases/download/v1.2.3/app-release.apk", release.apkUrl)
    }

    @Test fun archiveVersionComparedToInstalled() {
        val latest = Net.parseLatest(latestJson(tag = "v0.1.1", code = 2), "", apk)!!
        assertEquals(Status.UpdateAvailable, statusForRelease(1L, "0.1.0", latest))
        assertEquals(Status.UpToDate, statusForRelease(2L, "0.1.1", latest))
        assertEquals(Status.InstalledNewer, statusForRelease(3L, "0.1.2", latest))
        assertEquals(Status.NotInstalled, statusForRelease(null, null, latest))
        assertEquals(Status.Unavailable, statusForRelease(1L, "0.1.0", null))
    }

    @Test fun directEntryNeedsFreshMetadataUrl() {
        // fetchLatest must refuse a direct app whose metadata URL drifted from the allowlist.
        val drifted = Net.parseCatalog(catalog(chargeEntry()))[0].copy(latestJsonUrl = "https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/other.json")
        assertNull(Net.fetchLatest(drifted))
    }
}
