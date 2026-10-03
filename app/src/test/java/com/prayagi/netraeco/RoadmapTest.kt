package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadmapTest {
    private val json = """{"apps":[{"id":"a","name":"App A"}],"items":[
      {"apps":["a"],"title":"Later","status":"Planned","eta":"2026-10-10T20:00:00+05:30"},
      {"apps":["a"],"title":"Soon","status":"Planned","eta":"2026-10-05T20:00:00+05:30"},
      {"apps":["a"],"title":"Done","status":"Released","eta":"2026-10-01T20:00:00+05:30"},
      {"apps":["a"],"title":"","status":"Planned","eta":"x"}]}"""

    @Test fun parsesItemsAndSkipsBlankTitles() {
        val items = Roadmap.parse(json)
        assertEquals(3, items.size)
        assertEquals("App A", items[0].appNames)
        assertTrue(items[2].released)
    }

    @Test fun nextIsEarliestFutureUnreleased() {
        val items = Roadmap.parse(json)
        val now = java.time.OffsetDateTime.parse("2026-10-03T12:00:00+05:30").toInstant().toEpochMilli()
        assertEquals("Soon", Roadmap.next(items, now)?.title)
        val after = java.time.OffsetDateTime.parse("2026-10-11T00:00:00+05:30").toInstant().toEpochMilli()
        assertNull(Roadmap.next(items, after))
    }

    @Test fun badJsonGivesEmptyList() = assertTrue(Roadmap.parse("not json").isEmpty())

    @Test fun countdownText() {
        assertEquals("1d 02h 03m 04s", Roadmap.countdown(((26 * 3600 + 3 * 60 + 4) * 1000L)))
        assertEquals("Estimate passed, still being finished", Roadmap.countdown(0))
    }
}
