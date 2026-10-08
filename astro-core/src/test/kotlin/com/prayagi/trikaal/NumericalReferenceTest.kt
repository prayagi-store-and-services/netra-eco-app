package com.prayagi.trikaal
import org.junit.Test
import org.junit.Assert.*
import java.time.*
/** Independent current C Swiss Ephemeris 2.10.03, Moshier/Lahiri. Not user kundli fixtures. */
class NumericalReferenceTest {
@Test fun currentEngineVectors() {
run {
val b=BirthInput("Reference",LocalDate.parse("1900-01-01"),LocalTime.NOON,"Asia/Kolkata",25.4358,81.8463,"Prayagraj")
val c=(AstroCore.computeChart(b) as Computation.Available).value
assertEquals(Instant.parse("1900-01-01T06:38:50Z"),c.born)
val values=listOf(257.97043046511544,253.93003027405013,261.61575195431345,236.88696860049563,218.72464842018405,284.25345280267766,245.28363140228794,236.68125682922437)
c.positions.take(8).forEachIndexed { i,p -> assertEquals(values[i],p.longitude,0.01) }
assertEquals(353.91989685956014,c.lagna,0.01)
}
run {
val b=BirthInput("Reference",LocalDate.parse("1943-06-01"),LocalTime.NOON,"Asia/Kolkata",25.4358,81.8463,"Prayagraj")
val c=(AstroCore.computeChart(b) as Computation.Available).value
assertEquals(Instant.parse("1943-06-01T05:30:00Z"),c.born)
val values=listOf(46.794824329083546,26.901191923674226,340.51972267062894,34.47777995694898,90.93649802196595,90.16088846203753,52.10344794677466,116.44124763978151)
c.positions.take(8).forEachIndexed { i,p -> assertEquals(values[i],p.longitude,0.01) }
assertEquals(123.9572875285195,c.lagna,0.01)
}
run {
val b=BirthInput("Reference",LocalDate.parse("2000-01-01"),LocalTime.NOON,"Asia/Kolkata",25.4358,81.8463,"Prayagraj")
val c=(AstroCore.computeChart(b) as Computation.Available).value
assertEquals(Instant.parse("2000-01-01T06:30:00Z"),c.born)
val values=listOf(256.28208862810874,196.71168769046722,303.93234197795,247.67955816683266,1.3905643657975275,217.43554276125596,16.547044078005538,101.19956757320953)
c.positions.take(8).forEachIndexed { i,p -> assertEquals(values[i],p.longitude,0.01) }
assertEquals(349.2370246904135,c.lagna,0.01)
}
run {
val b=BirthInput("Reference",LocalDate.parse("2026-10-09"),LocalTime.NOON,"Asia/Kolkata",25.4358,81.8463,"Prayagraj")
val c=(AstroCore.computeChart(b) as Computation.Available).value
assertEquals(Instant.parse("2026-10-09T06:30:00Z"),c.born)
val values=listOf(171.7546549240982,154.83723506880784,102.2045601196063,196.57606817728532,116.78862235972258,193.55561455282736,346.69554319577304,303.0438069578168)
c.positions.take(8).forEachIndexed { i,p -> assertEquals(values[i],p.longitude,0.01) }
assertEquals(250.72126598667822,c.lagna,0.01)
}
}
}
