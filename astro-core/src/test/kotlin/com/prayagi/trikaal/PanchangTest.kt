package com.prayagi.trikaal
import java.time.*
import org.junit.Assert.*
import org.junit.Test
class PanchangTest {
    private val now = ZonedDateTime.of(2026, 10, 8, 9, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant()
    private fun day() = (Panchang.day(now) as Computation.Available).value
    private fun ist(i: Instant) = i.atZone(ZoneId.of("Asia/Kolkata")).toLocalTime()
    @Test fun printAndCheckDay() {
        val d = day()
        println("sunrise ${ist(d.sunrise)} sunset ${ist(d.sunset)} weekday ${d.weekday}")
        d.spans.forEach { println("${it.name} ${ist(it.start)}-${ist(it.end)} ${it.tone}") }
        d.tithis.forEach { println("tithi ${it.number} ${it.name} ${it.paksha} ${it.start} -> ${it.end}") }
        println(d.date)
        assertEquals(DayOfWeek.THURSDAY, d.weekday)
        assertTrue(ist(d.sunrise).isAfter(LocalTime.of(5, 30)) && ist(d.sunrise).isBefore(LocalTime.of(6, 30)))
        val ab = d.spans.single { it.name == "Abhijit Muhurat" }
        assertTrue(ist(ab.start).isAfter(LocalTime.of(11, 10)) && ist(ab.start).isBefore(LocalTime.of(11, 45)))
        assertEquals(Tone.GREEN, ab.tone)
        assertTrue(d.spans.filter { it.tone == Tone.RED }.size == 3)
        assertEquals(2083, d.date.samvat)
    }
    @Test fun tithiTimelineIsContiguousAndAdvances() {
        val t = day().tithis
        assertTrue(t.size >= 2)
        for (i in 1 until t.size) { assertEquals(t[i - 1].end, t[i].start); assertEquals(t[i - 1].number % 30 + 1, t[i].number) }
        assertTrue(t.first().contains(now))
    }
    @Test fun samvatChangesAtChaitra() {
        val before = Panchang.vedicDate(ZonedDateTime.of(2026, 3, 10, 6, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant())!!
        val after = Panchang.vedicDate(ZonedDateTime.of(2026, 4, 10, 6, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant())!!
        assertEquals(2082, before.samvat); assertEquals(2083, after.samvat)
    }
    @Test fun ghatiPal() {
        val sr = Instant.parse("2026-10-08T00:00:00Z")
        assertEquals(0 to 0, Panchang.ghatiPal(sr, sr))
        assertEquals(1 to 0, Panchang.ghatiPal(sr.plusSeconds(1440), sr))
        assertEquals(2 to 5, Panchang.ghatiPal(sr.plusSeconds(2880 + 5 * 24), sr))
    }
}
