package com.prayagi.netraeco

/** One visible list entry, as reported by the lazy list layout. */
data class VisibleEntry(val key: String, val offset: Int, val size: Int)

/**
 * Smooth-scrolling rules for the app list (Deepak, 20 Oct: scrolling had gone hard again).
 *
 * Two costs made the list stutter after preview media landed (v1.2.27): every visible app card
 * spun up its own inline video, and screenshots were decoded at full camera resolution into an
 * unbounded map. These pure rules keep the feature and drop the cost:
 *  - at most ONE card may autoplay its video: the live-app card that fills most of the viewport;
 *  - screenshot bitmaps are decoded at display size, never full resolution.
 */
object PreviewFocus {

    /** A card must show at least this much of itself before its video may autoplay. */
    const val MIN_VISIBLE_FRACTION = 0.5f

    /** Item key prefix used by live app rows in the list (coming-soon and utility cards have no preview). */
    const val LIVE_KEY_PREFIX = "l-"

    /**
     * The item key of the single live-app card that may autoplay its preview video right now:
     * the most visible live card, or null when no live card is at least half visible.
     * Everything else shows the tap-to-play poster, so scrolling never fights more than one video.
     */
    fun pickActive(visible: List<VisibleEntry>, viewportStart: Int, viewportEnd: Int): String? {
        var bestKey: String? = null
        var bestFraction = 0f
        for (entry in visible) {
            if (entry.size <= 0 || !entry.key.startsWith(LIVE_KEY_PREFIX)) continue
            val shown = minOf(entry.offset + entry.size, viewportEnd) - maxOf(entry.offset, viewportStart)
            if (shown <= 0) continue
            val fraction = shown.toFloat() / entry.size
            if (fraction > bestFraction) {
                bestFraction = fraction
                bestKey = entry.key
            }
        }
        return if (bestKey != null && bestFraction >= MIN_VISIBLE_FRACTION) bestKey else null
    }

    /**
     * Bitmap decode sample size: the largest power of two that keeps the decoded height at or
     * above [targetHeight]. Decoding a 12 MP photo to fill a 220 dp row wastes tens of MB per
     * shot and stalls the list; sampling keeps the pixels the screen can actually show.
     */
    fun sampleSize(width: Int, height: Int, targetHeight: Int): Int {
        if (width <= 0 || height <= 0 || targetHeight <= 0) return 1
        var sample = 1
        var h = height
        while (h / 2 >= targetHeight && sample < 16) {
            sample *= 2
            h /= 2
        }
        return sample
    }
}
