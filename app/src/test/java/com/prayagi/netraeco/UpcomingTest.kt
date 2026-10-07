package com.prayagi.netraeco

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class UpcomingTest {
    @Test fun twoTargetsStaySeparateWithoutLegacyEta() {
        val targets = listOf(UpcomingTarget("Public", null, "", LocalDate.parse("2027-04-01")), UpcomingTarget("Beta", null, "Testing required", LocalDate.parse("2026-10-15")))
        val card = UpcomingApp("a", "Assistant", "", "", null, targets)
        assertEquals(listOf("Public", "Beta"), card.targets.map { it.label })
        assertNull(card.etaMillis)
    }

    @Test fun legacySingleTimerModelStillWorks() {
        val card = UpcomingApp("astro", "Trikaal", "", "", 72 * 3600_000L)
        assertTrue(card.targets.isEmpty())
        assertEquals("72h 00m 00s", Upcoming.targetText(UpcomingTarget("Coming soon", card.etaMillis, ""), 0))
    }

    @Test fun dateOnlyTargetsDoNotInventLaunchHour() {
        val target = UpcomingTarget("Beta", null, "", LocalDate.parse("2026-10-15"))
        fun time(value: String) = OffsetDateTime.parse(value).toInstant().toEpochMilli()
        assertEquals("8 days to target", Upcoming.targetText(target, time("2026-10-07T23:59:59+05:30")))
        assertEquals("Approx. 24-48 hours to target day", Upcoming.targetText(target, time("2026-10-14T00:00:00+05:30")))
        assertEquals("Target day, release not confirmed", Upcoming.targetText(target, time("2026-10-15T18:00:00+05:30")))
        assertEquals("Target passed, check release status", Upcoming.targetText(target, time("2026-10-16T00:00:00+05:30")))
        assertEquals("Unavailable", Upcoming.targetText(UpcomingTarget("Bad", null, ""), 0))
    }
}
