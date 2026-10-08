package com.prayagi.trikaal
import java.time.*
import org.junit.Assert.*
import org.junit.Test
class RashifalTest {
    private fun chart(date: String, time: String, lat: Double = 25.4358, lon: Double = 81.8463) =
        (AstroCore.computeChart(BirthInput("T", LocalDate.parse(date), LocalTime.parse(time), "Asia/Kolkata", lat, lon, "p")) as Computation.Available).value
    private fun reading(c: Chart, now: Instant): List<RashifalLine> {
        val g = (AstroCore.gochar(now, c) as Computation.Available).value
        return (Rashifal.daily(c, g, vimshottariDasha(c), now) as Computation.Available).value
    }
    private val now = Instant.parse("2026-10-08T06:00:00Z")
    @Test fun taraCountMatchesNineFoldRule() {
        assertEquals(1, Rashifal.taraNumber(5, 5))
        assertEquals(2, Rashifal.taraNumber(5, 6))
        assertEquals(9, Rashifal.taraNumber(5, 13))
        assertEquals(1, Rashifal.taraNumber(5, 14))
        assertEquals(3, Rashifal.taraNumber(25, 0)) // wraps past Revati: 25 -> 26,0 is 3rd
    }
    @Test fun differentPeopleGetDifferentReadings() {
        val a = reading(chart("1990-03-05", "06:30"), now)
        val b = reading(chart("1985-11-22", "21:10"), now)
        assertNotEquals(a.map { it.text }, b.map { it.text })
    }
    @Test fun samePersonSameResult() {
        val c = chart("1990-03-05", "06:30")
        assertEquals(reading(c, now), reading(c, now))
    }
    @Test fun everyLineNamesItsRuleAndNothingInvented() {
        val r = reading(chart("1990-03-05", "06:30"), now)
        assertTrue(r.all { it.rule.isNotBlank() && it.text.isNotBlank() })
        assertTrue(r.none { it.text.contains("lucky number", ignoreCase = true) && !it.text.contains("Unavailable") })
        assertTrue(r.any { it.title == "Tarabala" } && r.any { it.title == "Dasha" } && r.any { it.title == "Sun gochar" })
    }
    @Test fun unknownBirthTimeIsUnavailableNotGuessed() {
        val u = AstroCore.computeChart(BirthInput("T", LocalDate.parse("1990-03-05"), null, "Asia/Kolkata", 25.4, 81.8, "p"))
        assertTrue(u is Computation.Unavailable)
    }
}
