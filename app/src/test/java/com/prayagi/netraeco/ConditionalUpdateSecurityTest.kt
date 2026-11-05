package com.prayagi.netraeco

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ConditionalUpdateSecurityTest {
 private val c get()=ApplicationProvider.getApplicationContext<android.content.Context>()
 @Test fun automaticUpdatesStartOff() {
  c.getSharedPreferences("conditional_updates",0).edit().clear().commit()
  assertFalse(ConditionalUpdates.enabled(c))
 }
 @Test fun unknownCallbackCannotClearOwnedSession() {
  val prefs=c.getSharedPreferences("conditional_updates",0)
  prefs.edit().putString("session_22","com.netracharge.app|0.2.0|3").commit()
  val forged=Intent("other.action").putExtra("owned_session",22).putExtra(PackageInstaller.EXTRA_SESSION_ID,22).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_SUCCESS)
  UpdateSessionReceiver().onReceive(c,forged)
  assertNotNull(ConditionalUpdates.session(c,22))
  val wrongId=Intent("com.prayagi.netraeco.UPDATE_RESULT.22").putExtra("owned_session",22).putExtra(PackageInstaller.EXTRA_SESSION_ID,23)
  UpdateSessionReceiver().onReceive(c,wrongId)
  assertNotNull(ConditionalUpdates.session(c,22))
 }
 @Test fun noNewInstallThroughAutomaticEngine() {
  val app=CatalogApp("netra-charge","Charge","Utility","","prayagi-store-and-services/netra-charge","com.netracharge.app","","")
  val release=LatestRelease("v0.2.0","0.2.0",3,"","https://github.com/prayagi-store-and-services/netra-charge/releases/download/v0.2.0/netra-charge-v0.2.0.apk","a".repeat(64),1)
  var rejected=false
  try { runBlocking {ConditionalUpdates.stage(c,AppRow(app,null,release),File(c.cacheDir,"untrusted.apk"),true)} } catch(_:Exception){rejected=true}
  assertTrue(rejected)
  assertTrue(c.packageManager.packageInstaller.mySessions.isEmpty())
 }
 @Test fun installComponentsCannotBeInvokedByOtherApps() {
  val manifest=File("src/main/AndroidManifest.xml").readText()
  assertTrue(manifest.contains("android:name=\".UpdateSessionReceiver\" android:exported=\"false\""))
  assertTrue(manifest.contains("android:name=\".VerifiedUpdateActivity\" android:exported=\"false\""))
  assertFalse(manifest.contains("QUERY_ALL_PACKAGES"))
 }
}
