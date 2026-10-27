package com.prayagi.netraeco

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h1000dp-xxhdpi", sdk = [34], application = Application::class)
class EcoSectionsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val catalog = """{"upcoming":[{"id":"future-one","name":"Future Alpha","type":"Planned","summary":"Not released"},{"id":"future-two","name":"Future Beta","type":"Planned","summary":"Not released"}]}"""
    private fun show(large: Boolean = true, dark: Boolean = false): () -> Int {
        var loads = 0
        val app = CatalogApp("fixture", "Current Alpha", "Utility", "Released fixture", "", "test.fixture", "", "")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 1.5f else 1f)) {
                androidx.compose.material3.MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                    EcoScreen(0, catalogLoader = { loads++; listOf(AppRow(app, null, null)) }, textLoader = { if (it == Net.CATALOG_URL) catalog else null })
                }
            }
        }
        rule.onNodeWithText("Search apps").assertExists()
        return { loads }
    }
    private fun shot(name: String) = rule.runOnIdle {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/outputs/eco-sections-$name.png").apply { parentFile.mkdirs() }.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    @Test fun tabsKeepSeparateSearchWithoutReloadingCatalog() {
        val loads = show()
        rule.onNodeWithText("Search apps").performTextInput("Current Alpha")
        rule.onNode(hasText("Current Alpha") and hasAnyAncestor(hasTestTag("current-list"))).assertExists()
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithText("Search apps").performTextInput("Future Beta")
        rule.onNode(hasText("Future Beta") and hasAnyAncestor(hasTestTag("future-list"))).assertExists()
        rule.onAllNodesWithText("Current Alpha").assertCountEquals(0)
        rule.onAllNodesWithText("Download").assertCountEquals(0)
        shot("future-large")
        rule.onNodeWithText("Current").performClick()
        rule.onNodeWithText("Search apps").assertTextContains("Current Alpha")
        rule.onNode(hasText("Current Alpha") and hasAnyAncestor(hasTestTag("current-list"))).assertExists()
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithText("Search apps").assertTextContains("Future Beta")
        assertEquals(1, loads())
    }
    @Test fun currentScrollPositionSurvivesFutureVisit() {
        show(large = false)
        rule.onNodeWithTag("current-list").performScrollToKey("l-fixture")
        rule.onNodeWithText("Current Alpha").assertIsDisplayed()
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithTag("future-list").assertExists()
        rule.onNodeWithText("Current").performClick()
        rule.onNodeWithText("Current Alpha").assertIsDisplayed()
    }
    @Test fun currentUpdateAndTickerLargeLight() {
        show()
        rule.onNodeWithText("Check for update").assertIsDisplayed()
        rule.onNodeWithText("Roadmap Unavailable right now.").assertIsDisplayed()
        shot("current-large-light")
    }
    @Test fun currentUpdateAndTickerLargeDark() {
        show(dark = true)
        rule.onNodeWithText("Check for update").assertIsDisplayed()
        shot("current-large-dark")
    }
}
