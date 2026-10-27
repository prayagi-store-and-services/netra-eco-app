package com.prayagi.netraeco

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EcoActionScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun installerButtonReturnKeepsBusyBlocksDuplicateAndShowsResult() {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        rule.setContent {
            val actions = remember { EcoInstallActions() }
            val scope = rememberCoroutineScope()
            var future by remember { mutableStateOf(false) }
            Column {
                Button(onClick = { future = !future }) { Text(if (future) "Current" else "Future") }
                if (!future) {
                    Button(enabled = !actions.state("fixture.apk").busy, onClick = {
                        actions.start(scope, "fixture.apk") { calls++; gate.await(); error("Fixture result") }
                    }) { Text(if (actions.state("fixture.apk").busy) "Checking installer..." else "Install fixture") }
                    actions.state("fixture.apk").error?.let { Text(it) }
                }
            }
        }
        rule.onNodeWithText("Install fixture").performClick()
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithText("Current").performClick()
        rule.onNodeWithText("Checking installer...").assertIsNotEnabled()
        assertEquals(1, calls)
        rule.onNodeWithText("Future").performClick()
        rule.runOnIdle { gate.complete(Unit) }
        rule.onNodeWithText("Current").performClick()
        rule.onNodeWithText("Fixture result").assertIsDisplayed()
        rule.onNodeWithText("Install fixture").assertIsEnabled()
        assertEquals(1, calls)
    }
    @Test fun futureCategoryRestoresThroughSavedStateRecreation() {
        val restorer = StateRestorationTester(rule)
        val catalog = """{"upcoming":[{"id":"a","name":"Future Alpha","type":"Utility"},{"id":"b","name":"Future Beta","type":"Music"}]}"""
        restorer.setContent {
            androidx.compose.material3.MaterialTheme {
                EcoScreen(0, catalogLoader = { emptyList() }, textLoader = { if (it == Net.CATALOG_URL) catalog else null })
            }
        }
        rule.onNodeWithText("Future").performClick()
        rule.onNodeWithText("Media").performClick()
        restorer.emulateSavedInstanceStateRestore()
        rule.waitUntil(10000) { rule.onAllNodesWithText("Search apps").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithTag("future-list").performScrollToKey("cat-head")
        rule.onNodeWithText("Media: 1 app. An app can be in more than one group, based on what it does.").assertExists()
        rule.onNodeWithTag("future-list").performScrollToKey("up-b")
        rule.onNodeWithText("Future Beta").assertIsDisplayed()
        rule.onAllNodesWithText("Future Alpha").assertCountEquals(0)
    }
}
