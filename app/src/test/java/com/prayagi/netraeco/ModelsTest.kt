package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test fun statusNotInstalled() = assertEquals(Status.NotInstalled, statusFor(null, 5))
    @Test fun statusUpdate() = assertEquals(Status.UpdateAvailable, statusFor(4, 5))
    @Test fun statusUpToDate() = assertEquals(Status.UpToDate, statusFor(5, 5))
    @Test fun statusInstalledNewer() = assertEquals(Status.InstalledNewer, statusFor(6, 5))
    @Test fun statusUnavailableWhenLatestUnknown() {
        assertEquals(Status.Unavailable, statusFor(null, null))
        assertEquals(Status.Unavailable, statusFor(3, null))
    }

    @Test fun tagAndShaValidation() {
        assertTrue(isValidTag("v1.0.5"))
        assertFalse(isValidTag("1.0.5"))
        assertFalse(isValidTag("v1.0"))
        assertTrue(isValidSha256("a".repeat(64)))
        assertFalse(isValidSha256("A".repeat(64)))
        assertFalse(isValidSha256("abc"))
    }

    @Test fun onlyOurOrganizationIsTrusted() {
        assertTrue(isTrustedRepo("prayagi-store-and-services/KBC"))
        assertTrue(isTrustedRepo("prayagi-store-and-services/-Battery-Sentinel-Pro-Netra"))
        assertFalse(isTrustedRepo("someone-else/KBC"))
        assertFalse(isTrustedRepo("prayagi-store-and-services/KBC/../x"))
        assertFalse(isTrustedRepo("prayagi-store-and-services-evil/KBC"))
    }

    @Test fun sizeFormatting() {
        assertEquals("Unavailable", formatSize(0))
        assertEquals("17.0 MB", formatSize(17L * 1024 * 1024))
    }
}
