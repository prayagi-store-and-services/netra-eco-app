package com.prayagi.netraeco

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Android12+ eligibility is conditional, never a guarantee that no confirmation appears. */
object ConditionalUpdatePolicy {
    fun minimumTarget(sdk: Int): Int? = when {
        sdk < 31 -> null
        sdk <= 32 -> 29
        sdk == 33 -> 30
        sdk == 34 -> 31
        sdk == 35 -> 33
        sdk == 36 -> 34
        else -> null // Future OS requirements must be reviewed, not guessed.
    }
    fun eligible(sdk: Int, targetSdk: Int, installed: Boolean, ownInstaller: Boolean,
                 ownershipAllowed: Boolean, installAllowed: Boolean, enabled: Boolean): Boolean =
        enabled && installed && ownInstaller && ownershipAllowed && installAllowed &&
            minimumTarget(sdk)?.let { targetSdk >= it } == true
}

object ConditionalUpdates {
    private const val PREFS = "conditional_updates"
    const val NOTICE = "Optional automatic updates apply only to eligible Netra apps installed by Eco. Android may still ask for a confirmation tap. No root or accessibility access. Background checks can be delayed."
    fun enabled(c: Context) = c.getSharedPreferences(PREFS,0).getBoolean("enabled",false)
    // UI must obtain explicit opt-in; default stays off, no enabling by notification or external intent.
    fun setEnabled(c: Context, enabled: Boolean) { c.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",enabled).apply() }

    fun eligible(c: Context, row: AppRow): Boolean = runCatching {
        val pm=c.packageManager
        val info=pm.getPackageInfo(row.app.packageName,0)
        val installer=if(Build.VERSION.SDK_INT>=30) pm.getInstallSourceInfo(row.app.packageName).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(row.app.packageName)
        val owner=if(Build.VERSION.SDK_INT>=34) pm.getInstallSourceInfo(row.app.packageName).updateOwnerPackageName else null
        ConditionalUpdatePolicy.eligible(Build.VERSION.SDK_INT, info.applicationInfo?.targetSdkVersion ?: 0,
            row.installed!=null, installer==c.packageName, owner==null || owner==c.packageName,
            pm.canRequestPackageInstalls(), enabled(c)) && storeStatus(row)==Status.UpdateAvailable
    }.getOrDefault(false)

    /** Same archive/hash/package/version/signer gate as manual installs, immediately before staging. */
    suspend fun stage(c: Context, row: AppRow, file: File, requestNoAction: Boolean): Int = withContext(Dispatchers.IO) {
        val release=requireNotNull(row.latest)
        check(row.deviceStateKnown && row.installed!=null && storeStatus(row)==Status.UpdateAvailable)
        check(c.packageManager.canRequestPackageInstalls()) { "Allow installs from Netra Ecosystem, then try again." }
        val archive=InstallerGate.archive(c,file) ?: error("Installer archive could not be read.")
        HumanSafetyHold.requireInstaller(file,archive.packageName,archive.versionName,PackageInfoCompat.getLongVersionCode(archive))
        InstallerGate.requireValid(c,file,archive)
        check(archive.packageName==row.app.packageName && archive.versionName==release.versionName)
        check(InstallerGate.bytesMatch(file,release))
        val device=InstalledApps.read(c,row.app.packageName)
        check(device.known && device.version!=null && statusForRelease(device.version.first,device.version.second,release)==Status.UpdateAvailable)
        val archiveTarget=archive.applicationInfo?.targetSdkVersion ?: 0
        val silentEligible=eligible(c,row) && ConditionalUpdatePolicy.minimumTarget(Build.VERSION.SDK_INT)?.let { archiveTarget>=it }==true
        val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(row.app.packageName)
        params.setSize(file.length())
        if(Build.VERSION.SDK_INT>=31) params.setRequireUserAction(
            if(requestNoAction && silentEligible) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
            else PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        val installer=c.packageManager.packageInstaller
        val id=installer.createSession(params)
        val prefs=c.getSharedPreferences(PREFS,0)
        // Durable session correlation; callback never trusts arbitrary package/version extras.
        check(prefs.edit().putString("session_$id",row.app.packageName+"|"+release.versionName+"|"+release.versionCode).commit())
        try {
            installer.openSession(id).use { session ->
                file.inputStream().use { input -> session.openWrite("base.apk",0,file.length()).use { output -> input.copyTo(output);session.fsync(output) } }
                val callback=Intent(c,UpdateSessionReceiver::class.java).setAction("com.prayagi.netraeco.UPDATE_RESULT.$id").putExtra("owned_session",id)
                val flags=PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31) PendingIntent.FLAG_MUTABLE else 0
                val pending=PendingIntent.getBroadcast(c,id,callback,flags)
                session.commit(pending.intentSender)
            }
            id
        } catch(e:Exception) { installer.abandonSession(id);prefs.edit().remove("session_$id").apply();throw e }
    }

    internal fun session(c:Context,id:Int): List<String>? = c.getSharedPreferences(PREFS,0).getString("session_$id",null)?.split("|")?.takeIf { it.size==3 }
    internal fun clear(c:Context,id:Int) { c.getSharedPreferences(PREFS,0).edit().remove("session_$id").apply() }
    internal fun notify(c:Context,id:Int,title:String,text:String,action:Intent?=null) {
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val nm=c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("netra_verified_updates","Verified Netra updates",NotificationManager.IMPORTANCE_DEFAULT))
        val b=NotificationCompat.Builder(c,"netra_verified_updates").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(title).setContentText(text).setAutoCancel(true)
        if(action!=null)b.setContentIntent(PendingIntent.getActivity(c,id,action,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        nm.notify(8500+id,b.build())
    }
}

/** Not exported. Only the explicit PendingIntent held by Android can deliver a session result. */
class UpdateSessionReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,intent:Intent) {
        val id=intent.getIntExtra("owned_session",-1)
        if(id<0 || intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1)!=id || intent.action!="com.prayagi.netraeco.UPDATE_RESULT.$id")return
        val expected=ConditionalUpdates.session(c,id) ?: return
        when(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm=intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if(confirm!=null) ConditionalUpdates.notify(c,id,"Confirm update","Android needs your approval to update this Netra app.",confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                // Keep correlation while the user decides. No automatic success or auto-click.
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val actual=InstalledApps.read(c,expected[0])
                val code=expected[2].toLongOrNull() ?: 0L
                val matched=actual.known && actual.version?.let { it.second==expected[1] && (code<=0 || it.first==code) }==true
                ConditionalUpdates.notify(c,id,if(matched)"Update installed"else"Check update status",if(matched)"Android confirmed the installed version."else"Install returned success, but the expected version could not be confirmed.")
                ConditionalUpdates.clear(c,id)
            }
            else -> { ConditionalUpdates.notify(c,id,"Update not installed","Android did not complete the update. Open Eco to retry.");ConditionalUpdates.clear(c,id) }
        }
    }
}
