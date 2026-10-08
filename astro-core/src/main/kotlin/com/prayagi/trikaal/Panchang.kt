package com.prayagi.trikaal

import java.time.*
import swisseph.DblObj
import swisseph.SweConst

/** Colour class for the ticker: RED ashubh kaal, GREEN shubh kaal, BLUE normal. */
enum class Tone { RED, GREEN, BLUE }

/** A named time window with a tone. */
data class Span(val name: String, val start: Instant, val end: Instant, val tone: Tone) {
    fun contains(t: Instant) = t >= start && t < end
}
data class TithiSpan(val number: Int, val start: Instant, val end: Instant) {
    val paksha: String get() = if (number <= 15) "Shukla" else "Krishna"
    val name: String get() = tithiName(number)
    fun contains(t: Instant) = t >= start && t < end
}
data class VedicDate(val samvat: Int, val month: String, val paksha: String, val tithiInPaksha: Int, val tithiName: String, val adhik: Boolean)
data class VedicDay(val sunrise: Instant, val sunset: Instant, val nextSunrise: Instant, val spans: List<Span>,
    val tithis: List<TithiSpan>, val date: VedicDate, val weekday: DayOfWeek)

/** Place for all panchang calculation. Set by the owner: Prayagraj. Not a default for anything else. */
data class PanchangPlace(val label: String, val lat: Double, val lon: Double, val zone: ZoneId)
val PRAYAGRAJ = PanchangPlace("Prayagraj", 25.4358, 81.8463, ZoneId.of("Asia/Kolkata"))

private val TITHI_NAMES = listOf("Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami", "Ashtami", "Navami",
    "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi")
fun tithiName(n: Int): String = when (n) { 15 -> "Purnima"; 30 -> "Amavasya"; else -> TITHI_NAMES[(n - 1) % 15] }
private val MONTHS = listOf("Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada", "Ashwin", "Kartika",
    "Margashirsha", "Pausha", "Magha", "Phalguna")
// part numbers (1..8) of the daylight eighths by weekday: Rahu Kaal, Yamaganda, Gulika
private val PARTS = mapOf(
    DayOfWeek.MONDAY to Triple(2, 4, 6), DayOfWeek.TUESDAY to Triple(7, 3, 5), DayOfWeek.WEDNESDAY to Triple(5, 2, 4),
    DayOfWeek.THURSDAY to Triple(6, 1, 3), DayOfWeek.FRIDAY to Triple(4, 7, 2), DayOfWeek.SATURDAY to Triple(3, 6, 1),
    DayOfWeek.SUNDAY to Triple(8, 5, 7))

object Panchang {
    private fun jd(i: Instant) = 2440587.5 + i.epochSecond / 86400.0 + i.nano / 86400e9
    private fun inst(jd: Double): Instant = Instant.ofEpochMilli(Math.round((jd - 2440587.5) * 86400000.0))

    private fun sunMoon(t: Instant): Pair<Double, Double>? = synchronized(AstroCore.lock) {
        val e = AstroCore.engine; e.swe_set_sid_mode(SweConst.SE_SIDM_LAHIRI)
        val a = DoubleArray(6); val b = DoubleArray(6); val err = StringBuffer()
        if (e.swe_calc_ut(jd(t), SweConst.SE_SUN, AstroCore.flags, a, err) < 0) return null
        if (e.swe_calc_ut(jd(t), SweConst.SE_MOON, AstroCore.flags, b, err) < 0) return null
        normalize(a[0]) to normalize(b[0])
    }
    /** Moon minus Sun, 0..360. */
    private fun elongation(t: Instant): Double? = sunMoon(t)?.let { normalize(it.second - it.first) }
    fun tithiNumberAt(t: Instant): Int? = elongation(t)?.let { (it / 12.0).toInt() + 1 }

    /** First instant in [from, from+limitHours] where the tithi number differs from [number]; bisection to 1 second. */
    private fun tithiEnd(from: Instant, number: Int, limitHours: Long = 40): Instant? {
        var lo = from; var hi = from.plusSeconds(limitHours * 3600)
        if (tithiNumberAt(hi) == number) return null
        while (hi.epochSecond - lo.epochSecond > 1) {
            val mid = Instant.ofEpochSecond((lo.epochSecond + hi.epochSecond) / 2)
            if (tithiNumberAt(mid) == number) lo = mid else hi = mid
        }
        return hi
    }
    private fun tithiStart(at: Instant, number: Int): Instant {
        var hi = at; var lo = at.minusSeconds(40 * 3600L)
        while (hi.epochSecond - lo.epochSecond > 1) {
            val mid = Instant.ofEpochSecond((lo.epochSecond + hi.epochSecond) / 2)
            if (tithiNumberAt(mid) == number) hi = mid else lo = mid
        }
        return hi
    }

    /** Next sunrise (rise=true) or sunset strictly after [after], at the place. */
    fun riseSet(after: Instant, place: PanchangPlace, rise: Boolean): Instant? = synchronized(AstroCore.lock) {
        val tret = DblObj(); val err = StringBuffer()
        val geo = doubleArrayOf(place.lon, place.lat, 0.0)
        val r = AstroCore.engine.swe_rise_trans(jd(after), SweConst.SE_SUN, StringBuffer(), SweConst.SEFLG_MOSEPH,
            if (rise) SweConst.SE_CALC_RISE else SweConst.SE_CALC_SET, geo, 0.0, 15.0, tret, err)
        if (r < 0 || !tret.`val`.isFinite()) null else inst(tret.`val`)
    }

    /** Sunrise that starts the Vedic day containing [now]. */
    fun sunriseFor(now: Instant, place: PanchangPlace): Instant? {
        val localMidnight = now.atZone(place.zone).toLocalDate().atStartOfDay(place.zone).toInstant()
        val today = riseSet(localMidnight, place, true) ?: return null
        return if (now >= today) today else riseSet(localMidnight.minusSeconds(86400), place, true)
    }

    fun day(now: Instant, place: PanchangPlace = PRAYAGRAJ): Computation<VedicDay> {
        val sr = sunriseFor(now, place) ?: return Computation.Unavailable("Sunrise unavailable for this place and date")
        val ss = riseSet(sr, place, false) ?: return Computation.Unavailable("Sunset unavailable")
        val nsr = riseSet(sr.plusSeconds(3600), place, true) ?: return Computation.Unavailable("Next sunrise unavailable")
        val wd = sr.atZone(place.zone).dayOfWeek
        val dayLen = (ss.epochSecond - sr.epochSecond).toDouble()
        fun part(k: Int, name: String, tone: Tone) = Span(name, sr.plusMillis(((k - 1) * dayLen / 8 * 1000).toLong()), sr.plusMillis((k * dayLen / 8 * 1000).toLong()), tone)
        val (rk, ym, gk) = PARTS.getValue(wd)
        val spans = listOf(
            part(rk, "Rahu Kaal", Tone.RED), part(ym, "Yamaganda", Tone.RED), part(gk, "Gulika Kaal", Tone.RED),
            Span("Abhijit Muhurat", sr.plusMillis((7 * dayLen / 15 * 1000).toLong()), sr.plusMillis((8 * dayLen / 15 * 1000).toLong()), Tone.GREEN))
        // Tithi timeline from before now to 36 hours after now.
        val n0 = tithiNumberAt(now) ?: return Computation.Unavailable("Tithi unavailable")
        val tithis = ArrayList<TithiSpan>()
        var number = n0; var start = tithiStart(now, n0)
        val horizon = now.plusSeconds(36 * 3600L)
        while (start < horizon) {
            val end = tithiEnd(start.plusSeconds(1), number) ?: break
            tithis += TithiSpan(number, start, end)
            start = end; number = number % 30 + 1
        }
        val date = vedicDate(sr) ?: return Computation.Unavailable("Vikram Samvat date unavailable")
        return Computation.Available(VedicDay(sr, ss, nsr, spans, tithis, date, wd))
    }

    private fun sunSignAt(t: Instant): Int? = sunMoon(t)?.let { (it.first / 30).toInt() }
    /** Last new moon at or before [t]. */
    private fun lastNewMoon(t: Instant): Instant? {
        val e = elongation(t) ?: return null
        var guess = t.minusSeconds((e / 12.19 * 86400).toLong())   // mean elongation rate, then refine
        var lo = guess.minusSeconds(3 * 86400L); var hi = guess.plusSeconds(3 * 86400L)
        // find sign change of elongation across 360->0 inside [lo,hi]
        fun before(x: Instant) = (elongation(x) ?: 0.0) > 180.0
        if (!(before(lo) && !before(hi))) { lo = t.minusSeconds(31 * 86400L); hi = t
            // coarse scan for the latest crossing
            var x = t; var step = 3600L * 6
            while (x > lo) { val p = x.minusSeconds(step); if (before(p) && !before(x)) { lo = p; hi = x; break }; x = p }
        }
        while (hi.epochSecond - lo.epochSecond > 1) {
            val mid = Instant.ofEpochSecond((lo.epochSecond + hi.epochSecond) / 2)
            if (before(mid)) lo = mid else hi = mid
        }
        return hi
    }
    private fun nextNewMoon(t: Instant): Instant? = lastNewMoon(t.plusSeconds(30 * 86400L))?.takeIf { it > t }

    /** Vikram Samvat date of the Vedic day starting at [sunrise]: Purnimanta months (north India), year starts at Chaitra Shukla. */
    fun vedicDate(sunrise: Instant): VedicDate? {
        val n = tithiNumberAt(sunrise) ?: return null
        val nm = lastNewMoon(sunrise) ?: return null
        val nextNm = nextNewMoon(sunrise) ?: return null
        val s1 = sunSignAt(nm.plusSeconds(60)) ?: return null
        val s2 = sunSignAt(nextNm.minusSeconds(60)) ?: return null
        val adhik = s1 == s2   // no sankranti inside this lunar month
        val amanta = (s1 + 1) % 12      // 0 = Chaitra (Sun in Meena at the new moon)
        val shukla = n <= 15
        val monthIdx = if (shukla) amanta else (amanta + 1) % 12  // Purnimanta: Krishna paksha takes the next month's name
        val y = sunrise.atZone(PRAYGZ).year
        val chaitraStart = chaitraShuklaStart(y) ?: return null
        val samvat = if (sunrise >= chaitraStart) y + 57 else y + 56
        val inPaksha = if (n <= 15) n else n - 15
        return VedicDate(samvat, MONTHS[monthIdx], if (shukla) "Shukla" else "Krishna", inPaksha, tithiName(n), adhik && shukla)
    }
    private val PRAYGZ = ZoneId.of("Asia/Kolkata")
    /** New moon starting Chaitra of Gregorian year [y]: first new moon from 1 March with the Sun in sidereal Meena (index 11). */
    private fun chaitraShuklaStart(y: Int): Instant? {
        var t = LocalDate.of(y, 3, 1).atStartOfDay(PRAYGZ).toInstant()
        repeat(3) {
            val nm = nextNewMoon(t) ?: return null
            if (sunSignAt(nm.plusSeconds(60)) == 11) return nm
            t = nm
        }
        return null
    }

    /** Vedic clock: ghati (24 min) and pal (24 s) elapsed since sunrise. */
    fun ghatiPal(now: Instant, sunrise: Instant): Pair<Int, Int> {
        val s = (now.epochSecond - sunrise.epochSecond).coerceAtLeast(0)
        val ghati = (s / 1440).toInt()
        val pal = ((s % 1440) / 24).toInt()
        return ghati to pal
    }
}
