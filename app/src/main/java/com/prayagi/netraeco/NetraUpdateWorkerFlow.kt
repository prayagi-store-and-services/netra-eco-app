package com.prayagi.netraeco

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Central six-hour worker; no duplicate updater inside each app. */
object NetraUpdateWorkerFlow {
    internal fun rows(c:Context):List<AppRow> {
        val catalog=Net.fetchText(Net.CATALOG_URL) ?: return emptyList()
        return Net.parseCatalog(catalog).filter { it.packageName!=c.packageName }.mapNotNull { app ->
            val device=InstalledApps.read(c,app.packageName)
            if(!device.known || device.version==null)return@mapNotNull null
            AppRow(app,device.version,Net.fetchLatest(app,c),true)
        }.let(::storeUpdateRows)
    }
    suspend fun check(c:Context) {
        if(android.os.Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
        for(row in rows(c)) {
            val release=row.latest ?: continue
            val key="offer_"+row.app.id
            val prefs=c.getSharedPreferences("netra_update_offers",0)
            if(prefs.getString(key,null)==release.tag)continue
            val cm=c.getSystemService(ConnectivityManager::class.java)
            val caps=cm.getNetworkCapabilities(cm.activeNetwork)
            val battery=c.registerReceiver(null,android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level=battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1) ?: -1
            val scale=battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,100) ?: 100
            val batteryOk=level>=0 && scale>0 && level*100/scale>=20
            val canDownload=caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)==true && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) && cm.restrictBackgroundStatus!=ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED && batteryOk
            if(ConditionalUpdates.eligible(c,row) && canDownload) {
                try {
                    val file=if(DownloadCenter.hasFile(c,row.app,release))DownloadCenter.file(c,DownloadCenter.keyFor(row.app,release))else Net.download(c,row.app,release)
                    ConditionalUpdates.stage(c,row,file,true)
                    prefs.edit().putString(key,release.tag).apply()
                    continue
                } catch(_:Exception) { /* explicit notification remains the safe fallback */ }
            }
            // Explicit immutable activity PI, no exported update command or arbitrary URL.
            val target=Intent(c,VerifiedUpdateActivity::class.java).putExtra("netra_app_id",row.app.id)
            ConditionalUpdates.notify(c,row.app.id.hashCode().and(0x3fff),row.app.name+" update",release.versionName+" is available. Tap to verify and update.",target)
            prefs.edit().putString(key,release.tag).apply()
        }
    }
}

/** Only an explicit notification PendingIntent can open this non-exported activity. */
class VerifiedUpdateActivity:androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val label=TextView(this).apply { text="Checking the latest verified release...";setPadding(32,48,32,32) }
        setContentView(label)
        val id=intent.getStringExtra("netra_app_id")
        lifecycleScope.launch {
            try {
                val row=withContext(Dispatchers.IO){NetraUpdateWorkerFlow.rows(applicationContext).firstOrNull { it.app.id==id }} ?: error("No verified update is available.")
                val release=requireNotNull(row.latest)
                val file=withContext(Dispatchers.IO){if(DownloadCenter.hasFile(applicationContext,row.app,release))DownloadCenter.file(applicationContext,DownloadCenter.keyFor(row.app,release))else Net.download(applicationContext,row.app,release)}
                // Foreground explicit tap still uses the same verified system confirmation route.
                Net.install(this@VerifiedUpdateActivity,file)
                label.text="Follow Android's install confirmation. Downloaded does not mean installed."
            } catch(e:Exception) {label.text=e.message ?: "Could not prepare the update."}
        }
    }
}
