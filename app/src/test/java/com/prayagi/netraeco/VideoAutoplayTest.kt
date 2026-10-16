package com.prayagi.netraeco

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAutoplayTest {
    @Test fun wifiAutoplays() = assertTrue(VideoAutoplay.shouldAutoplay(PreviewNetwork.WIFI))

    @Test fun mobileDataNeverAutoplays() =
        assertFalse(VideoAutoplay.shouldAutoplay(PreviewNetwork.MOBILE_OR_OTHER))

    @Test fun offlineNeverAutoplays() =
        assertFalse(VideoAutoplay.shouldAutoplay(PreviewNetwork.OFFLINE))
}
