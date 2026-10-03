package com.prayagi.netraeco

import org.junit.Assert.assertTrue
import org.junit.Test

class SelfUpdateTest {
    @Test fun latestMessage() = assertTrue(selfUpdateMessage("1.0.2", Status.UpToDate, "1.0.2").contains("latest version"))
    @Test fun updateMessageNamesNewVersion() = assertTrue(selfUpdateMessage("1.0.1", Status.UpdateAvailable, "1.0.2").contains("1.0.2"))
    @Test fun failureSaysUnavailable() = assertTrue(selfUpdateMessage("1.0.1", Status.Unavailable, null).startsWith("Unavailable"))
}
