package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Test

class UiHelpersTest {
    @Test fun genericBackupNoteIsHidden() {
        assertEquals("", usefulNotes("Backup update source for the in-app updater."))
        assertEquals("", usefulNotes("   "))
    }

    @Test fun realNotesAreKept() {
        assertEquals("Patch: fixed a crash", usefulNotes("  Patch: fixed a crash "))
    }

    @Test fun everyStatusHasALabel() {
        Status.values().forEach { assertEquals(false, statusLabel(it).isBlank()) }
        assertEquals("Update available", statusLabel(Status.UpdateAvailable))
    }

    @Test fun searchMatchesNameTypeAndSummaryWords() {
        assertEquals(true, matchesQuery("", "Hub"))
        assertEquals(true, matchesQuery("  ", "Hub"))
        assertEquals(true, matchesQuery("batt", "Battery Sentinel", "app", "Charging alerts"))
        assertEquals(true, matchesQuery("charging battery", "Battery Sentinel", "app", "Charging alerts"))
        assertEquals(false, matchesQuery("kundli", "Battery Sentinel", "app", "Charging alerts"))
    }
}
