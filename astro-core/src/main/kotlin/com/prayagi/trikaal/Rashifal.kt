package com.prayagi.trikaal

import java.time.Instant

/** One line of the personalized daily reading. [rule] names the classical rule that produced it. */
data class RashifalLine(val title: String, val text: String, val rule: String)

/**
 * Personalized daily reading. Every line is derived from this person's own natal Moon, nakshatra,
 * Lagna houses and running dasha, compared with the live sky. Two people get different output because
 * their inputs differ. Nothing here is a random, canned or generic paragraph.
 *
 * Rules used (named in each line):
 * - Gochara: favourable houses counted from the natal Moon sign, Brihat Parashara Hora Shastra list
 *   as published at astronidan.com/transit/gochara and vedastro.org (Hindu Predictive Astrology ch. 34).
 * - Tarabala: nine-fold count from the birth nakshatra (panchangtime.com/methodology/tarabala).
 * Not applied: Vedha, Ashtakavarga, Rahu and Ketu transit results (no table vetted), lucky numbers and colours.
 */
object Rashifal {
    val FAVOURABLE_FROM_MOON: Map<Graha, Set<Int>> = mapOf(
        Graha.SUN to setOf(3, 6, 10, 11),
        Graha.MOON to setOf(1, 3, 6, 7, 10, 11),
        Graha.MARS to setOf(3, 6, 11),
        Graha.MERCURY to setOf(2, 4, 6, 8, 10, 11),
        Graha.JUPITER to setOf(2, 5, 7, 9, 11),
        Graha.VENUS to setOf(1, 2, 3, 4, 5, 8, 9, 11, 12),
        Graha.SATURN to setOf(3, 6, 11)
    )
    private val TARA_NAMES = listOf("Janma", "Sampat", "Vipat", "Kshema", "Pratyari", "Sadhaka", "Vadha", "Mitra", "Ati-mitra")
    private val TARA_MEANING = listOf("birth star, use with care", "wealth, auspicious", "danger, avoided", "well-being, auspicious",
        "obstacle, avoided", "accomplishment, auspicious", "harm, avoided", "friend, auspicious", "close friend, auspicious")

    /** 1..9 count from the birth nakshatra to today's Moon nakshatra. */
    fun taraNumber(birthNakshatra: Int, todayNakshatra: Int): Int = ((todayNakshatra - birthNakshatra + 27) % 27) % 9 + 1

    fun daily(chart: Chart, transits: List<Transit>, periods: List<Period>, now: Instant): Computation<List<RashifalLine>> {
        val natalMoon = chart.positions.firstOrNull { it.graha == Graha.MOON }
            ?: return Computation.Unavailable("Natal Moon unavailable")
        val lines = ArrayList<RashifalLine>()
        var good = 0; var counted = 0
        for ((graha, houses) in FAVOURABLE_FROM_MOON) {
            val t = transits.firstOrNull { it.position.graha == graha } ?: continue
            counted++
            val ok = t.houseFromMoon in houses
            if (ok) good++
            lines += RashifalLine(
                graha.label + " gochar",
                "${graha.label} is in ${SIGN_NAMES[t.position.rashi]}, house ${t.houseFromMoon} from your Moon (${SIGN_NAMES[natalMoon.rashi]}): " +
                    if (ok) "favourable." else "not favourable.",
                "Gochara: ${graha.label} favourable in houses ${houses.sorted().joinToString(", ")} from the natal Moon")
        }
        if (counted > 0) lines.add(0, RashifalLine("Overview",
            "$good of $counted planets are in favourable houses from your Moon today.", "Count of the gochara results below"))
        val todayMoon = transits.firstOrNull { it.position.graha == Graha.MOON }
        if (todayMoon != null) {
            val n = taraNumber(natalMoon.nakshatra, todayMoon.position.nakshatra)
            lines += RashifalLine("Tarabala",
                "Today's Moon is in ${NAKSHATRA_NAMES[todayMoon.position.nakshatra]}, counted from your birth star ${NAKSHATRA_NAMES[natalMoon.nakshatra]}: " +
                    "tara $n, ${TARA_NAMES[n - 1]} (${TARA_MEANING[n - 1]}).",
                "Tarabala: nine-fold count from the birth nakshatra")
        }
        val current = currentPeriod(periods, now)
        if (current != null) {
            val maha = current.first; val antar = current.second
            val placed = chart.positions.firstOrNull { it.graha == maha.lord }
            lines += RashifalLine("Dasha",
                "Running ${maha.lord.label} Mahadasha" + (antar?.let { ", ${it.lord.label} Antardasha" } ?: "") +
                    (placed?.let { ". ${maha.lord.label} sits in ${SIGN_NAMES[it.rashi]}, house ${chart.houseOf(it)} of your chart, nakshatra ${NAKSHATRA_NAMES[it.nakshatra]}." } ?: "."),
                "Vimshottari dasha from the birth Moon")
        } else lines += RashifalLine("Dasha", "Unavailable: today is outside this 120-year cycle.", "Vimshottari dasha")
        lines += RashifalLine("Not applied",
            "Vedha, Ashtakavarga, Rahu and Ketu transit results, lucky numbers and colours: Unavailable (no vetted rule table). Vedic reading, not certainty.",
            "Scope note")
        return Computation.Available(lines)
    }
}
