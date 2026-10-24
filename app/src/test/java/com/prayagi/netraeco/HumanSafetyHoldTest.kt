package com.prayagi.netraeco

import org.junit.Assert.*
import org.junit.Test

class HumanSafetyHoldTest {
    @Test fun canonicalRepoIsBlockedEvenWithoutCatalogPackage() { assertTrue(HumanSafetyHold.matches(repo = HumanSafetyHold.REPO)) }
    @Test fun canonicalPackageIsBlockedEvenWhenCatalogChangesRepo() { assertTrue(HumanSafetyHold.matches(packageName = HumanSafetyHold.PACKAGE)) }
    @Test fun otherAppsAreNotBlocked() {
        assertFalse(HumanSafetyHold.matches("prayagi-store-and-services/KBC", "com.example.kbc"))
        assertFalse(HumanSafetyHold.matches("prayagi-store-and-services/netra-eco-app", "com.prayagi.netraeco"))
        assertFalse(HumanSafetyHold.matches("prayagi-store-and-services/netra-hub-fork", HumanSafetyHold.PACKAGE + ".fork"))
    }
    @Test fun legacyCachedHubInstallerIsBlocked() { assertTrue(HumanSafetyHold.installerName("netra-hub-v1.5.0.apk")) }
    @Test fun otherCachedInstallerIsNotBlocked() { assertFalse(HumanSafetyHold.installerName("kbc-v1.1.37.apk")) }
    @Test fun blockedPrimitiveHasAdvisoryNotNetworkFailure() {
        try { HumanSafetyHold.requireAllowed(repo = HumanSafetyHold.REPO); fail("expected quarantine") }
        catch (e: IllegalStateException) { assertEquals(HumanSafetyHold.MESSAGE, e.message) }
    }
    @Test fun otherDownloadStillAllowed() { HumanSafetyHold.requireAllowed("prayagi-store-and-services/KBC", "com.example.kbc") }
}
