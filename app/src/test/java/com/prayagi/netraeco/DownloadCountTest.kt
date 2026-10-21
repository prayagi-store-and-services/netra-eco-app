package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadCountTest {
    @Test fun mapsRepoToWebsiteCode() {
        assertEquals("bspn", DownloadCount.codeFor("prayagi-store-and-services/-Battery-Sentinel-Pro-Netra"))
        assertEquals("kbc", DownloadCount.codeFor("prayagi-store-and-services/KBC"))
        assertEquals("hub", DownloadCount.codeFor("prayagi-store-and-services/netra-hub"))
        assertEquals("privacy", DownloadCount.codeFor("prayagi-store-and-services/prayagi-Privacy-"))
        assertEquals("trikaal", DownloadCount.codeFor("prayagi-store-and-services/netra-trikaal"))
        assertEquals("player", DownloadCount.codeFor("prayagi-store-and-services/netra-player"))
        assertEquals("eco", DownloadCount.codeFor("prayagi-store-and-services/netra-eco-app"))
    }
    @Test fun unknownRepoIsNotCounted() {
        assertNull(DownloadCount.codeFor("someone/else"))
        assertNull(DownloadCount.urlFor("someone/else"))
    }
    @Test fun urlHasOnlyTheCode() {
        assertEquals("https://abacus.jasoncameron.dev/hit/netra-pages/dl-hub", DownloadCount.urlFor("x/netra-hub"))
    }
}
