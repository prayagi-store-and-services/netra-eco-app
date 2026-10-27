package com.prayagi.netraeco

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
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
 * Update alert: every 6 hours (network required) asks the repo's latest release whether a newer version exists and
 * posts one notification per new version. Tapping it opens the app, downloads the verified build and starts the install.
 */
object UpdateAlert {
    const val EXTRA_AUTO_UPDATE = "netra_auto_update"
    private const val ACTION_INTERNAL_UPDATE = "com.prayagi.netraeco.action.CONFIRMED_UPDATE"
    private const val EXTRA_TOKEN = "netra_update_token"
    private const val TOKEN_PREF = "pending_execution_token"
    internal var executionObserver: (() -> Unit)? = null
    internal fun notificationIntent(c: Context): Intent {
        val token = java.util.UUID.randomUUID().toString()
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TOKEN_PREF, token).apply()
        return Intent(c, MainActivity::class.java).setAction(ACTION_INTERNAL_UPDATE)
            .putExtra(EXTRA_AUTO_UPDATE, true).putExtra(EXTRA_TOKEN, token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    internal fun consumeAuthorizedNotification(c: Context, intent: Intent?): Boolean {
        if (intent == null || intent.action != ACTION_INTERNAL_UPDATE || intent.data != null || intent.clipData != null ||
            intent.getBooleanExtra(EXTRA_AUTO_UPDATE, false) != true) return false
        val offered = intent.getStringExtra(EXTRA_TOKEN) ?: return false
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        synchronized(this) {
            val expected = prefs.getString(TOKEN_PREF, null) ?: return false
            if (!java.security.MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), offered.toByteArray(Charsets.UTF_8))) return false
            if (!prefs.edit().remove(TOKEN_PREF).commit()) return false
        }
        intent.removeExtra(EXTRA_AUTO_UPDATE)
        intent.removeExtra(EXTRA_TOKEN)
        return true
    }
    private const val NOTIFICATION_ID = 7431
    private const val WORK_NAME = "netra_update_alert"
    private const val PREFS = "netra_update_alert"
    // Netra Ecosystem beeps (default notification sound); the other apps use a silent channel.
    private const val BEEP = true
    private val CHANNEL_ID = if (BEEP) "netra_app_update_beep" else "netra_app_update"

    /** Mobile data is allowed only for a file of at most this size. */
    const val MOBILE_LIMIT_BYTES = 100L * 1024 * 1024

    /**
     * Background download: on Wi-Fi (unmetered) always; on mobile data only when not roaming and the file is within the limit.
     * Never with Data Saver on or a low battery, never offline, and only once per version.
     */
    internal fun shouldPreDownload(online: Boolean, unmetered: Boolean, roaming: Boolean, dataSaverOn: Boolean, batteryLow: Boolean, alreadySaved: Boolean, sizeBytes: Long): Boolean {
        if (!online || dataSaverOn || batteryLow || alreadySaved) return false
        return unmetered || (!roaming && sizeBytes in 1..MOBILE_LIMIT_BYTES)
    }

    private fun online(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun unmetered(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun roaming(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
    }

    private fun dataSaverOn(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }

    private fun batteryLow(c: Context): Boolean {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val charging = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
        return level >= 0 && !charging && level * 100 / scale < 20
    }

    fun start(activity: Activity) {
        schedule(activity.applicationContext)
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
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Runs when the notification was tapped: check, download, verify, hand to the installer. */
    fun handle(activity: Activity, intent: Intent?) {
        if (!consumeAuthorizedNotification(activity.applicationContext, intent)) return
        executionObserver?.let { it(); return }
        val app = activity.applicationContext
        Toast.makeText(app, "Preparing the update...", Toast.LENGTH_SHORT).show()
        Thread {
            kotlinx.coroutines.runBlocking { try {
                val self = Net.selfApp(app)
                val installed = Net.installed(app, self.packageName)
                val latest = Net.fetchLatest(self)
                if (latest != null && statusForRelease(installed?.first, installed?.second, latest) == Status.UpdateAvailable) {
                    // A file saved in the background is used as it is: one tap, no waiting.
                    val file = if (DownloadCenter.hasFile(app, self, latest)) DownloadCenter.file(app, DownloadCenter.keyFor(self, latest))
                    else Net.download(app, self, latest)
                    try { Net.install(app, file) } catch (e: Exception) {
                        activity.runOnUiThread { Toast.makeText(app, e.message ?: "Update failed.", Toast.LENGTH_LONG).show() }
                    }
                } else {
                    activity.runOnUiThread { Toast.makeText(app, "No newer version found.", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                activity.runOnUiThread { Toast.makeText(app, e.message ?: "Update failed.", Toast.LENGTH_LONG).show() }
            } }
        }.start()
    }

    internal suspend fun checkAndNotify(c: Context) {
        val self = Net.selfApp(c)
        val latest = Net.fetchLatest(self) ?: return
        val installed = Net.installed(c, self.packageName)
        if (statusForRelease(installed?.first, installed?.second, latest) != Status.UpdateAvailable) return
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (shouldPreDownload(online(c), unmetered(c), roaming(c), dataSaverOn(c), batteryLow(c), DownloadCenter.hasFile(c, self, latest), latest.size)) {
            try { Net.download(c, self, latest); DownloadCenter.register(c, self, latest) } catch (_: Exception) { /* the tap path still downloads */ }
        }
        val ready = DownloadCenter.hasFile(c, self, latest)
        val state = latest.tag + (if (ready) ":ready" else "")
        if (prefs.getString("notified_tag", "") == state) return
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
        val launch = notificationIntent(c)
        val open = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update available")
            .setContentText("Version " + latest.versionName + (if (ready) " is downloaded. Tap to install." else " is ready. Tap to update."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        prefs.edit().putString("notified_tag", state).apply()
    }
}

class UpdateAlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try { UpdateAlert.checkAndNotify(applicationContext) } catch (_: Exception) { }
        Result.success()
    }
}
