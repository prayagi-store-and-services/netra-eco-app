package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppRequestsTest {
    @Test fun dailyGuardBlocksSecondSendSameDay() {
        assertTrue(AppRequests.canSendToday(null, "2026-10-16"))
        assertTrue(AppRequests.canSendToday("2026-10-15", "2026-10-16"))
        assertFalse(AppRequests.canSendToday("2026-10-16", "2026-10-16"))
    }

    @Test fun payloadHasSubjectAndCappedRequest() {
        val long = "x".repeat(AppRequests.MAX_REQUEST + 500)
        val j = AppRequests.buildPayload(long, "", "1.2.31")
        assertEquals("[Netra] New app request", j.getString("_subject"))
        assertEquals(AppRequests.MAX_REQUEST, j.getString("request").length)
        assertFalse(j.has("contact_optional"))
    }

    @Test fun optionalContactIncludedOnlyWhenFilledAndCapped() {
        val withContact = AppRequests.buildPayload("please build a bus tracker app", "user@example.com", "1.2.31")
        assertEquals("user@example.com", withContact.getString("contact_optional"))
        val tooLong = AppRequests.buildPayload("please build a bus tracker app", "y".repeat(AppRequests.MAX_CONTACT + 50), "1.2.31")
        assertEquals(AppRequests.MAX_CONTACT, tooLong.getString("contact_optional").length)
        val blank = AppRequests.buildPayload("please build a bus tracker app", "   ", "1.2.31")
        assertFalse(blank.has("contact_optional"))
    }

    @Test fun previewShowsExactTextAndOmitsEmptyContact() {
        val p = AppRequests.preview("  please build a bus tracker app  ", "", "1.2.31")
        assertTrue(p.contains("please build a bus tracker app"))
        assertFalse(p.contains("Contact (optional)"))
        assertTrue(p.contains("App version: 1.2.31"))
        val pc = AppRequests.preview("please build a bus tracker app", "user@example.com", "1.2.31")
        assertTrue(pc.contains("user@example.com"))
    }
}
