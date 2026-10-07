package com.prayagi.netraeco

import org.junit.Assert.*
import org.junit.Test

class UpcomingTest {
    @Test fun legacySingleTimerStillParses() {
        val card = Upcoming.parse("""{"upcoming":[{"id":"astro","name":"Trikaal","eta":"2026-10-09T07:15:00+05:30"}]}""").single()
        assertNotNull(card.etaMillis)
        assertTrue(card.targets.isEmpty())
    }

    @Test fun twoTargetsStaySeparateAndInvalidTimeIsUnavailable() {
        val card = Upcoming.parse("""{"upcoming":[{"id":"assistant","name":"NETRA x TRIKAL","targets":[{"label":"Public release target","eta":"2027-04-01T00:00:00+05:30"},{"label":"First beta target","eta":"bad","note":"Subject to testing"}]}]}""").single()
        assertEquals(2, card.targets.size)
        assertNotNull(card.targets[0].etaMillis)
        assertNull(card.targets[1].etaMillis)
        assertEquals("Subject to testing", card.targets[1].note)
    }

    @Test fun dateOnlyTargetsDoNotInventLaunchHour() {
        val card = Upcoming.parse("""{"upcoming":[{"id":"a","name":"A","targets":[{"label":"Beta","targetDate":"2026-10-15"}]}]}""").single()
        val target = card.targets.single()
        val before = java.time.OffsetDateTime.parse("2026-10-07T23:59:59+05:30").toInstant().toEpochMilli()
        assertEquals("8 days to target", Upcoming.targetText(target, before))
        val onDay = java.time.OffsetDateTime.parse("2026-10-15T18:00:00+05:30").toInstant().toEpochMilli()
        assertEquals("Target day, release not confirmed", Upcoming.targetText(target, onDay))
        assertEquals("Unavailable", Upcoming.targetText(UpcomingTarget("Bad", null, ""), before))
    }

    @Test fun malformedEntriesAreSkippedAndTargetCountIsBounded() {
        val card = Upcoming.parse("""{"upcoming":[{"id":"a","name":"A","targets":[null,{}, {"label":"Beta"},{"label":"Public"},{"label":"Extra"}]}]}""").single()
        assertEquals(listOf("Beta", "Public"), card.targets.map { it.label })
        assertTrue(card.targets.all { it.etaMillis == null })
    }
}
