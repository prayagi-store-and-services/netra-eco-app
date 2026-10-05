package com.prayagi.netraeco

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalBannerTest {
    private fun day(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }

    @Test fun showsDussehraOnTheDay() {
        val b = FestivalBanner.pick(day(2026, 10, 20))
        assertNotNull(b)
        assertEquals(BannerKind.FEST, b!!.kind)
        assertTrue(b.text.contains("Dussehra"))
    }

    @Test fun showsComingSoonWithinThreeDays() {
        val b = FestivalBanner.pick(day(2026, 10, 9))
        assertNotNull(b)
        assertEquals(BannerKind.SOON, b!!.kind)
    }

    @Test fun showsIndiaIndependenceDay() {
        val b = FestivalBanner.pick(day(2026, 8, 15))
        assertNotNull(b)
        assertTrue(b!!.text.contains("India"))
    }

    @Test fun showsNothingWhenNoDataAndNoNonIndianDays() {
        assertNull(FestivalBanner.pick(day(2028, 3, 3)))
        assertTrue(FestivalData.FEST.none { it.name.contains("Passover") || it.name.contains("Lunar") || it.name.contains("Hanukkah") })
    }
}
