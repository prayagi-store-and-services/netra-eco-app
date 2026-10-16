package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** org.json only exists under Robolectric in unit tests, so JSON parsing is tested here, not in UpcomingTest. */
@RunWith(RobolectricTestRunner::class)
class UpcomingParseTest {

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
