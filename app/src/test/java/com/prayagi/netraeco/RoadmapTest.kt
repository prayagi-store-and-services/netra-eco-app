package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadmapTest {
    private fun item(title: String, eta: Long?, released: Boolean = false) =
        RoadmapItem(title, if (released) "Released" else "Planned", eta, "App A", released)

    private val items = listOf(item("Later", 2000L), item("Soon", 1000L), item("Done", 500L, released = true), item("NoDate", null))

    @Test fun nextIsEarliestFutureUnreleased() {
        assertEquals("Soon", Roadmap.next(items, 100L)?.title)
        assertEquals("Later", Roadmap.next(items, 1500L)?.title)
    }

    @Test fun releasedAndPastItemsAreNotNext() {
        assertNull(Roadmap.next(items, 2500L))
        assertNull(Roadmap.next(emptyList(), 0L))
    }

    @Test fun badJsonGivesEmptyList() = assertTrue(Roadmap.parse("not json").isEmpty())

    @Test fun countdownText() {
        assertEquals("1d 02h 03m 04s", Roadmap.countdown(((26 * 3600 + 3 * 60 + 4) * 1000L)))
        assertEquals("Estimate passed, still being finished", Roadmap.countdown(0))
    }
}
