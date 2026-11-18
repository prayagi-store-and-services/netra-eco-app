package com.prayagi.netraeco

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h1000dp-xxhdpi",sdk=[34],application=Application::class)
class StorePreviewScreenTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val app=CatalogApp("kbc","Netra KBC","Game","17-question reasoning quiz","prayagi-store-and-services/KBC","com.aistudio.reasoningquiz.tark","","")
    private val release=LatestRelease("v1.1.43","1.1.43",43,"The update card appears only for a newer version.","https://github.com/prayagi-store-and-services/KBC/releases/download/v1.1.43/app-release.apk","a".repeat(64),17848443)
    private fun shot(name:String)=rule.runOnIdle {
        val v=rule.activity.window.decorView
        val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(b))
        java.io.File("build/outputs/eco-sections-store-$name.png").apply{parentFile.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        b.recycle()
    }
    @Test fun browseOpensDetailAndKeepsHonestReviewState() {
        rule.setContent { MaterialTheme { EcoScreen(0,catalogLoader={listOf(AppRow(app,null,release))},textLoader={null}) } }
        rule.waitUntil(10000){rule.onAllNodesWithText("Search apps").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("current-list").performScrollToKey("l-kbc")
        shot("browse")
        rule.onNodeWithText("App details").performClick()
        rule.onNodeWithText("Back to all apps").assertIsDisplayed()
        shot("detail")
        rule.onNodeWithTag("handoff-app").performScrollToNode(hasText("No verified ratings available. Reviews are not collected here yet."))
        rule.onNodeWithText("No verified ratings available. Reviews are not collected here yet.").assertExists()
    }
    @Test fun guardianCatalogCardAndDetail() {
        val guardian=CatalogApp(GUARDIAN_ID,"Netra Battery Guardian","Battery & Device","Local battery and temperature monitoring",GUARDIAN_REPO,GUARDIAN_PACKAGE,"","https://prayagi-store-and-services.github.io/netra-eco/app/guardian/")
        val latest=LatestRelease("v1.0.0","1.0.0",1,"Physical-device tests not yet verified.","https://github.com/$GUARDIAN_REPO/releases/download/v1.0.0/app-release.apk","f115e902aa9e191c4367519f274c26bf6adaf0692f12c57493af151f1e6f467f",13300013,minSdk=24)
        rule.setContent { MaterialTheme { EcoScreen(0,catalogLoader={listOf(AppRow(guardian,null,latest))},textLoader={null}) } }
        rule.waitUntil(10000){rule.onAllNodesWithText("Search apps").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("current-list").performScrollToKey("l-$GUARDIAN_ID")
        rule.onNodeWithText("Netra Battery Guardian").assertExists()
        shot("guardian-browse")
        rule.onNodeWithText("App details").performClick()
        rule.onNodeWithText("Back to all apps").assertIsDisplayed()
        shot("guardian-detail")
    }
}
