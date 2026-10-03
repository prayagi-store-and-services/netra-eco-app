package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {
    @Test fun noSpeedUntilOneSecondMeasured() {
        val t = SpeedTracker()
        assertNull(t.etaSeconds(0, 1000))
        t.add(0, 0)
        t.add(500, 500)
        assertNull(t.bytesPerSecond())
        assertEquals("Unavailable", DownloadText.formatEta(t.etaSeconds(500, 1000)))
    }

    @Test fun etaFromMeasuredSpeed() {
        val t = SpeedTracker()
        t.add(0, 0)
        t.add(2000, 2000) // 1000 bytes per second
        assertEquals(8L, t.etaSeconds(2000, 10000))
    }

    @Test fun etaAdjustsWhenSpeedChanges() {
        val t = SpeedTracker(windowMs = 5000)
        t.add(0, 0)
        t.add(5000, 5000) // 1000 B/s
        assertEquals(5L, t.etaSeconds(5000, 10000))
        t.add(10000, 25000) // window now covers 5000..10000 at 4000 B/s
        assertEquals(1L, t.etaSeconds(25000, 29000))
    }

    @Test fun percentAndText() {
        assertEquals(75, DownloadText.percentLeft(25, 100))
        assertNull(DownloadText.percentLeft(5, 0))
        assertEquals("75% left, time left: 1 min 5 s (estimated)", DownloadText.line(25, 100, 65))
        assertEquals("Unavailable, time left: Unavailable (estimated)", DownloadText.line(5, 0, null))
        assertEquals("2 h 3 min", DownloadText.formatEta(7380))
    }
}
