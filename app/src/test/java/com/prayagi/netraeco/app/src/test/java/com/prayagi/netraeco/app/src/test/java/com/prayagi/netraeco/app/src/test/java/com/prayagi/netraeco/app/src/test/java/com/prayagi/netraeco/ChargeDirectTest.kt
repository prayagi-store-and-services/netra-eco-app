package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Repo-less catalog support: only the exact allowlisted app, only URLs on the Eco site. */
class ChargeDirectTest {

    private fun chargeEntry(download: String = "https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/app-release.apk",
                            latestJson: String = "https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/latest.json",
                            pkg: String = "com.netracharge.app", id: String = "netra-charge") =
        """{"id":"$id","package":"$pkg","name":"Netra Charge","type":"t","summary":"s","code":"charge","download":"$download","latestJson":"$latestJson"}"""

    private fun catalog(vararg entries: String) = """{"projects":[${entries.joinToString(",")}]}"""

    @Test fun allowlistedDirectEntryParses() {
        val apps = Net.parseCatalog(catalog(chargeEntry()))
        assertEquals(1, apps.size)
        val app = apps[0]
        assertEquals("", app.repo)
        assertEquals("com.netracharge.app", app.packageName)
        assertEquals("https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/app-release.apk", app.directApkUrl)
        assertEquals("https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/latest.json", app.latestJsonUrl)
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

    @Test fun unknownRepolessEntryDropped() {
        val stranger = chargeEntry(id = "other-app", pkg = "com.example.other")
        assertTrue(Net.parseCatalog(catalog(stranger)).isEmpty())
    }

    @Test fun wrongPackageDropped() {
        assertTrue(Net.parseCatalog(catalog(chargeEntry(pkg = "com.netracharge.evil"))).isEmpty())
    }

    @Test fun untrustedUrlsDropped() {
        assertTrue(Net.parseCatalog(catalog(chargeEntry(download = "https://evil.example.com/app-release.apk"))).isEmpty())
        assertTrue(Net.parseCatalog(catalog(chargeEntry(latestJson = "https://evil.example.com/latest.json"))).isEmpty())
        assertTrue(Net.parseCatalog(catalog(chargeEntry(download = "http://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/app-release.apk"))).isEmpty())
        assertTrue(Net.parseCatalog(catalog(chargeEntry(download = "https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/"))).isEmpty())
    }

    @Test fun directLatestUsesSiteApkUrl() {
        val json = """{"tag":"v0.1.0","versionName":"0.1.0","versionCode":1,"sha256":"${"1".repeat(64)}","size":656862,"notes":"n"}"""
        val release = Net.parseLatest(json, "", "https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/app-release.apk")!!
        assertEquals("https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/app-release.apk", release.apkUrl)
        assertEquals(1L, release.versionCode)
    }

    @Test fun repoLatestStillUsesGitHubUrl() {
        val json = """{"tag":"v1.2.3","versionName":"1.2.3","versionCode":5,"sha256":"${"1".repeat(64)}","size":1000}"""
        val release = Net.parseLatest(json, "prayagi-store-and-services/KBC")!!
        assertEquals("https://github.com/prayagi-store-and-services/KBC/releases/download/v1.2.3/app-release.apk", release.apkUrl)
    }

    @Test fun siteUrlTrust() {
        assertTrue(isTrustedSiteUrl("https://prayagi-store-and-services.github.io/netra-eco/downloads/netra-charge/latest.json"))
        assertFalse(isTrustedSiteUrl("https://prayagi-store-and-services.github.io.evil.com/netra-eco/x"))
        assertFalse(isTrustedSiteUrl("https://prayagi-store-and-services.github.io/other/x"))
        assertFalse(isTrustedSiteUrl(""))
    }
}
