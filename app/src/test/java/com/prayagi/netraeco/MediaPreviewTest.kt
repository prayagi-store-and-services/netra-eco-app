package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaPreviewTest {
    private val trustedVideo = "https://prayagi-store-and-services.github.io/netra-eco/videos/a.mp4"
    private val trustedShot = "https://prayagi-store-and-services.github.io/battery-sentinel/shots/1.png"
    private val evil = "https://evil.example/x.png"

    private fun json(video: String? = null, shots: List<Pair<String, String>> = emptyList()): String {
        val v = if (video == null) "" else "\"video\":{\"url\":\"$video\",\"label\":\"Promo\"},"
        val s = shots.joinToString(",") { "{\"url\":\"${it.first}\",\"label\":\"${it.second}\"}" }
        return "{\"apps\":{\"netra-eco\":{$v\"screenshots\":[$s]}}}"
    }

    @Test fun badJsonGivesEmptyMap() = assertTrue(MediaPreviewStore.parse("not json").isEmpty())

    @Test fun videoAndShotsParse() {
        val m = MediaPreviewStore.parse(json(trustedVideo, listOf(trustedShot to "Home")))
        val p = m["netra-eco"]!!
        assertEquals(trustedVideo, p.videoUrl)
        assertEquals(listOf(trustedShot to "Home"), p.shots)
    }

    @Test fun untrustedUrlsAreDropped() {
        assertFalse(MediaPreviewStore.isTrustedMediaUrl(evil))
        assertTrue(MediaPreviewStore.isTrustedMediaUrl(trustedVideo))
        val m = MediaPreviewStore.parse(json(evil, listOf(evil to "Evil", trustedShot to "Home")))
        val p = m["netra-eco"]!!
        assertNull(p.videoUrl)
        assertEquals(listOf(trustedShot to "Home"), p.shots)
    }

    @Test fun entryWithNoUsableMediaIsHidden() {
        assertTrue(MediaPreviewStore.parse(json()).isEmpty())
        assertTrue(MediaPreviewStore.parse(json(evil, listOf(evil to "Evil"))).isEmpty())
    }

    @Test fun missingAppsObjectGivesEmptyMap() =
        assertTrue(MediaPreviewStore.parse("{}").isEmpty())
}
