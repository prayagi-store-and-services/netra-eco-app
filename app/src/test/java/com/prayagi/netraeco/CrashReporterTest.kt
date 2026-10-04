package com.prayagi.netraeco

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {
    @Test fun traceKeepsClassNamesButDropsExceptionMessage() {
        val s = CrashReporter.sanitize(IllegalStateException("secret-user-text 555-1234"))
        assertTrue(s.startsWith("java.lang.IllegalStateException"))
        assertFalse(s.contains("secret-user-text"))
    }
    @Test fun acceptedNeedsRealSuccessAnswer() {
        assertTrue(CrashReporter.accepted("{\"success\":\"true\",\"message\":\"ok\"}"))
        assertTrue(CrashReporter.accepted("{\"success\":true}"))
        assertFalse(CrashReporter.accepted("{\"success\":\"false\",\"message\":\"Needs Activation\"}"))
        assertFalse(CrashReporter.accepted(""))
    }
}
