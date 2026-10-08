package com.prayagi.trikaal
import java.time.*
import org.junit.Assert.*
import org.junit.Test
class AstroCoreTest {
    private fun input(date: String = "2000-01-01", time: String? = "12:00", tz: String? = "Asia/Kolkata", lat: Double=25.4358, lon:Double=81.8463) = BirthInput("Test",LocalDate.parse(date),time?.let(LocalTime::parse),tz,lat,lon,"Test place")
    private fun chart(b:BirthInput=input()) = (AstroCore.computeChart(b) as Computation.Available).value
    @Test fun boundaries() {
        assertEquals(0,Position(Graha.SUN,29.999,1.0).rashi)
        assertEquals(1,Position(Graha.SUN,30.0,1.0).rashi)
        assertEquals(0,Position(Graha.MOON,13.333333-0.01,0.0).nakshatra)
        assertEquals(1,Position(Graha.MOON,13.333333+0.01,0.0).nakshatra)
        assertEquals(4,Position(Graha.MOON,13.32,0.0).pada)
        assertEquals(1,Position(Graha.MOON,13.35,0.0).pada)
    }
    @Test fun unknownIsNotDefaulted() {
        assertTrue(AstroCore.computeChart(input(time=null)) is Computation.Unavailable)
        assertTrue(AstroCore.computeChart(input(tz="Bad/Zone")) is Computation.Unavailable)
        assertTrue(AstroCore.computeChart(input(lat=Double.NaN)) is Computation.Unavailable)
    }
    @Test fun dstGapOverlap() {
        assertTrue(AstroCore.resolveInstant(input("2024-03-10","02:30","America/New_York")) is Computation.Unavailable)
        assertTrue(AstroCore.resolveInstant(input("2024-11-03","01:30","America/New_York")) is Computation.Unavailable)
        val explicit = input("2024-11-03","01:30","America/New_York").copy(offset=ZoneOffset.of("-04:00"))
        assertEquals(Instant.parse("2024-11-03T05:30:00Z"),(AstroCore.resolveInstant(explicit) as Computation.Available).value)
    }
    @Test fun historicalIndiaAndMidnight() {
        for(date in listOf("1900-01-01","1943-06-01","1947-01-01","2000-01-01")) {
            val b=input(date,"00:00")
            val actual=(AstroCore.resolveInstant(b) as Computation.Available).value
            assertEquals(LocalDateTime.of(b.dateLocal,b.timeLocal).atZone(ZoneId.of("Asia/Kolkata")).toInstant(),actual)
            assertTrue(chart(b).positions.all { it.longitude in 0.0..<360.0 })
        }
    }
    @Test fun hemispheresAndDateLine() {
        for((lat,lon) in listOf(-33.86 to 151.2,51.5 to -0.12,0.0 to 179.9,0.0 to -179.9)) {
            val c=chart(input(lat=lat,lon=lon)); assertTrue(c.lagna in 0.0..<360.0)
            assertEquals(9,c.positions.size); assertTrue(c.positions.all { c.houseOf(it) in 1..12 })
        }
    }
    @Test fun nodesDashaAndTransits() {
        val c=chart(); val r=c.positions.single{it.graha==Graha.RAHU};val k=c.positions.single{it.graha==Graha.KETU}
        assertEquals(180.0,normalize(k.longitude-r.longitude),0.000001)
        val p=vimshottariDasha(c);assertEquals(9,p.size)
        for(i in 0..7) assertEquals(p[i].end,p[i+1].start)
        p.forEach { assertEquals(it.start,it.children.first().start);assertEquals(it.end,it.children.last().end);assertEquals(9,it.children.size) }
        assertNotNull(currentPeriod(p,c.born)); assertNull(currentPeriod(p,p.last().end))
        val t=(AstroCore.gochar(c.born,c) as Computation.Available).value
        assertTrue(t.all { it.houseFromLagna in 1..12 && it.houseFromMoon in 1..12 })
    }
}
