package com.prayagi.netraeco

/** Measures download speed over a rolling window so the time left adjusts as the connection changes. */
class SpeedTracker(private val windowMs: Long = 5000L) {
    private val samples = ArrayDeque<Pair<Long, Long>>()

    fun add(nowMs: Long, bytesDone: Long) {
        samples.addLast(nowMs to bytesDone)
        while (samples.size > 2 && nowMs - samples.first().first > windowMs) samples.removeFirst()
    }

    /** Bytes per second, or null until there is at least one second of measurement. */
    fun bytesPerSecond(): Double? {
        if (samples.size < 2) return null
        val (t0, b0) = samples.first()
        val (t1, b1) = samples.last()
        val dt = t1 - t0
        if (dt < 1000L || b1 <= b0) return null
        return (b1 - b0) * 1000.0 / dt
    }

    /** Seconds left, or null (shown as Unavailable) when the speed is not measured yet. */
    fun etaSeconds(bytesDone: Long, total: Long): Long? {
        val s = bytesPerSecond() ?: return null
        if (total <= 0L || bytesDone > total) return null
        return Math.ceil((total - bytesDone) / s).toLong()
    }
}

object DownloadText {
    fun percentLeft(done: Long, total: Long): Int? =
        if (total <= 0L || done < 0L || done > total) null else (100 - (done * 100 / total)).toInt()

    fun formatEta(seconds: Long?): String = when {
        seconds == null -> "Unavailable"
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
        else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    }

    fun line(done: Long, total: Long, eta: Long?): String {
        val left = percentLeft(done, total)
        val pct = if (left == null) "Unavailable" else "$left% left"
        return "$pct, time left: ${formatEta(eta)} (estimated)"
    }
}
