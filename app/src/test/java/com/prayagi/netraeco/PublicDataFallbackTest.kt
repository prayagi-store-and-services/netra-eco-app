package com.prayagi.netraeco

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PublicDataFallbackTest {
    private val catalog = """{"projects":[],"upcoming":[{"id":"netra-neg","name":"Netra Neg"}]}"""
    private val roadmap = """{"apps":[],"items":[{"title":"Active work","status":"Active"}]}"""
    @Test fun pagesDnsFailureUsesSameOwnerRawJson() {
        val calls = mutableListOf<String>()
        val result = Net.fetchWithFallback(Net.CATALOG_URL) { calls += it; if (it == Net.CATALOG_FALLBACK) catalog else null }
        assertEquals(catalog, result)
        assertEquals(listOf(Net.CATALOG_URL, Net.CATALOG_FALLBACK), calls)
        assertEquals("Netra Neg", Upcoming.parse(result!!).single().name)
    }
    @Test fun invalidHtmlUsesFallbackAndRoadmapRemainsParseable() {
        val result = Net.fetchWithFallback(Roadmap.URL) { if (it == Roadmap.URL) "<html>error</html>" else roadmap }
        assertEquals("Active work", Roadmap.parse(result!!).single().title)
    }
    @Test fun validPrimaryNeverCallsFallback() {
        var calls = 0
        assertEquals(catalog, Net.fetchWithFallback(Net.CATALOG_URL) { calls++; catalog })
        assertEquals(1, calls)
    }
    @Test fun bothUnavailableReturnsNullAndNoFallbackForApkOrOtherUrl() {
        assertNull(Net.fetchWithFallback(Net.CATALOG_URL) { null })
        assertNull(Net.publicDataFallback("https://example.com/projects.json"))
        assertNull(Net.publicDataFallback("https://github.com/org/repo/releases/download/v1.0.0/app-release.apk"))
    }
}
