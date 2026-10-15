package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Test

class NameGateTest {
    @Test fun nameWorksOfflineWithoutClockOrRemoteData() {
        assertEquals("Netra Ecosystem", NameGate.decide(null, null, null))
        assertEquals("Netra Ecosystem", NameGate.decide(0L, null, "Netra Eco"))
    }
    @Test fun storedOrRemoteNameCannotOverridePublicBrand() {
        assertEquals("Netra Ecosystem", NameGate.decide(Long.MAX_VALUE, "Other Name", "Old Name"))
    }
}
