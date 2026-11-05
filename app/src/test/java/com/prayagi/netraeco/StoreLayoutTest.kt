package com.prayagi.netraeco

import org.junit.Assert.*
import org.junit.Test

class StoreLayoutTest {
    private val app = CatalogApp("kbc", "Netra KBC", "Game", "Quiz", "prayagi-store-and-services/KBC", "com.aistudio.reasoningquiz.tark", "", "")
    private val latest = LatestRelease("v1.1.43", "1.1.43", 43, "notes", "https://github.com/prayagi-store-and-services/KBC/releases/download/v1.1.43/app-release.apk", "a".repeat(64), 100)
    @Test fun exactDeviceActionMatrix() {
        assertEquals("Install", storeActionLabel(AppRow(app,null,latest)))
        assertEquals("Update", storeActionLabel(AppRow(app,42L to "1.1.42",latest)))
        assertEquals("Open", storeActionLabel(AppRow(app,43L to "1.1.43",latest)))
        assertEquals("Open", storeActionLabel(AppRow(app,44L to "1.1.44",latest)))
    }
    @Test fun unknownDeviceNeverBecomesInstall() {
        val row = AppRow(app,null,latest,false)
        assertEquals(Status.Unavailable,storeStatus(row))
        assertEquals("Details",storeActionLabel(row))
        assertTrue(storeUpdateRows(listOf(row)).isEmpty())
    }
    @Test fun updateCountExcludesNotInstalledCurrentAndUnknownRelease() {
        assertEquals(1,storeUpdateRows(listOf(AppRow(app,42L to "1.1.42",latest),AppRow(app,null,latest),AppRow(app,43L to "1.1.43",latest),AppRow(app,42L to "1.1.42",null))).size)
    }
    @Test fun chargeCatalogEntryHasExactKnownRepoAndPackage() {
        val parsed=Net.parseCatalog("""{"projects":[{"id":"netra-charge","name":"Netra Charge","package":"com.netracharge.app"}]}""")
        assertEquals("prayagi-store-and-services/netra-charge",parsed.single().repo)
        assertTrue(InstalledApps.knownPackages.contains(parsed.single().packageName))
    }
    @Test fun noBroadOrUntrustedPolicyLink() {
        assertEquals("",catalogPrivacyUrl("https://evil.invalid/policy"))
        assertEquals("",catalogPrivacyUrl("javascript:alert(1)"))
        assertEquals("https://prayagi-store-and-services.github.io/netra-eco/legal/privacy-kbc.html",catalogPrivacyUrl("../../legal/privacy-kbc.html"))
    }
    @Test fun everyKnownOtherPackageHasManifestVisibility() {
        val text=java.io.File("src/main/AndroidManifest.xml").readText()
        InstalledApps.knownPackages.filter { it != "com.prayagi.netraeco" }.forEach { assertTrue(it,text.contains("android:name=\""+it+"\"")) }
        assertFalse(text.contains("QUERY_ALL_PACKAGES"))
    }
    @Test fun chargeSignerIsNarrowlyPinned() {
        assertEquals("814d2dd07f8ba510643795215f61083d1ebc905a5e9b3fba570faa5862381ab9",InstallerGate.expectedSigner("com.netracharge.app"))
        assertNull(InstallerGate.expectedSigner("com.netracharge.other"))
    }
    @Test fun unknownReleaseDateIsNotInvented() { assertEquals("Unavailable",storeReleaseDate(null)) }
}
