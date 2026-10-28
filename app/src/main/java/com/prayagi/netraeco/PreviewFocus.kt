package com.prayagi.netraeco

/** Display-sized screenshot decoding avoids full-resolution memory use. */
object PreviewFocus {
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
