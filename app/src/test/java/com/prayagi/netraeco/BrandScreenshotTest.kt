package com.prayagi.netraeco

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.material3.MaterialTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi", sdk = [34], application = android.app.Application::class)
class BrandScreenshotTest {
    @get:Rule val rule = createComposeRule()
    @Test fun ecosystemNameFitsProductionHeader() {
        rule.setContent { MaterialTheme { EcoHeader(NameGate.PUBLIC_NAME, "1.2.26", java.util.Date(1792067400000L)) } }
        rule.onNodeWithText("Netra Ecosystem").assertIsDisplayed()
        rule.onRoot().captureRoboImage(filePath = "build/outputs/ecosystem-header.png")
    }
}
