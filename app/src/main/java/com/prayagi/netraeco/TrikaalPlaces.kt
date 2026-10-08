package com.prayagi.netraeco

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.prayagi.trikaal.PRAYAGRAJ
import com.prayagi.trikaal.PanchangPlace
import java.time.ZoneId

/** Optional place choice for panchang timings. Stored only on this phone, never sent anywhere. Declined or unavailable falls back to Prayagraj. */
object TrikaalPlaces {
    private const val P = "trikaal_place"
    /** "unset", "yes" or "no". */
    fun choice(c: Context): String = c.getSharedPreferences(P, 0).getString("choice", "unset") ?: "unset"
    fun setChoice(c: Context, v: String) { c.getSharedPreferences(P, 0).edit().putString("choice", v).apply() }
    fun load(c: Context): PanchangPlace {
        val sp = c.getSharedPreferences(P, 0)
        if (sp.getString("choice", "unset") != "yes" || !sp.contains("lat")) return PRAYAGRAJ
        return PanchangPlace("Your location", Double.fromBits(sp.getLong("lat", 0)), Double.fromBits(sp.getLong("lon", 0)), ZoneId.systemDefault())
    }
    fun save(c: Context, lat: Double, lon: Double) {
        c.getSharedPreferences(P, 0).edit().putString("choice", "yes").putLong("lat", lat.toBits()).putLong("lon", lon.toBits()).apply()
    }
    @SuppressLint("MissingPermission")
    fun lastKnown(c: Context): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val best = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        return best?.let { it.latitude to it.longitude }
    }
}

@Composable fun TrikaalLocationChoice(place: PanchangPlace, onPlace: (PanchangPlace) -> Unit) {
    val ctx = LocalContext.current
    var choice by remember { mutableStateOf(TrikaalPlaces.choice(ctx)) }
    var note by remember { mutableStateOf("") }
    fun apply() {
        val ll = TrikaalPlaces.lastKnown(ctx)
        if (ll == null) { TrikaalPlaces.setChoice(ctx, "no"); choice = "no"; note = "Location unavailable on this phone right now. Using Prayagraj."; onPlace(PRAYAGRAJ) }
        else { TrikaalPlaces.save(ctx, ll.first, ll.second); choice = "yes"; note = ""; onPlace(TrikaalPlaces.load(ctx)) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) apply() else { TrikaalPlaces.setChoice(ctx, "no"); choice = "no"; note = "Permission declined. Using Prayagraj."; onPlace(PRAYAGRAJ) }
    }
    fun ask() { if (TrikaalPlaces.lastKnown(ctx) != null) apply() else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Place for timings: ${place.label}", style = MaterialTheme.typography.titleSmall)
            if (choice == "unset") {
                Text("Rahu Kaal, muhurat and tithi change with place. Use your approximate location? It stays on this phone and is never sent anywhere. If you say no, Prayagraj is used.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ask() }) { Text("Yes, use my location") }
                    OutlinedButton(onClick = { TrikaalPlaces.setChoice(ctx, "no"); choice = "no"; onPlace(PRAYAGRAJ) }) { Text("No, use Prayagraj") }
                }
            } else {
                if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ask() }) { Text(if (choice == "yes") "Refresh my location" else "Use my location") }
                    if (choice == "yes") OutlinedButton(onClick = { TrikaalPlaces.setChoice(ctx, "no"); choice = "no"; onPlace(PRAYAGRAJ) }) { Text("Use Prayagraj") }
                }
            }
        }
    }
}
