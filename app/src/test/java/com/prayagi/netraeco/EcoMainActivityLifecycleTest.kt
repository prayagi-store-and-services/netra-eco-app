package com.prayagi.netraeco
import android.app.Application
import android.content.Intent
import android.net.Uri
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class EcoMainActivityLifecycleTest {
 @After fun cleanup(){UpdateAlert.executionObserver=null}
 private fun link(code:String)=Intent(Intent.ACTION_VIEW,Uri.parse("https://prayagi-store-and-services.github.io/netra-eco/app/$code/")).putExtra(UpdateAlert.EXTRA_AUTO_UPDATE,true)
 @Test fun actualActivityColdWarmRecreationAndBackNeverExecuteUpdate(){
  androidx.work.WorkManager.initialize(org.robolectric.RuntimeEnvironment.getApplication(), androidx.work.Configuration.Builder().build())
  var executions=0;UpdateAlert.executionObserver={executions++}
  val c=Robolectric.buildActivity(MainActivity::class.java,link("kbc")).setup()
  assertEquals("kbc",c.get().requestedAppForTest)
  c.newIntent(link("player"))
  assertEquals("netra-player",c.get().requestedAppForTest)
  assertEquals("player",c.get().intent.data?.lastPathSegment)
  c.recreate()
  assertEquals("netra-player",c.get().requestedAppForTest)
  org.robolectric.shadows.ShadowLooper.idleMainLooper()
  c.get().onBackPressedDispatcher.onBackPressed()
  org.robolectric.shadows.ShadowLooper.idleMainLooper()
  assertNull(c.get().requestedAppForTest)
  c.recreate()
  assertNull(c.get().requestedAppForTest)
  assertEquals(0,executions)
  c.pause().stop().destroy()
 }
}
