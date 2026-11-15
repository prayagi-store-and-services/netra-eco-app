package com.prayagi.netraeco

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlaybackGuardTest {
    @Test fun idleSpeaks() = assertFalse(MediaPlaybackGuard.mediaBlocks(false, false))
    @Test fun musicBlocks() = assertTrue(MediaPlaybackGuard.mediaBlocks(true, false))
    @Test fun playbackConfigBlocks() = assertTrue(MediaPlaybackGuard.mediaBlocks(false, true))
    @Test fun detectorErrorFailsClosed() {
        assertTrue(MediaPlaybackGuard.mediaBlocks(null, false))
        assertTrue(MediaPlaybackGuard.mediaBlocks(false, null))
        assertTrue(MediaPlaybackGuard.mediaBlocks(null, null))
    }
}
