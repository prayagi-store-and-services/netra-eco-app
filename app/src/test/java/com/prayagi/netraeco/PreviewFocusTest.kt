package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewFocusTest {
    @Test fun screenshotDecodeIsSampledToDisplaySize() {
        assertEquals(1, PreviewFocus.sampleSize(400, 600, 660))   // already small enough
        assertEquals(2, PreviewFocus.sampleSize(1080, 2400, 660)) // 2400 -> 1200
        assertEquals(4, PreviewFocus.sampleSize(2000, 4000, 660)) // 4000 -> 1000
        assertEquals(1, PreviewFocus.sampleSize(0, 4000, 660))    // corrupt bounds stay safe
        assertEquals(1, PreviewFocus.sampleSize(1080, 2400, 0))
    }
}
