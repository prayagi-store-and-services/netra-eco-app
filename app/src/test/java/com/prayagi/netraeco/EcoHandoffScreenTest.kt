package com.prayagi.netraeco
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi",sdk=[34],application=Application::class)
class EcoHandoffScreenTest {
 @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
 @Test fun onlyRequestedAppCardIsDisplayed() {
  val app=CatalogApp("kbc","Netra KBC","Quiz","Fixture, not live metadata","prayagi-store-and-services/KBC","com.aistudio.reasoningquiz.tark","","")
  val other=app.copy(id="netra-player",name="Netra Player")
  rule.setContent { MaterialTheme { EcoScreen(0,catalogLoader={listOf(AppRow(app,null,null),AppRow(other,null,null))},textLoader={null},requestedAppId="kbc") } }
  rule.waitUntil(10000){rule.onAllNodesWithText("Netra KBC").fetchSemanticsNodes().isNotEmpty()}
  rule.onNodeWithText("Netra KBC").assertIsDisplayed()
  rule.onAllNodesWithText("Netra Player").assertCountEquals(0)
  rule.onNodeWithText("Back to all apps").assertIsDisplayed()
  rule.runOnIdle {
   val view=rule.activity.window.decorView
   val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
   view.draw(android.graphics.Canvas(bitmap))
   java.io.File("build/outputs/eco-handoff-fixture.png").apply{parentFile.mkdirs()}.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
   bitmap.recycle()
  }
 }
 @Test fun failedLoadDoesNotDisplayStaleCard() {
  rule.setContent { MaterialTheme { EcoScreen(0,catalogLoader={null},textLoader={null},requestedAppId="kbc") } }
  rule.waitUntil(10000){rule.onAllNodesWithText("Unavailable: could not refresh app details. No download started.").fetchSemanticsNodes().isNotEmpty()}
  rule.onNodeWithText("Check again").assertExists()
 }
}
