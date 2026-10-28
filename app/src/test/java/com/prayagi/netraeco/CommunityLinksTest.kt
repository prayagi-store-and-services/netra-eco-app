package com.prayagi.netraeco

import org.junit.Assert.*
import org.junit.Test

class CommunityLinksTest {
    @Test fun officialProfilesAreFixedAndUnique() {
        assertEquals(5, CommunityLinks.social.size)
        assertEquals(5, CommunityLinks.social.map { it.url }.toSet().size)
        assertTrue(CommunityLinks.social.all { it.url.startsWith("https://") })
        assertEquals("https://t.me/Netraecosystem", CommunityLinks.social.last().url)
    }
    @Test fun perAppDestinationsNeverAcceptUnknownIds() {
        for (id in listOf("battery-sentinel", "kbc", "netra-hub", "prayagi-privacy", "netra-player", "netra-trikaal", "netra-eco", "netra-charge")) {
            assertNotNull(CommunityLinks.website(id))
            assertEquals("https://prayagi-store-and-services.github.io/netra-eco/embed.html?app=$id", CommunityLinks.reviews(id))
            assertEquals("https://prayagi-store-and-services.github.io/netra-eco/contact.html?app=$id", CommunityLinks.contact(id))
        }
        for (id in listOf("", "unknown", "netra-eco&device=secret", "../hub")) {
            assertNull(CommunityLinks.website(id)); assertNull(CommunityLinks.reviews(id)); assertNull(CommunityLinks.contact(id))
        }
    }
}
