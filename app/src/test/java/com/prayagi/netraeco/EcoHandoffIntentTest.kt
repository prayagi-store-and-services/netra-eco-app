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

 @Test fun tokenRemovalCommitFailureRejectsExecution() {
  val app=org.robolectric.RuntimeEnvironment.getApplication()
  val valid=UpdateAlert.notificationIntent(app)
  val wrapped=object:android.content.ContextWrapper(app) {
   override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
    val real=super.getSharedPreferences(name,mode)
    return object:android.content.SharedPreferences by real {
     override fun edit():android.content.SharedPreferences.Editor {
      val edit=real.edit()
      return object:android.content.SharedPreferences.Editor by edit { override fun remove(key:String):android.content.SharedPreferences.Editor { edit.remove(key); return this }; override fun commit()=false }
     }
    }
   }
  }
  assertFalse(UpdateAlert.consumeAuthorizedNotification(wrapped,valid))
 }
 @Test fun clipWrongActionAndMissingTokenAreRejected() {
  val app=org.robolectric.RuntimeEnvironment.getApplication()
  val valid=UpdateAlert.notificationIntent(app)
  val badClip=Intent(valid).apply { clipData=android.content.ClipData.newPlainText("test","untrusted") }
  assertFalse(UpdateAlert.consumeAuthorizedNotification(app,badClip))
  assertFalse(UpdateAlert.consumeAuthorizedNotification(app,Intent(valid).setAction(Intent.ACTION_VIEW)))
  assertFalse(UpdateAlert.consumeAuthorizedNotification(app,Intent(valid).apply{removeExtra("netra_update_token")}))
  assertTrue(UpdateAlert.consumeAuthorizedNotification(app,valid))
 }
}
