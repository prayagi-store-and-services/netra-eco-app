package com.prayagi.netraeco

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Result of the last update check done inside the app, kept on this phone only. One line per app. */
object EcoWidgetStore {
    private const val PREFS = "eco_widget_snapshot"

    fun lineFor(name: String, installedName: String?, latestName: String?, status: Status): String {
        val inst = installedName?.takeIf { it.isNotBlank() } ?: "not installed"
        val lat = latestName?.takeIf { it.isNotBlank() } ?: "Unavailable"
        return "$name: $inst, latest $lat (${statusLabel(status)})"
    }

    fun save(context: Context, lines: List<String>, updates: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("lines", lines.joinToString("\n")).putInt("updates", updates).putLong("at", System.currentTimeMillis()).apply()
    }

    fun summary(updates: Int): String = when (updates) {
        0 -> "All installed Netra apps are up to date"
        1 -> "1 update available"
        else -> "$updates updates available"
    }
}

/**
 * Netra Eco widget: the result of the last update check made inside the app (installed version, latest published
 * version, status for each Netra app) and when it was checked. No check yet shows "Unavailable". The widget makes no
 * network call and has no timer: the app redraws it after each check.
 */
class EcoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) draw(context, appWidgetManager, id)
    }

    companion object {
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            for (id in mgr.getAppWidgetIds(ComponentName(context, EcoWidgetProvider::class.java))) draw(context, mgr, id)
        }

        private fun draw(context: Context, mgr: AppWidgetManager, id: Int) {
            val p = context.getSharedPreferences("eco_widget_snapshot", Context.MODE_PRIVATE)
            val lines = p.getString("lines", null)
            val v = RemoteViews(context.packageName, R.layout.widget_eco)
            if (lines == null) {
                v.setTextViewText(R.id.eco_title, "Unavailable")
                v.setTextViewText(R.id.eco_lines, "No update check yet. Open " + NameGate.displayName(context) + " once.")
                v.setTextViewText(R.id.eco_time, "")
            } else {
                v.setTextViewText(R.id.eco_title, EcoWidgetStore.summary(p.getInt("updates", 0)))
                v.setTextViewText(R.id.eco_lines, lines)
                v.setTextViewText(R.id.eco_time, "Checked " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(p.getLong("at", 0L))) + " (when the app last checked)")
            }
            val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            v.setOnClickPendingIntent(R.id.eco_root, PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            mgr.updateAppWidget(id, v)
        }
    }
}
