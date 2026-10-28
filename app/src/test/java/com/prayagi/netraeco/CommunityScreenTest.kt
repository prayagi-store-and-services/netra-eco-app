package com.prayagi.netraeco

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h1000dp-xxhdpi", sdk = [34], application = Application::class)
class CommunityScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun shot(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/outputs/community-$name.png").apply { parentFile.mkdirs() }.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    @Test fun socialLinksOnlyOpenOnExplicitTapAndWrapAtLargeFont() {
        val opened = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                androidx.compose.material3.MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) { CommunityCard { opened += it } }
                }
            }
        }
        assertTrue(opened.isEmpty())
        shot("social-large")
        CommunityLinks.social.forEach { link ->
            rule.onNodeWithText(link.icon + "  " + link.label).performScrollTo().performClick()
            assertEquals(link.url, opened.last())
        }
        rule.onNodeWithText("Rate this app or read reviews").performScrollTo().performClick()
        assertEquals(CommunityLinks.reviews("netra-eco"), opened.last())
        rule.onNodeWithText("Private complaint or feedback").performScrollTo().performClick()
        assertEquals(CommunityLinks.contact("netra-eco"), opened.last())
        shot("feedback-large")
        assertEquals(7, opened.size)
    }
    @Test fun unavailableBrowserShowsHonestFailure() {
        rule.setContent { androidx.compose.material3.MaterialTheme { AppCommunityLinks("netra-player") { error("No browser") } } }
        rule.onNodeWithText("Open this app's website").performClick()
        rule.onNodeWithText("Could not open this link. Check that a browser is available.").assertIsDisplayed()
        shot("browser-unavailable")
    }
    @Test fun productionCurrentTabContainsCommunityCard() {
        rule.setContent {
            androidx.compose.material3.MaterialTheme {
                EcoScreen(0, catalogLoader = { emptyList() }, textLoader = { null })
            }
        }
        rule.onNodeWithTag("current-list").performScrollToKey("community")
        rule.onNodeWithText("Connect with Netra Ecosystem").assertIsDisplayed()
        shot("current-integration")
    }
    @Test fun unknownAppShowsNoInventedLinks() {
        rule.setContent { AppCommunityLinks("unknown") { error("must not open") } }
        rule.onAllNodesWithText("Open this app's website").assertCountEquals(0)
    }
}
