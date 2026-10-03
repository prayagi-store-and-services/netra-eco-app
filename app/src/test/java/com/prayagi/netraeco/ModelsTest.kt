package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

class FreshUpdateTest {
    private fun rel(code: Long, name: String) = LatestRelease("v$name", name, code, "", "u", "a".repeat(64), 1L)

    @Test fun versionNamesCompareNumerically() {
        assertTrue(compareVersions("1.1.15", "1.1.16") < 0)
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertEquals(0, compareVersions("v1.0", "1.0.0"))
    }

    @Test fun releaseWithoutCodeUsesNames() {
        assertEquals(Status.UpdateAvailable, statusForRelease(17, "1.1.15", rel(0, "1.1.16")))
        assertEquals(Status.UpToDate, statusForRelease(18, "1.1.16", rel(0, "1.1.16")))
        assertEquals(Status.InstalledNewer, statusForRelease(19, "1.1.17", rel(0, "1.1.16")))
    }

    @Test fun releaseWithCodeUsesCode() {
        assertEquals(Status.UpdateAvailable, statusForRelease(4, "1.0.3", rel(5, "1.0.4")))
        assertEquals(Status.Unavailable, statusForRelease(4, "1.0.3", null))
        assertEquals(Status.NotInstalled, statusForRelease(null, null, rel(5, "1.0.4")))
    }

    @Test fun digestParsing() {
        assertEquals("a".repeat(64), Net.shaFromDigest("sha256:" + "A".repeat(64)))
        assertNull(Net.shaFromDigest("md5:abc"))
        assertNull(Net.shaFromDigest(""))
    }
}
