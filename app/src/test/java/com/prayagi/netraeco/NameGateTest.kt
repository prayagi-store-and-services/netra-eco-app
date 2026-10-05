package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameGateTest {
    private val before = NameGate.CUTOFF_MS - 1000
    private val after = NameGate.CUTOFF_MS + 1000

    @Test fun beforeCutoffKeepsOldName() = assertEquals(NameGate.OLD_NAME, NameGate.decide(before, "Some Name", null))
    @Test fun noServerTimeKeepsOldName() {
        assertEquals(NameGate.OLD_NAME, NameGate.decide(null, "Some Name", null))
        assertEquals(NameGate.OLD_NAME, NameGate.decide(0L, "Some Name", null))
    }
    @Test fun afterCutoffWithoutNameFileKeepsOldName() = assertEquals(NameGate.OLD_NAME, NameGate.decide(after, null, null))
    @Test fun afterCutoffWithNameSwitches() = assertEquals("Some Name", NameGate.decide(after, " Some Name ", null))
    @Test fun savedNameIsKept() = assertEquals("Saved", NameGate.decide(null, null, "Saved"))
    @Test fun badNamesAreRejected() {
        assertNull(NameGate.validName("ab"))
        assertNull(NameGate.validName("<b>x</b>"))
        assertNull(NameGate.validName("x".repeat(41)))
        assertNull(NameGate.validName(null))
    }
    @Test fun parsesJson() {
        assertEquals("Some Name", NameGate.parseName("{\"name\":\"Some Name\"}"))
        assertNull(NameGate.parseName("not json"))
    }
    @Test fun cutoffIsOct11Midnight() = assertEquals(1791657000000L, NameGate.CUTOFF_MS)
}
