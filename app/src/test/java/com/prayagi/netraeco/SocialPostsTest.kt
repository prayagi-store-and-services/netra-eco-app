package com.prayagi.netraeco
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class SocialPostsTest {
 private val now = Instant.parse("2026-11-05T07:00:00Z")
 private fun feed(date: String = "2026-11-05", counts: String = "{\"youtube\":2,\"x\":0,\"telegram\":1}") = """{"schemaVersion":1,"timezone":"Asia/Kolkata","date":"$date","generatedAt":"2026-11-05T06:00:00Z","counts":$counts}"""
 @Test fun truePositiveCountsOnly() { assertEquals(mapOf("youtube" to 2, "telegram" to 1), SocialPosts.parse(feed(),now)) }
 @Test fun noBadgeOnMissingInvalidOrStale() { listOf(null,"{}","broken",feed("2026-11-04")).forEach { assertTrue(SocialPosts.parse(it,now).isEmpty()) } }
 @Test fun rejectsNegativeFractionalAndUnknown() { assertEquals(mapOf("facebook" to 1),SocialPosts.parse(feed(counts="""{"youtube":-1,"instagram":1.5,"telegram":"3","evil":9,"facebook":1}"""),now)) }
 @Test fun resetsAtIstMidnight() { assertTrue(SocialPosts.parse(feed(),Instant.parse("2026-11-05T18:30:00Z")).isEmpty());assertEquals("2026-11-06",SocialPosts.day(Instant.parse("2026-11-05T18:30:00Z"))) }
 @Test fun futureDateOrGeneratedTimeDoesNotGetBadge() { assertTrue(SocialPosts.parse(feed().replace("06:00:00Z","08:00:00Z"),now).isEmpty()) }
}
