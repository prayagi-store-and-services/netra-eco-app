package com.prayagi.netraeco

import java.time.LocalDate

/** Place name and state as they were on a given birth date. Only changes backed by a cited source are applied; anything else is said plainly. */
data class PlaceAsOf(val city: String, val state: String, val note: String) {
    val label: String get() = listOf(city, state).filter { it.isNotBlank() }.joinToString(", ")
}

object PlaceHistory {
    private class StateRule(val state: String, val before: LocalDate, val then: String)
    // Sources: en.wikipedia.org/wiki/States_and_union_territories_of_India and /List_of_renamed_places_in_India (fetched 8 Oct 2026).
    private val stateRules = listOf(
        StateRule("Jharkhand", LocalDate.of(2000, 11, 15), "Bihar"),
        StateRule("Chhattisgarh", LocalDate.of(2000, 11, 1), "Madhya Pradesh"),
        StateRule("Uttarakhand", LocalDate.of(2000, 11, 9), "Uttar Pradesh"),
        StateRule("Uttarakhand", LocalDate.of(2007, 1, 1), "Uttaranchal"),
        StateRule("Telangana", LocalDate.of(2014, 6, 2), "Andhra Pradesh"),
        StateRule("Ladakh", LocalDate.of(2019, 10, 31), "Jammu and Kashmir"),
        StateRule("Odisha", LocalDate.of(2011, 9, 23), "Orissa"),
        StateRule("Tamil Nadu", LocalDate.of(1969, 1, 14), "Madras State"),
        StateRule("Karnataka", LocalDate.of(1973, 11, 1), "Mysore State"),
        StateRule("Maharashtra", LocalDate.of(1960, 5, 1), "Bombay State"),
        StateRule("Gujarat", LocalDate.of(1960, 5, 1), "Bombay State"),
        StateRule("Haryana", LocalDate.of(1966, 11, 1), "Punjab"),
        StateRule("Himachal Pradesh", LocalDate.of(1971, 1, 25), "Himachal Pradesh (Union Territory)"),
        StateRule("Mizoram", LocalDate.of(1987, 2, 20), "Mizoram (Union Territory)"),
        StateRule("Arunachal Pradesh", LocalDate.of(1987, 2, 20), "Arunachal Pradesh (Union Territory)"),
        StateRule("Goa", LocalDate.of(1987, 5, 30), "Goa (Union Territory)"),
        StateRule("Nagaland", LocalDate.of(1963, 12, 1), "Nagaland (Union Territory)"),
    )
    /** The first date on which the Republic's states and their names are the ones listed above (26 Jan 1950). */
    private val FIRST_RELIABLE = LocalDate.of(1950, 1, 26)

    /** modern city -> old name, year of rename, exact date when the source gives one. Cities with no date in the source are not renamed here. */
    private class Rename(val old: String, val year: Int, val exact: LocalDate?)
    private val renames = mapOf(
        "Mumbai" to Rename("Bombay", 1995, null),
        "Chennai" to Rename("Madras", 1996, null),
        "Kolkata" to Rename("Calcutta", 2001, null),
        "Pune" to Rename("Poona", 1978, null),
        "Vadodara" to Rename("Baroda", 1974, null),
        "Kochi" to Rename("Cochin", 1996, null),
        "Gurugram" to Rename("Gurgaon", 2016, null),
        "Prayagraj" to Rename("Allahabad", 2018, null),
        "Puducherry" to Rename("Pondicherry", 2006, LocalDate.of(2006, 10, 1)),
        "Bengaluru" to Rename("Bangalore", 2014, LocalDate.of(2014, 11, 1)),
        "Belagavi" to Rename("Belgaum", 2014, LocalDate.of(2014, 11, 1)),
        "Hubballi" to Rename("Hubli", 2014, LocalDate.of(2014, 11, 1)),
        "Mangaluru" to Rename("Mangalore", 2014, LocalDate.of(2014, 11, 1)),
        "Mysuru" to Rename("Mysore", 2014, LocalDate.of(2014, 11, 1)),
    )

    fun asOf(city: City, dob: LocalDate?): PlaceAsOf {
        if (city.country != "IN" || dob == null) return PlaceAsOf(city.name, city.state, if (dob == null) "Choose the birth date to see the place as it was then." else "")
        val notes = ArrayList<String>()
        var name = city.name
        renames[city.name]?.let { r ->
            val exact = r.exact
            when {
                exact != null -> if (dob < exact) { name = r.old; notes.add("${r.old} was renamed ${city.name} on $exact.") }
                dob.year < r.year -> { name = r.old; notes.add("${r.old} was renamed ${city.name} in ${r.year}.") }
                dob.year == r.year -> notes.add("Renamed in ${r.year} (${r.old} before); the exact date is not in our source, so today's name is shown.")
            }
        }
        var state = city.state
        if (dob < FIRST_RELIABLE) {
            state = ""; notes.add("Before 26 Jan 1950 provinces and princely states differ. Unavailable: state at birth is not resolved. Today it is ${city.state}.")
        } else {
            val rule = stateRules.filter { it.state == city.state && dob < it.before }.minByOrNull { it.before }
            if (rule != null) { state = rule.then; notes.add("Today this place is in ${city.state}; on that date it was in ${rule.then}.") }
        }
        if (city.state == "Kerala" && dob < LocalDate.of(1956, 11, 1)) { state = ""; notes.add("Unavailable: state before 1 Nov 1956 is not resolved.") }
        if (city.state == "Andhra Pradesh" && dob < LocalDate.of(1956, 11, 1)) { state = ""; notes.add("Unavailable: state before 1 Nov 1956 is not resolved.") }
        return PlaceAsOf(name, state, notes.joinToString(" "))
    }
}
