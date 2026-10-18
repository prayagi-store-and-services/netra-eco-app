package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {
    @Test fun traceKeepsClassNamesButDropsExceptionMessage() {
        val t = IllegalStateException("secret-user-text 555-1234")
        val s = CrashReporter.sanitize(t)
        assertTrue(s.startsWith("java.lang.IllegalStateException"))
        assertFalse(s.contains("secret-user-text"))
    }
    @Test fun causeMessageIsDroppedToo() {
        val s = CrashReporter.sanitize(RuntimeException("outer", IllegalArgumentException("inner-secret")))
        assertTrue(s.contains("Caused by: java.lang.IllegalArgumentException"))
        assertFalse(s.contains("inner-secret"))
    }
    @Test fun acceptedNeedsRealSuccessAnswer() {
        assertTrue(CrashReporter.accepted("{\"success\":\"true\",\"message\":\"ok\"}"))
        assertTrue(CrashReporter.accepted("{\"success\":true}"))
        assertFalse(CrashReporter.accepted("{\"success\":\"false\",\"message\":\"Needs Activation\"}"))
        assertFalse(CrashReporter.accepted(""))
    }
    @Test fun unpackReadsCrashTimeFactsFromHeader() {
        val raw = "netraeco-crash-v1|v=1.4.17|t=1760697600000\njava.lang.IllegalStateException\n  at a.b(:1)"
        val saved = CrashReporter.unpack(raw)
        assertEquals("1.4.17", saved.version)
        assertEquals(1760697600000L, saved.crashedAt)
        assertEquals("java.lang.IllegalStateException\n  at a.b(:1)", saved.trace)
    }
    @Test fun unpackLegacyFileHasNoFacts() {
        val raw = "java.lang.IllegalStateException\n  at a.b(:1)"
        val saved = CrashReporter.unpack(raw)
        assertNull(saved.version)
        assertNull(saved.crashedAt)
        assertEquals(raw, saved.trace)
    }
    @Test fun legacyLabelNeverClaimsACurrentVersion() {
        val saved = CrashReporter.unpack("java.lang.Exception\n  at x.y(:2)")
        assertNull(saved.version)
    }
}
