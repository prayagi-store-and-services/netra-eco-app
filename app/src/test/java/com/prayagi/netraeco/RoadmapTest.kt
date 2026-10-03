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

    private fun it2(status: String, eta: Long?, apps: String = "App A") = RoadmapItem("T", status, eta, apps, false)
    private val min = 60_000L

    @Test fun tickerColours() {
        assertEquals(Tone.RED, Ticker.tone(it2("Planned", 3 * 3600 * 1000L + 1), 0L))
        assertEquals(Tone.BLUE, Ticker.tone(it2("Planned", 2 * 3600 * 1000L), 0L))
        assertEquals(Tone.GREEN, Ticker.tone(it2("Planned", 30 * min), 0L))
        assertEquals(Tone.GREEN, Ticker.tone(it2("Planned", 10L), 100L))
        assertEquals(Tone.GREY, Ticker.tone(it2("Planned", null), 0L))
    }

    @Test fun inProgressIsOrangeEvenWhenClose() {
        assertEquals(Tone.ORANGE, Ticker.tone(it2("In progress", 5 * min), 0L))
        assertEquals(Tone.ORANGE, Ticker.tone(it2("In progress", null), 0L))
    }

    @Test fun tickerTimeText() {
        assertEquals("22 m", Ticker.left(22 * min))
        assertEquals("1 h 45 m", Ticker.left(105 * min))
        assertEquals("5 d 3 h", Ticker.left((5 * 24 * 60 + 3 * 60) * min))
    }

    @Test fun tickerLabels() {
        assertEquals("App A: T - in 22 m", Ticker.label(it2("Planned", 22 * min), 0L))
        assertEquals("T - Unavailable", Ticker.label(it2("Planned", null, "A, B"), 0L))
        assertEquals("App A: T - IN PROGRESS", Ticker.label(it2("In progress", null), 0L))
        assertEquals("App A: T - estimate passed, check for update", Ticker.label(it2("Planned", 5L), 10L))
    }

    @Test fun upcomingSkipsReleasedAndSortsByDate() {
        val l = listOf(item("B", 2000L), item("Done", 1L, released = true), item("A", 1000L), item("None", null))
        assertEquals(listOf("A", "B", "None"), Ticker.upcoming(l).map { it.title })
    }
}
