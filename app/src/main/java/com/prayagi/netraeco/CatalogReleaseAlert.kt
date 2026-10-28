package com.prayagi.netraeco

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

/** Uses the existing six-hour worker and foreground catalog refresh, never a polling loop. */
object CatalogReleaseAlert {
    private const val PREFS = "catalog_release_alerts"
    private const val CHANNEL = "netra_new_app_releases"
    internal fun newlyReleased(planned: Set<String>, live: Set<String>): Set<String> = planned.intersect(live)

    internal fun enabled(c: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return false
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Null/invalid snapshots never erase the last successful observation or invent a release. */
    @Synchronized
    fun observe(c: Context, json: String, rows: List<AppRow>) {
        val source = runCatching { JSONObject(json) }.getOrNull() ?: return
        if (!source.has("projects")) return
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet("planned", emptySet()).orEmpty().toSet()
        val planned = Upcoming.parse(json).map { it.id }.toSet()
        val live = rows.filter { it.latest != null }.map { it.app.id }.toSet()
        val pending = prefs.getStringSet("pending", emptySet()).orEmpty().toSet() + newlyReleased(old, live)
        // Keep removed plans until a verified release appears, including across temporary metadata failures.
        prefs.edit().putStringSet("planned", (old + planned) - live).putStringSet("pending", pending).apply()
        if (pending.isEmpty() || !enabled(c)) return
        val names = pending.mapNotNull { id -> rows.firstOrNull { it.app.id == id && it.latest != null }?.app?.name }
        if (names.isEmpty()) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "New Netra apps", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, 7433,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            nm.notify(7433, NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("New Netra app released")
                .setContentText(names.joinToString(", ") + ". Tap to view in Current.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString(", ") + ". Tap to view in Current."))
                .setContentIntent(open).setAutoCancel(true).build())
            val delivered = pending.filter { id -> rows.any { it.app.id == id && it.latest != null } }.toSet()
            prefs.edit().putStringSet("pending", pending - delivered).apply()
        } catch (_: SecurityException) { /* Permission revoked during post: leave pending for next allowed check. */ }
    }

    fun check(c: Context) {
        val json = Net.fetchText(Net.CATALOG_URL) ?: return
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val candidates = prefs.getStringSet("planned", emptySet()).orEmpty() + prefs.getStringSet("pending", emptySet()).orEmpty()
        val rows = Net.parseCatalog(json).filter { it.id in candidates }.map { AppRow(it, null, Net.fetchLatest(it)) }
        observe(c, json, rows)
    }
}
