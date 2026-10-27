package com.prayagi.netraeco
import android.app.Activity
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
class EcoHandoffIntentTest {
 @After fun cleanup(){ UpdateAlert.executionObserver=null }
 @Test fun coldAndWarmViewIntentsNeverEnterSelfUpdateExecution(){
  val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
  var executions=0
  UpdateAlert.executionObserver={executions++}
  for(url in listOf("https://prayagi-store-and-services.github.io/netra-eco/app/kbc/","https://evil.invalid/bad","")) {
   val view=Intent(Intent.ACTION_VIEW).putExtra(UpdateAlert.EXTRA_AUTO_UPDATE,true)
   if(url.isNotEmpty())view.data=Uri.parse(url)
   activity.intent=view
   UpdateAlert.handle(activity,activity.intent) // cold-start boundary called by start()
   UpdateAlert.handle(activity,view) // warm boundary called by onNewIntent()
  }
  assertEquals(0,executions) // observer precedes fetch/download/install and replaces whole execution
 }
 @Test fun forgedAndReplayedInternalIntentsCannotExecute(){
  val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
  var executions=0;UpdateAlert.executionObserver={executions++}
  UpdateAlert.handle(activity,Intent("com.prayagi.netraeco.action.CONFIRMED_UPDATE").putExtra(UpdateAlert.EXTRA_AUTO_UPDATE,true).putExtra("netra_update_token","forged"))
  val valid=UpdateAlert.notificationIntent(activity)
  val withUri=Intent(valid).setData(Uri.parse("https://prayagi-store-and-services.github.io/netra-eco/app/kbc/"))
  UpdateAlert.handle(activity,withUri)
  assertEquals(0,executions)
  UpdateAlert.handle(activity,valid);assertEquals(1,executions)
  UpdateAlert.handle(activity,valid);assertEquals(1,executions)
 }
}
