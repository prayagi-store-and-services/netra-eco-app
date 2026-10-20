package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewFocusTest {

    @Test fun mostVisibleLiveCardWins() {
        val active = PreviewFocus.pickActive(
            listOf(
                VisibleEntry("l-alpha", offset = 0, size = 100),      // fully visible
                VisibleEntry("l-beta", offset = 100, size = 400)      // 300 of 400 visible
            ),
            viewportStart = 0, viewportEnd = 400
        )
        assertEquals("l-alpha", active) // 100% beats 75%
    }

    @Test fun partiallyLargerCardCanWin() {
        val active = PreviewFocus.pickActive(
            listOf(
                VisibleEntry("l-alpha", offset = -80, size = 100),    // 20% visible
                VisibleEntry("l-beta", offset = 20, size = 400)       // 95% visible
            ),
            viewportStart = 0, viewportEnd = 400
        )
        assertEquals("l-beta", active)
    }

    @Test fun belowHalfVisibleMeansNoAutoplay() {
        val active = PreviewFocus.pickActive(
            listOf(VisibleEntry("l-alpha", offset = 0, size = 400)),
            viewportStart = 0, viewportEnd = 150 // only 37.5% visible
        )
        assertNull(active)
    }

    @Test fun nonLiveCardsNeverAutoplay() {
        val active = PreviewFocus.pickActive(
            listOf(
                VisibleEntry("up-coming", offset = 0, size = 100),
                VisibleEntry("timespeak", offset = 100, size = 100),
                VisibleEntry("cat-head", offset = 200, size = 100)
            ),
            viewportStart = 0, viewportEnd = 400
        )
        assertNull(active)
    }

    @Test fun emptyAndDegenerateInputIsSafe() {
        assertNull(PreviewFocus.pickActive(emptyList(), 0, 400))
        assertNull(PreviewFocus.pickActive(listOf(VisibleEntry("l-x", 0, 0)), 0, 400))
        assertNull(PreviewFocus.pickActive(listOf(VisibleEntry("l-x", 500, 100)), 0, 400))
    }

    @Test fun screenshotDecodeIsSampledToDisplaySize() {
        assertEquals(1, PreviewFocus.sampleSize(400, 600, 660))   // already small enough
        assertEquals(2, PreviewFocus.sampleSize(1080, 2400, 660)) // 2400 -> 1200
        assertEquals(4, PreviewFocus.sampleSize(2000, 4000, 660)) // 4000 -> 1000
        assertEquals(1, PreviewFocus.sampleSize(0, 4000, 660))    // corrupt bounds stay safe
        assertEquals(1, PreviewFocus.sampleSize(1080, 2400, 0))
    }
}
