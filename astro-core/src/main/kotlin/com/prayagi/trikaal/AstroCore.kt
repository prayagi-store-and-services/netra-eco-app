package com.prayagi.trikaal

import java.time.*
import swisseph.SwissEph
import swisseph.SweConst
import kotlin.math.floor

/** All dates are Gregorian. Ambiguous/nonexistent local times require an explicit offset. */
data class BirthInput(val name: String, val dateLocal: LocalDate, val timeLocal: LocalTime?,
    val tzId: String?, val lat: Double?, val lon: Double?, val placeLabel: String,
    val offset: ZoneOffset? = null)
sealed class Computation<out T> {
    data class Available<T>(val value: T) : Computation<T>()
    data class Unavailable(val reason: String) : Computation<Nothing>()
}
enum class Graha(val label: String, val swissId: Int) {
    SUN("Sun", SweConst.SE_SUN), MOON("Moon", SweConst.SE_MOON), MARS("Mars", SweConst.SE_MARS),
    MERCURY("Mercury", SweConst.SE_MERCURY), JUPITER("Jupiter", SweConst.SE_JUPITER),
    VENUS("Venus", SweConst.SE_VENUS), SATURN("Saturn", SweConst.SE_SATURN),
    RAHU("Rahu (mean)", SweConst.SE_MEAN_NODE), KETU("Ketu", -1)
}
data class Position(val graha: Graha, val longitude: Double, val speed: Double) {
    val rashi: Int get() = floor(longitude / 30).toInt()
    val degreeInSign: Double get() = longitude % 30
    val nakshatra: Int get() = floor(longitude * 27 / 360).toInt()
    val pada: Int get() = floor((longitude * 108 / 360) % 4).toInt() + 1
    val retrograde: Boolean get() = speed < 0
}
data class Chart(val input: BirthInput, val born: Instant, val positions: List<Position>,
    val lagna: Double, val engineVersion: String) {
    val lagnaRashi: Int get() = floor(lagna / 30).toInt()
    fun houseOf(p: Position): Int = (p.rashi - lagnaRashi + 12) % 12 + 1
    val houseLords: List<Graha> get() = (0..11).map { SIGN_LORDS[(lagnaRashi + it) % 12] }
}
data class Period(val lord: Graha, val start: Instant, val end: Instant, val children: List<Period> = emptyList()) {
    fun contains(now: Instant) = now >= start && now < end
}
data class Transit(val position: Position, val houseFromLagna: Int, val houseFromMoon: Int)

val SIGN_NAMES = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
val NAKSHATRA_NAMES = listOf("Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra", "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha", "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishtha", "Shatabhisha", "Purva Bhadrapada", "Uttara Bhadrapada", "Revati")
val SIGN_LORDS = listOf(Graha.MARS, Graha.VENUS, Graha.MERCURY, Graha.MOON, Graha.SUN, Graha.MERCURY, Graha.VENUS, Graha.MARS, Graha.JUPITER, Graha.SATURN, Graha.SATURN, Graha.JUPITER)
private val DASHA_LORDS = listOf(Graha.KETU, Graha.VENUS, Graha.SUN, Graha.MOON, Graha.MARS, Graha.RAHU, Graha.JUPITER, Graha.SATURN, Graha.MERCURY)
private val DASHA_YEARS = listOf(7,20,6,10,7,18,16,19,17)
private const val YEAR_SECONDS = 365.25 * 86400
fun normalize(deg: Double): Double = ((deg % 360) + 360) % 360

/** Serialized: upstream 2.01 Java port has shared mutable state. No network or ephemeris files. */
object AstroCore {
    internal val engine = SwissEph()
    internal val lock = Any()
    internal val flags = SweConst.SEFLG_MOSEPH or SweConst.SEFLG_SIDEREAL or SweConst.SEFLG_SPEED
    fun resolveInstant(input: BirthInput): Computation<Instant> {
        val time = input.timeLocal ?: return Computation.Unavailable("Birth time unknown: Moon, Lagna, houses and dasha timing unavailable")
        val tz = input.tzId ?: return Computation.Unavailable("IANA timezone required")
        return try {
            val zone = ZoneId.of(tz)
            val local = LocalDateTime.of(input.dateLocal, time)
            val offsets = zone.rules.getValidOffsets(local)
            if (offsets.isEmpty()) Computation.Unavailable("Local time did not exist in this timezone")
            else if (offsets.size > 1 && input.offset == null) Computation.Unavailable("Ambiguous local time: choose the recorded UTC offset")
            else if (input.offset != null && input.offset !in offsets) Computation.Unavailable("UTC offset does not match this local time")
            else Computation.Available(local.toInstant(input.offset ?: offsets.single()))
        } catch (e: DateTimeException) { Computation.Unavailable("Unknown or invalid timezone") }
    }
    private fun jd(instant: Instant): Double = 2440587.5 + instant.epochSecond / 86400.0 + instant.nano / 86400e9
    private fun positions(instant: Instant): Computation<List<Position>> = synchronized(lock) {
        engine.swe_set_sid_mode(SweConst.SE_SIDM_LAHIRI)
        val out = mutableListOf<Position>()
        for (g in Graha.entries.filter { it != Graha.KETU }) {
            val xx = DoubleArray(6); val err = StringBuffer()
            val result = engine.swe_calc_ut(jd(instant), g.swissId, flags, xx, err)
            if (result < 0 || xx.any { !it.isFinite() } || (result and SweConst.SEFLG_MOSEPH) == 0)
                return@synchronized Computation.Unavailable("Engine could not calculate ${g.label} in Moshier mode")
            out += Position(g, normalize(xx[0]), xx[3])
        }
        val rahu = out.single { it.graha == Graha.RAHU }
        out += Position(Graha.KETU, normalize(rahu.longitude + 180), rahu.speed)
        Computation.Available(out)
    }
    fun computeChart(input: BirthInput): Computation<Chart> {
        if (input.lat == null || input.lon == null) return Computation.Unavailable("Birth coordinates required for Lagna and houses")
        if (!input.lat.isFinite() || !input.lon.isFinite() || input.lat !in -89.999..89.999 || input.lon !in -180.0..180.0)
            return Computation.Unavailable("Invalid coordinates; polar birth charts unsupported")
        val born = when (val r = resolveInstant(input)) { is Computation.Available -> r.value; is Computation.Unavailable -> return r }
        // Moshier validity range is constrained more conservatively for this initial implementation.
        if (input.dateLocal.year !in 1800..2399) return Computation.Unavailable("This release supports birth years 1800-2399 only")
        return synchronized(lock) {
            val p = when (val r = positions(born)) { is Computation.Available -> r.value; is Computation.Unavailable -> return@synchronized r }
            val cusp = DoubleArray(13); val asc = DoubleArray(10)
            engine.swe_set_sid_mode(SweConst.SE_SIDM_LAHIRI)
            val result = engine.swe_houses(jd(born), SweConst.SEFLG_SIDEREAL, input.lat, input.lon, 'E'.code, cusp, asc)
            if (result < 0 || !asc[0].isFinite()) Computation.Unavailable("Lagna calculation unavailable")
            else Computation.Available(Chart(input, born, p, normalize(asc[0]), engine.swe_java_version()))
        }
    }
    fun gochar(now: Instant, chart: Chart): Computation<List<Transit>> = when (val r = positions(now)) {
        is Computation.Unavailable -> r
        is Computation.Available -> {
            val moon = chart.positions.single { it.graha == Graha.MOON }.rashi
            Computation.Available(r.value.map { Transit(it, chart.houseOf(it), (it.rashi - moon + 12) % 12 + 1) })
        }
    }
}
private fun addSeconds(start: Instant, seconds: Double): Instant = start.plusMillis((seconds * 1000).toLong())
/** One 120-year cycle beginning at the birth Moon's current mahadasha start. */
fun vimshottariDasha(chart: Chart): List<Period> {
    val moon = chart.positions.single { it.graha == Graha.MOON }
    val first = moon.nakshatra % 9
    val progress = (moon.longitude * 27 / 360) % 1
    var start = addSeconds(chart.born, -progress * DASHA_YEARS[first] * YEAR_SECONDS)
    return (0..8).map { step ->
        val index = (first + step) % 9
        val duration = DASHA_YEARS[index] * YEAR_SECONDS
        val end = addSeconds(start, duration)
        var subStart = start
        val children = (0..8).map { sub ->
            val si = (index + sub) % 9
            val subEnd = if (sub == 8) end else addSeconds(subStart, duration * DASHA_YEARS[si] / 120)
            Period(DASHA_LORDS[si], subStart, subEnd).also { subStart = subEnd }
        }
        Period(DASHA_LORDS[index], start, end, children).also { start = end }
    }
}
fun currentPeriod(periods: List<Period>, now: Instant): Pair<Period, Period?>? = periods.firstOrNull { it.contains(now) }?.let { it to it.children.firstOrNull { p -> p.contains(now) } }

/** Live sky positions for the ticker (no birth chart needed). */
fun AstroCore.currentPositions(now: Instant): Computation<List<Position>> = synchronized(lock) {
    engine.swe_set_sid_mode(SweConst.SE_SIDM_LAHIRI)
    val out = mutableListOf<Position>()
    for (g in Graha.entries.filter { it != Graha.KETU }) {
        val xx = DoubleArray(6); val err = StringBuffer()
        val r = engine.swe_calc_ut(2440587.5 + now.epochSecond / 86400.0, g.swissId, flags, xx, err)
        if (r < 0 || xx.any { !it.isFinite() }) return@synchronized Computation.Unavailable("Engine could not calculate ${g.label}")
        out += Position(g, normalize(xx[0]), xx[3])
    }
    val rahu = out.single { it.graha == Graha.RAHU }
    out += Position(Graha.KETU, normalize(rahu.longitude + 180), rahu.speed)
    Computation.Available(out)
}
