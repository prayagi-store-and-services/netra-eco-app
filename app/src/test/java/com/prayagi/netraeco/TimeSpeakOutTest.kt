package com.prayagi.netraeco

import android.media.AudioManager
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSpeakOutTest {

    private fun at(hour: Int, minute: Int): Long =
        Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 19, hour, minute, 0) }.timeInMillis

    private fun slot(millis: Long): Pair<Int, Int> =
        Calendar.getInstance().apply { timeInMillis = millis }.let { it.get(Calendar.HOUR_OF_DAY) to it.get(Calendar.MINUTE) }

    @Test fun midHourGoesToHalfPast() = assertEquals(10 to 30, slot(TimeSpeakOut.nextSlotMillis(at(10, 15))))

    @Test fun onHalfPastGoesToNextHour() = assertEquals(11 to 0, slot(TimeSpeakOut.nextSlotMillis(at(10, 30))))

    @Test fun justBeforeHourGoesToHour() = assertEquals(11 to 0, slot(TimeSpeakOut.nextSlotMillis(at(10, 59))))

    @Test fun beforeMidnightSkipsToMorning() = assertEquals(6 to 0, slot(TimeSpeakOut.nextSlotMillis(at(22, 45))))

    @Test fun insideSilentWindowSkipsToMorning() = assertEquals(6 to 0, slot(TimeSpeakOut.nextSlotMillis(at(23, 30))))

    @Test fun justBeforeSixGoesToSix() = assertEquals(6 to 0, slot(TimeSpeakOut.nextSlotMillis(at(5, 45))))

    @Test fun sixAmIsNotSilent() = assertEquals(6 to 30, slot(TimeSpeakOut.nextSlotMillis(at(6, 0))))

    @Test fun silentWindowBounds() {
        assertTrue(TimeSpeakOut.isSilentAt(at(23, 0)))
        assertTrue(TimeSpeakOut.isSilentAt(at(3, 15)))
        assertTrue(!TimeSpeakOut.isSilentAt(at(6, 0)))
        assertTrue(!TimeSpeakOut.isSilentAt(at(22, 59)))
    }

    @Test fun allowedWhenNormalRingerInDay() =
        assertNull(TimeSpeakOut.skipReason(at(12, 0), AudioManager.RINGER_MODE_NORMAL))

    @Test fun skippedInSilentHours() =
        assertEquals("Silent hours (11 PM to 6 AM)", TimeSpeakOut.skipReason(at(1, 30), AudioManager.RINGER_MODE_NORMAL))

    @Test fun skippedOnVibrate() =
        assertEquals("Phone on silent or vibrate", TimeSpeakOut.skipReason(at(12, 0), AudioManager.RINGER_MODE_VIBRATE))

    @Test fun skippedOnSilent() =
        assertEquals("Phone on silent or vibrate", TimeSpeakOut.skipReason(at(12, 0), AudioManager.RINGER_MODE_SILENT))

    @Test fun englishTimeFormats() {
        assertEquals("The time is 9:30 PM.", TimeSpeaker.englishTime(at(21, 30)))
        assertEquals("The time is 12:00 AM.", TimeSpeaker.englishTime(at(0, 0)))
        assertEquals("The time is 12:05 PM.", TimeSpeaker.englishTime(at(12, 5)))
    }

    @Test fun hindiTimeFormats() {
        assertEquals("समय है, रात के 9 बजकर 30 मिनट।", TimeSpeaker.hindiTime(at(21, 30)))
        assertEquals("समय है, दोपहर के 12 बजे।", TimeSpeaker.hindiTime(at(12, 0)))
        assertEquals("समय है, सुबह के 6 बजे।", TimeSpeaker.hindiTime(at(6, 0)))
    }

    @Test fun slotLinesMatchSpeakerWording() {
        val t = at(10, 30)
        val (hi, en) = TimeSpeakOut.slotLines(t)
        assertEquals(TimeSpeaker.hindiTime(t), hi)
        assertEquals(TimeSpeaker.englishTime(t), en)
    }

    @Test fun slotLinesForSlotNotRequestTime() {
        // The precomputed line must say the slot time, so a slightly early or late alarm
        // still announces the exact slot it belongs to.
        val slotAt = TimeSpeakOut.nextSlotMillis(at(10, 15))
        val (_, en) = TimeSpeakOut.slotLines(slotAt)
        assertTrue(en.contains("10:30"))
    }
}
