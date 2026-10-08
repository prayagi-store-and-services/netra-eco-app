package com.prayagi.netraeco

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One place from the bundled GeoNames list (CC BY 4.0, geonames.org). Coordinates and timezone come from here, never from the user. */
data class City(val name: String, val state: String, val country: String, val lat: Double, val lon: Double, val tz: String, val pop: Long, val alt: List<String>) {
    val label: String get() = listOf(name, state, country).filter { it.isNotBlank() }.joinToString(", ")
}

object TrikaalCities {
    @Volatile private var cache: List<City>? = null
    /** The asset keeps its name ending, so the loader finds it even if the upload tool added a numeric prefix. */
    fun all(c: Context): List<City> {
        cache?.let { return it }
        val file = c.assets.list("")?.firstOrNull { it.endsWith("trikaal_cities.tsv") } ?: return emptyList()
        val out = ArrayList<City>(40000)
        c.assets.open(file).bufferedReader().useLines { lines ->
            lines.forEach { l ->
                val p = l.split('\t')
                if (p.size >= 7) {
                    val lat = p[3].toDoubleOrNull(); val lon = p[4].toDoubleOrNull()
                    if (lat != null && lon != null) out.add(City(p[0], p[1], p[2], lat, lon, p[5], p[6].toLongOrNull() ?: 0, p.getOrNull(7)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()))
                }
            }
        }
        cache = out
        return out
    }
    /** Names starting with the query first, then names containing it; biggest places first. At most 8. */
    fun search(all: List<City>, q: String): List<City> {
        val s = q.trim().lowercase(Locale.ROOT)
        if (s.length < 2) return emptyList()
        val starts = ArrayList<City>(); val contains = ArrayList<City>()
        for (c in all) {
            val n = c.name.lowercase(Locale.ROOT)
            when {
                n.startsWith(s) || c.alt.any { it.lowercase(Locale.ROOT).startsWith(s) } -> starts.add(c)
                n.contains(s) -> contains.add(c)
            }
            if (starts.size >= 8) break
        }
        return (starts + contains).take(8)
    }
}

private val DOB_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

/** Birth details: name, date (calendar), time (12-hour AM/PM clock) and place (search). Everything else is worked out by the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TrikaalBirthForm(profile: TrikaalProfile, onChange: (TrikaalProfile) -> Unit) {
    val ctx = LocalContext.current
    var cities by remember { mutableStateOf<List<City>>(emptyList()) }
    LaunchedEffect(Unit) { cities = withContext(Dispatchers.IO) { TrikaalCities.all(ctx) } }
    var query by remember(profile.place) { mutableStateOf(profile.place) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(profile.date) }.getOrNull()
    val time = runCatching { LocalTime.parse(profile.time) }.getOrNull()
    val hits = remember(query, cities, profile.latitude) { if (profile.latitude.isNotBlank() && query == profile.place) emptyList() else TrikaalCities.search(cities, query) }

    OutlinedTextField(value = profile.name, onValueChange = { onChange(profile.copy(name = it)) }, label = { Text("Name / नाम") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) { Text(if (date != null) "Date of birth: ${DOB_FMT.format(date)}" else "Choose date of birth / जन्म तिथि") }
    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) { Text(if (time != null) "Birth time: ${TIME_FMT.format(time)}" else "Choose birth time / जन्म समय (AM/PM)") }
    if (time != null) TextButton(onClick = { onChange(profile.copy(time = "")) }) { Text("I do not know my birth time") }
    else Text("No birth time chosen: the chart will show Unavailable instead of guessing.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = query,
        onValueChange = { query = it; onChange(profile.copy(place = it, latitude = "", longitude = "", zone = "")) },
        label = { Text("Birth place / जन्म स्थान (type your city)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
    )
    hits.forEach { c ->
        TextButton(onClick = {
            query = c.label
            onChange(profile.copy(place = c.label, latitude = c.lat.toString(), longitude = c.lon.toString(), zone = c.tz, offset = ""))
        }, modifier = Modifier.fillMaxWidth()) { Text(c.label, modifier = Modifier.fillMaxWidth()) }
    }
    if (profile.latitude.isNotBlank()) Text("Place set: ${profile.place}", style = MaterialTheme.typography.bodySmall)
    else if (query.length >= 2 && hits.isEmpty() && cities.isNotEmpty()) Text("Unavailable: this place is not in the built-in list. Try the nearest larger city.", style = MaterialTheme.typography.bodySmall)
    Text("Places from GeoNames (geonames.org, CC BY 4.0), stored in the app. Latitude, longitude and time zone are filled in for you.", style = MaterialTheme.typography.bodySmall)

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            yearRange = 1900..LocalDate.now().year,
            selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis() })
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                st.selectedDateMillis?.let { ms -> onChange(profile.copy(date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString())) }
                showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }) { DatePicker(state = st) }
    }
    if (showTime) {
        val st = rememberTimePickerState(initialHour = time?.hour ?: 6, initialMinute = time?.minute ?: 0, is24Hour = false)
        AlertDialog(onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = {
                onChange(profile.copy(time = LocalTime.of(st.hour, st.minute).toString())); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = st) })
    }
}
