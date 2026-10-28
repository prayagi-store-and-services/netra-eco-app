package com.prayagi.netraeco

import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdatesTest {
    private fun row(id: String, time: Long?) = AppRow(CatalogApp(id, id, "", "", "", "", "", ""), 1L to "1.0.0",
        LatestRelease("v1.0.1", "1.0.1", 2, "", "", "a".repeat(64), 1, time))
    @Test fun latestActualPublicationWins() {
        assertEquals(listOf("z", "a", "b"), AppSort.current(listOf(row("a", 10), row("b", null), row("z", 20))).map { it.app.id })
    }
    @Test fun missingDatesLastAndTiesDeterministic() {
        assertEquals(listOf("a", "b", "c"), AppSort.current(listOf(row("c", null), row("b", 10), row("a", 10))).map { it.app.id })
    }
    @Test fun parsePublicationTimeWithoutGuessing() {
        assertEquals(0L, releaseTimeMillis("1970-01-01T00:00:00Z"))
        assertNull(releaseTimeMillis("")); assertNull(releaseTimeMillis("tomorrow"))
    }
    @Test fun onlyObservedFutureToVerifiedCurrentTransitionsAlert() {
        assertEquals(setOf("future"), CatalogReleaseAlert.newlyReleased(setOf("future", "waiting"), setOf("old", "future")))
        assertTrue(CatalogReleaseAlert.newlyReleased(emptySet(), setOf("old")).isEmpty())
        assertTrue(CatalogReleaseAlert.newlyReleased(setOf("waiting"), emptySet()).isEmpty())
    }
}
