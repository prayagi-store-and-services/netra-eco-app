package com.prayagi.netraeco

import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedesignGateTest {
    @Test fun offJustBeforeSwitch() = assertFalse(RedesignGate.isOn(LocalDateTime.of(2026, 10, 10, 23, 59, 59)))
    @Test fun onAtSwitch() = assertTrue(RedesignGate.isOn(LocalDateTime.of(2026, 10, 11, 0, 0)))
    @Test fun onAfter() = assertTrue(RedesignGate.isOn(LocalDateTime.of(2027, 1, 1, 12, 0)))
    @Test fun offToday() = assertFalse(RedesignGate.isOn(LocalDateTime.of(2026, 10, 9, 12, 0)))
}
