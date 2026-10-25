package com.prayagi.netraeco

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Update alert: every 6 hours (network required) asks the repo's latest release whether a newer version exists,
 * posts one notification per new version. APK bytes are not downloaded by periodic work.
 * A notification tap opens a download choice; a later explicit Install tap opens Android's installer.
 * Old background-download work is cancelled and its compatibility worker performs no network activity.
 */
object UpdateAlert {
    const val EXTRA_AUTO_UPDATE = "netra_auto_update"
    private const val NOTIFICATION_ID = 7431
    private const val WORK_NAME = "netra_update_alert"
    internal const val PREFS = "netra_update_alert"
    // Netra Eco beeps (default notification sound); the other apps use a silent channel.
    private const val BEEP = true
    private val CHANNEL_ID = if (BEEP) "netra_app_update_beep" else "netra_app_update"

    const val MOBILE_LIMIT_BYTES = 100L * 1024 * 1024
    internal fun shouldPreDownload(online: Boolean, unmetered: Boolean, roaming: Boolean, dataSaverOn: Boolean, batteryLow: Boolean, alreadySaved: Boolean, sizeBytes: Long): Boolean = false

    fun start(activity: Activity) {
        try { schedule(activity.applicationContext) } catch (_: Exception) { /* background check unavailable; the manual check still works */ }
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7432)
        }
        handle(activity, activity.intent)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateAlertWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).cancelUniqueWork("netra_update_download")
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** A notification tap opens an explicit download choice, never the installer. */
    fun handle(activity: Activity, intent: Intent?) {
        if (activity !is UpdateEntryActivity || intent?.getBooleanExtra(EXTRA_AUTO_UPDATE, false) != true) return
        intent.removeExtra(EXTRA_AUTO_UPDATE)
        android.app.AlertDialog.Builder(activity)
            .setTitle("Download Netra Ecosystem update?")
            .setMessage("Download the verified update from the Netra Ecosystem GitHub release. After download, use the Install button to open Android's installer. Keep Play Protect enabled; do not bypass a security warning.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Download") { _, _ ->
                val app = activity.applicationContext
                Thread {
                    try {
                        val self = Net.selfApp(app)
                        val latest = Net.fetchLatest(self) ?: throw IllegalStateException("Update information unavailable.")
                        val current = Net.installed(app, self.packageName)
                        check(statusForRelease(current?.first, current?.second, latest) == Status.UpdateAvailable) { "No newer version found." }
                        DownloadCenter.start(app, self, latest)
                        activity.runOnUiThread { Toast.makeText(app, "Download started. Review it under Update before installing.", Toast.LENGTH_LONG).show() }
                    } catch (e: Exception) {
                        activity.runOnUiThread { Toast.makeText(app, e.message ?: "Update unavailable.", Toast.LENGTH_LONG).show() }
                    }
                }.start()
            }.show()
    }

    internal fun checkAndNotify(c: Context) {
        val self = Net.selfApp(c)
        val latest = Net.fetchLatest(self) ?: return
        val installed = Net.installed(c, self.packageName)
        if (statusForRelease(installed?.first, installed?.second, latest) != Status.UpdateAvailable) return
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("notified_tag", "") == latest.tag) return
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates",
                    if (BEEP) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW)
            )
        }
        val launch = Intent(c, UpdateEntryActivity::class.java)
        launch.putExtra(EXTRA_AUTO_UPDATE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update available")
            .setContentText("Version " + latest.versionName + " is ready. Tap to update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        prefs.edit().putString("notified_tag", latest.tag).apply()
        // Metadata checks may notify; APK bytes require the user to choose Download.
    }

    /** The verified file for a tag, or null when it is missing or fails the integrity re-check. Never trusts a partial. */
    internal fun verifiedFileFor(c: Context, app: CatalogApp, latest: LatestRelease): java.io.File? {
        val file = java.io.File(java.io.File(c.cacheDir, "updates"), "${app.id}-${latest.tag}.apk")
        return if (file.isFile && ApkIntegrity.matches(file, latest.size, latest.sha256)) file else null
    }


}

class UpdateAlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try { UpdateAlert.checkAndNotify(applicationContext) } catch (_: Exception) { }
        Result.success()
    }
}

/** Compatibility class for old queued work. It must never download APK bytes without a new user action. */
class UpdateDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = Result.success()
}
