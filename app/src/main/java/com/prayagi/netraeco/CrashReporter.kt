package com.prayagi.netraeco

import android.content.Context
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Automatic crash reports. When the app crashes, a short report is saved on the device. The next time the app opens,
 * it is sent by itself (no button, no question) and then deleted; if the send fails it is kept and retried next start.
 * The report holds ONLY: app name, phone model, Android version, the app version and time AT THE MOMENT OF THE CRASH,
 * and the crash stack trace (exception class names and code locations; exception messages are dropped).
 * No name, email, location, files or device IDs. Crash-time facts are saved with the trace so an old crash
 * is never shown or sent under a newer app's version label.
 */
object CrashReporter {
    private const val APP_NAME = "NetraEco"
    private const val FILE = "pending_crash_report.txt"
    private const val LAST_FILE = "last_crash_report.txt"
    private const val ENDPOINT = "https://formsubmit.co/ajax/prayagideepak@gmail.com"
    private const val MAX_TRACE = 3000
    private const val HEADER_PREFIX = "netraeco-crash-v1|"
    @Volatile private var installed = false

    /** A saved crash file: crash-time facts plus the trace. Legacy files (no header) parse with null facts. */
    internal data class SavedCrash(val version: String?, val crashedAt: Long?, val trace: String)

    internal fun pack(context: Context, trace: String): String =
        HEADER_PREFIX + "v=" + appVersion(context) + "|t=" + System.currentTimeMillis() + "\n" + trace

    internal fun unpack(raw: String): SavedCrash {
        if (!raw.startsWith(HEADER_PREFIX)) return SavedCrash(null, null, raw)
        val nl = raw.indexOf('\n')
        if (nl < 0) return SavedCrash(null, null, raw)
        var v: String? = null
        var t: Long? = null
        for (part in raw.substring(HEADER_PREFIX.length, nl).split('|')) {
            if (part.startsWith("v=")) v = part.substring(2)
            if (part.startsWith("t=")) t = part.substring(2).toLongOrNull()
        }
        return SavedCrash(v, t, raw.substring(nl + 1))
    }

    internal fun formatCrashTime(t: Long): String =
        java.text.SimpleDateFormat("d MMM yyyy, h:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date(t))

    /** Honest label when the file predates crash-time version saving. */
    private const val LEGACY_VERSION_LABEL = "unknown (crash was saved by an older app version)"

    /** Exception class names and code locations only. */
    fun sanitize(t: Throwable): String {
        val sb = StringBuilder()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 5) {
            sb.append(if (depth == 0) "" else "Caused by: ").append(cur.javaClass.name).append('\n')
            for (frame in cur.stackTrace.take(25)) sb.append("  at ").append(frame.toString()).append('\n')
            cur = cur.cause
            depth++
        }
        return sb.toString().take(MAX_TRACE)
    }

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { val packed = pack(app, sanitize(throwable)); File(app.filesDir, FILE).writeText(packed); File(app.filesDir, LAST_FILE).writeText(packed) }
            previous?.uncaughtException(thread, throwable)
        }
        Thread { sendPending(app) }.start()
    }

    /** Compile-time version constant: readable even mid-crash, when PackageManager can be unavailable. */
    private fun appVersion(c: Context): String =
        com.prayagi.netraeco.BuildConfig.VERSION_NAME.ifBlank { "unknown" }

    fun sendPending(context: Context): Boolean {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return false
        val raw = runCatching { file.readText() }.getOrDefault("")
        if (raw.isBlank()) { runCatching { file.delete() }; return false }
        val saved = unpack(raw)
        return try {
            val json = org.json.JSONObject()
            json.put("_subject", "[Netra] Automatic crash report: $APP_NAME")
            json.put("_captcha", "false")
            json.put("_template", "table")
            json.put("app", APP_NAME)
            json.put("device", Build.MODEL ?: "Unknown")
            json.put("android_version", Build.VERSION.RELEASE ?: "Unknown")
            json.put("app_version", saved.version ?: LEGACY_VERSION_LABEL)
            if (saved.crashedAt != null) json.put("crash_time", formatCrashTime(saved.crashedAt))
            json.put("stack_trace", saved.trace)
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 15000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("Origin", "https://prayagi-store-and-services.github.io"); c.setRequestProperty("Referer", "https://prayagi-store-and-services.github.io/")
            c.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode in 200..299 && accepted(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
            if (ok) runCatching { file.delete() }
            ok
        } catch (_: Exception) { false }
    }

    /** FormSubmit answers {"success":"true"} only when it really queued the email; an HTTP 200 alone is not enough. */
    fun accepted(body: String): Boolean = Regex("\"success\"\\s*:\\s*\"?true\"?", RegexOption.IGNORE_CASE).containsMatchIn(body)

    /** The exact text a manual report contains, or null when no crash is saved on this device. */
    fun manualPreview(context: Context): String? {
        val raw = runCatching { File(context.filesDir, LAST_FILE).readText() }.getOrDefault("")
        if (raw.isBlank()) return null
        val saved = unpack(raw)
        val timeLine = saved.crashedAt?.let { "\nCrash time: " + formatCrashTime(it) } ?: ""
        return "App: $APP_NAME\nApp version: ${saved.version ?: LEGACY_VERSION_LABEL}$timeLine\nPhone model: ${Build.MODEL ?: "Unknown"}\nAndroid version: ${Build.VERSION.RELEASE ?: "Unknown"}\n\nLast crash (class names and code locations only):\n${saved.trace}"
    }

    /** Sends the last saved crash on user request. true only if the service confirms. Call off the main thread. */
    fun sendManual(context: Context): Boolean {
        val raw = runCatching { File(context.filesDir, LAST_FILE).readText() }.getOrDefault("")
        if (raw.isBlank()) return false
        val saved = unpack(raw)
        return try {
            val json = org.json.JSONObject()
            json.put("_subject", "[Netra] Manual crash report: $APP_NAME")
            json.put("_captcha", "false"); json.put("_template", "table")
            json.put("app", APP_NAME); json.put("device", Build.MODEL ?: "Unknown")
            json.put("android_version", Build.VERSION.RELEASE ?: "Unknown")
            json.put("app_version", saved.version ?: LEGACY_VERSION_LABEL)
            if (saved.crashedAt != null) json.put("crash_time", formatCrashTime(saved.crashedAt))
            json.put("stack_trace", saved.trace)
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 15000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("Origin", "https://prayagi-store-and-services.github.io"); c.setRequestProperty("Referer", "https://prayagi-store-and-services.github.io/")
            c.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode in 200..299 && accepted(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
            ok
        } catch (_: Exception) { false }
    }
}
