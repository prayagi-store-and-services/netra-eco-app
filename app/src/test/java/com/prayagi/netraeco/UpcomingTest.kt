package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpcomingTest {
    private fun app(id: String, eta: Long? = null) = UpcomingApp(id, "Name $id", "Type", "Summary", eta)

    private fun plan(vararg ids: String, eta: Long) =
        RoadmapItem("Plan", "Planned", eta, "Names", false, emptyList(), ids.toList())

    @Test fun ownEtaWins() {
        assertEquals(100L, Upcoming.etaFor(app("netra-email", 100L), null))
        assertEquals(100L, Upcoming.etaFor(app("netra-email", 100L), listOf(plan("netra-email", eta = 200L))))
    }

    @Test fun roadmapEtaIsUsedWhenTheCardHasNone() {
        val pending = listOf(plan("netra-intruder", eta = 300L))
        assertEquals(300L, Upcoming.etaFor(app("netra-intruder"), pending))
    }

    @Test fun earliestRoadmapEtaWins() {
        val pending = listOf(plan("netra-neg", eta = 900L), plan("netra-neg", eta = 400L))
        assertEquals(400L, Upcoming.etaFor(app("netra-neg"), pending))
    }

    @Test fun ideaStageWithoutRoadmapEntryStaysUndated() {
        assertNull(Upcoming.etaFor(app("netra-open-store"), listOf(plan("netra-email", eta = 100L))))
        assertNull(Upcoming.etaFor(app("netra-open-store"), emptyList()))
    }

    @Test fun unreadableRoadmapLeavesEtaUnknown() {
        assertNull(Upcoming.etaFor(app("netra-email"), null))
    }

    @Test fun parseReadsPlannedFeatures() {
        val json = """{"upcoming":[{"id":"netra-travel-rider","name":"Netra Travel Rider","type":"Intercom","summary":"Rider intercom","eta":"2026-11-22T20:00:00+05:30","features":["Push-to-talk intercom","SOS alert"]}]}"""
        val apps = Upcoming.parse(json)
        assertEquals(1, apps.size)
        assertEquals(listOf("Push-to-talk intercom", "SOS alert"), apps[0].features)
    }

    @Test fun missingFeaturesParsesAsEmpty() {
        val json = """{"upcoming":[{"id":"netra-email","name":"Netra Email","type":"Email","summary":"Private email"}]}"""
        assertEquals(emptyList<String>(), Upcoming.parse(json)[0].features)
    }

    @Test fun blankFeatureEntriesAreDropped() {
        val json = """{"upcoming":[{"id":"netra-hub","name":"Netra Hub","type":"Safety","summary":"Safety","features":["Real feature","","   "]}]}"""
        assertEquals(listOf("Real feature"), Upcoming.parse(json)[0].features)
    }
}
