package com.prayagi.netraeco

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import android.content.Context
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
    private val catalog = """{"upcoming":[{"id":"future-one","name":"Future Alpha","type":"Planned","summary":"Not released"},{"id":"future-two","name":"Future Beta","type":"Music","summary":"Not released"}]}"""
    private fun show(large: Boolean = true, dark: Boolean = false, short: Boolean = false, roadmap: String? = null,
        checker: suspend (Context) -> Pair<String, LatestRelease?> = { "Fixture checked" to null }): () -> Int {
        var loads = 0
        val app = CatalogApp("fixture", "Current Alpha", "Utility", "Released fixture", "", "test.fixture", "", "")
        if (roadmap != null) rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 1.5f else 1f)) {
                androidx.compose.material3.MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                    Box(if (short) Modifier.height(480.dp) else Modifier) {
                    EcoScreen(0, catalogLoader = { loads++; listOf(AppRow(app, null, null)) }, textLoader = { if (it == Net.CATALOG_URL) catalog else roadmap }, selfChecker = checker)
                    }
                }
            }
        }
        if (roadmap != null) rule.mainClock.advanceTimeBy(1000)
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
        rule.onNode(hasText("Current Alpha") and !hasSetTextAction() and hasAnyAncestor(hasTestTag("current-list"))).assertExists()
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithText("Search apps").performTextInput("Future Beta")
        rule.onNode(hasText("Future Beta") and !hasSetTextAction() and hasAnyAncestor(hasTestTag("future-list"))).assertExists()
        rule.onAllNodesWithText("Current Alpha").assertCountEquals(0)
        rule.onAllNodesWithText("Download").assertCountEquals(0)
        shot("future-large")
        rule.onNodeWithText("Current").performClick()
        rule.onNodeWithTag("current-list").performScrollToKey("section-controls")
        rule.onNodeWithText("Search apps").assertTextContains("Current Alpha")
        rule.onNode(hasText("Current Alpha") and !hasSetTextAction() and hasAnyAncestor(hasTestTag("current-list"))).assertExists()
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithTag("future-list").performScrollToKey("section-controls")
        rule.onNodeWithText("Search apps").assertTextContains("Future Beta")
        assertEquals(1, loads())
    }
    @Test fun releaseNotificationSwitchesFutureToCurrent() {
        val request = androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent {
            EcoScreen(0, catalogLoader = { emptyList() }, textLoader = { if (it == Net.CATALOG_URL) catalog else null }, currentRequestKey = request.intValue)
        }
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithTag("future-list").assertExists()
        rule.runOnIdle { request.intValue++ }
        rule.onNodeWithTag("current-list").assertExists()
        rule.onAllNodesWithTag("future-list").assertCountEquals(0)
    }
    @Test fun namedUpdateIsVisibleWithBothVersions() {
        val app = CatalogApp("fixture", "Netra Player", "Media", "Fixture", "", "test.fixture", "", "")
        val release = LatestRelease("v1.0.1", "1.0.1", 2L, "", "", "a".repeat(64), 1L)
        rule.setContent {
            EcoScreen(0, catalogLoader = { listOf(AppRow(app, 1L to "1.0.0", release)) }, textLoader = { null })
        }
        rule.onNodeWithTag("current-list").performScrollToKey("downloads")
        rule.onNodeWithText("Updates available").assertIsDisplayed()
        rule.onNodeWithText("Installed: 1.0.0 | Published: 1.0.1").assertIsDisplayed()
        rule.onNodeWithText("Update Netra Player").assertIsDisplayed()
        shot("named-updates")
    }
    @Test fun futureKnownTargetLargeFontMixedOrder() {
        val now = System.currentTimeMillis()
        val near = java.time.Instant.ofEpochMilli(now + 86_400_000L).toString()
        val far = java.time.Instant.ofEpochMilli(now + 172_800_000L).toString()
        val past = java.time.Instant.ofEpochMilli(now - 86_400_000L).toString()
        val data = """{"projects":[],"upcoming":[{"id":"undated","name":"Undated"},{"id":"far","name":"Far target","eta":"$far"},{"id":"past","name":"Passed target","eta":"$past"},{"id":"near","name":"Nearest target","eta":"$near"}]}"""
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                androidx.compose.material3.MaterialTheme { EcoScreen(0, catalogLoader = { emptyList() }, textLoader = { if (it == Net.CATALOG_URL) data else null }) }
            }
        }
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithTag("future-list").performScrollToKey("up-near")
        rule.onNodeWithText("Nearest target").assertIsDisplayed()
        rule.onNodeWithText(Upcoming.targetLabel(java.time.Instant.parse(near).toEpochMilli())).assertIsDisplayed()
        shot("future-known-target-large")
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
    @Test fun noManualUpdateCheckInEitherSection() {
        show()
        rule.onAllNodesWithText("Check for update").assertCountEquals(0)
        rule.onNodeWithText("Future").performClick()
        rule.onAllNodesWithText("Check for update").assertCountEquals(0)
    }
    @Test fun futureScrollPositionSurvivesCurrentVisit() {
        show(large = false)
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithTag("future-list").performScrollToKey("up-future-two")
        rule.onNodeWithText("Future Beta").assertIsDisplayed()
        rule.onNodeWithText("Current").performClick()
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithText("Future Beta").assertIsDisplayed()
    }
    @Test fun futureCategorySurvivesCurrentVisit() {
        show(large = false)
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithText("Media", substring = false).performClick()
        rule.onNodeWithText("Future Beta").assertExists()
        rule.onAllNodesWithText("Future Alpha").assertCountEquals(0)
        rule.onNodeWithText("Current").performClick()
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithText("Future Beta").assertExists()
        rule.onAllNodesWithText("Future Alpha").assertCountEquals(0)
    }
    @Test fun activeDownloadFinishesAcrossFutureVisit() {
        DownloadCenter.items.value = emptyMap()
        show(large = false)
        val bytes = ByteArray(128) { it.toByte() }
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/fixture.apk") { x ->
            started.countDown()
            release.await(10, java.util.concurrent.TimeUnit.SECONDS)
            x.sendResponseHeaders(200, bytes.size.toLong())
            x.responseBody.use { it.write(bytes) }
        }
        server.start()
        val app = CatalogApp("download-fixture", "Download fixture", "", "", "", "fixture.test", "", "")
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val r = LatestRelease("v1.0.0", "1.0.0", 1, "", "http://127.0.0.1:" + server.address.port + "/fixture.apk", sha, bytes.size.toLong())
        val key = DownloadCenter.keyFor(app, r)
        try {
            rule.runOnIdle { DownloadCenter.start(rule.activity, app, r) }
            assertTrue(started.await(10, java.util.concurrent.TimeUnit.SECONDS))
            rule.onNodeWithText("Future").performClick()
            release.countDown()
            rule.waitUntil(15000) { DownloadCenter.items.value[key]?.state == DownloadCenter.DONE }
            rule.onNodeWithText("Current").performClick()
            assertEquals(DownloadCenter.DONE, DownloadCenter.items.value[key]?.state)
            assertTrue(DownloadCenter.file(rule.activity, key).readBytes().contentEquals(bytes))
        } finally { release.countDown(); server.stop(0); DownloadCenter.delete(rule.activity, key) }
    }
    @Test fun realTickerAndShortViewportStillReachLastCurrentItem() {
        val roadmap = """{"apps":[{"id":"eco","name":"Netra Ecosystem"}],"items":[{"title":"Long planned Current and Future accessibility release","status":"In progress","eta":"2026-11-01T20:00:00+05:30","apps":["eco"]}]}"""
        show(short = true, roadmap = roadmap)
        rule.onNodeWithTag("current-list").performScrollToKey("perms")
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Permissions").assertIsDisplayed()
        shot("short-large-real-ticker-bottom")
        rule.onNodeWithText("Future").performClick()
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Search apps").performTextInput("Future")
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithTag("future-list").performScrollToKey("up-future-two")
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Future Beta").assertIsDisplayed()
        shot("short-large-search-real-ticker")
        rule.onNodeWithTag("future-list").performScrollToKey("section-controls")
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Search apps").assertIsDisplayed()
        rule.onNodeWithText("Current").performClick()
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Permissions").assertIsDisplayed()
        rule.onNodeWithTag("current-list").performScrollToKey("section-controls")
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Search apps").assertIsDisplayed()
    }
    @Test fun currentUpdateAndTickerLargeLight() {
        show()
        rule.onAllNodesWithText("Check for update").assertCountEquals(0)
        rule.onNodeWithText("Roadmap Unavailable right now.").assertIsDisplayed()
        shot("current-large-light")
    }
    @Test fun currentUpdateAndTickerLargeDark() {
        show(dark = true)
        rule.onAllNodesWithText("Check for update").assertCountEquals(0)
        shot("current-large-dark")
    }
}
