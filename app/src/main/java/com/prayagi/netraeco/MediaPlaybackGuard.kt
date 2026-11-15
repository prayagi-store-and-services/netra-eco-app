package com.prayagi.netraeco

import android.content.Context
import android.media.AudioManager
import android.os.Build

/**
 * Owner rule (15 Nov 2026): a spoken announcement is SKIPPED while any media plays. It never
 * pauses, ducks or takes audio focus. The check runs at admission and again right before the
 * actual speak call. If the detector cannot decide, the announcement is skipped (fails closed).
 * Known limits: silent video, cast/remote playback and OEM-hidden players may not be visible.
 */
object MediaPlaybackGuard {
    /** Pure rule. A null input means the detector failed: that blocks the announcement. */
    fun mediaBlocks(musicActive: Boolean?, playbackActive: Boolean?): Boolean =
        musicActive != false || playbackActive != false

    fun isMediaActive(context: Context): Boolean {
        val am = try { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager } catch (_: Throwable) { null }
            ?: return true
        val music: Boolean? = try { am.isMusicActive } catch (_: Throwable) { null }
        // AudioPlaybackConfiguration.isActive exists from API 34; older phones rely on isMusicActive.
        val playback: Boolean? = try {
            if (Build.VERSION.SDK_INT >= 34) am.activePlaybackConfigurations.any { it.isActive } else false
        } catch (_: Throwable) { null }
        return mediaBlocks(music, playback)
    }
}
