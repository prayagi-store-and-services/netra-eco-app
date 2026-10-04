package com.prayagi.netraeco

import android.content.Context
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Crash reports. When the app crashes, a short report is saved on the device. Nothing is sent by itself: the user sends it from the Crash report card after seeing the exact text.
 * The report holds ONLY: app name, phone model, Android version, app version and the crash stack trace
 * (exception class names and code locations; exception messages are dropped). No name, email, location, files or device IDs.
 */
object CrashReporter {
    private const val APP_NAME = "NetraEco"
    private const val FILE = "pending_crash_report.txt"
    private const val LAST_FILE = "last_crash_report.txt"
    private const val ENDPOINT = "https://formsubmit.co/ajax/prayagideepak@gmail.com"
    private const val MAX_TRACE = 3000
    @Volatile private var installed = false

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
            runCatching { val t = sanitize(throwable); File(app.filesDir, FILE).writeText(t); File(app.filesDir, LAST_FILE).writeText(t) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun appVersion(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "unknown" }.getOrDefault("unknown")

    fun sendPending(context: Context, version: String): Boolean {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return false
        val trace = runCatching { file.readText() }.getOrDefault("")
        if (trace.isBlank()) { runCatching { file.delete() }; return false }
        return try {
            val json = org.json.JSONObject()
            json.put("_subject", "[Netra] Automatic crash report: $APP_NAME")
            json.put("_captcha", "false")
            json.put("_template", "table")
            json.put("app", APP_NAME)
            json.put("device", Build.MODEL ?: "Unknown")
            json.put("android_version", Build.VERSION.RELEASE ?: "Unknown")
            json.put("app_version", version)
            json.put("stack_trace", trace)
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 15000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
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
        val trace = runCatching { File(context.filesDir, LAST_FILE).readText() }.getOrDefault("")
        if (trace.isBlank()) return null
        return "App: $APP_NAME\nApp version: ${appVersion(context)}\nPhone model: ${Build.MODEL ?: "Unknown"}\nAndroid version: ${Build.VERSION.RELEASE ?: "Unknown"}\n\nLast crash (class names and code locations only):\n$trace"
    }

    /** Sends the last saved crash on user request. true only if the service confirms. Call off the main thread. */
    fun sendManual(context: Context): Boolean {
        val trace = runCatching { File(context.filesDir, LAST_FILE).readText() }.getOrDefault("")
        if (trace.isBlank()) return false
        return try {
            val json = org.json.JSONObject()
            json.put("_subject", "[Netra] Manual crash report: $APP_NAME")
            json.put("_captcha", "false"); json.put("_template", "table")
            json.put("app", APP_NAME); json.put("device", Build.MODEL ?: "Unknown")
            json.put("android_version", Build.VERSION.RELEASE ?: "Unknown")
            json.put("app_version", appVersion(context)); json.put("stack_trace", trace)
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 15000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode in 200..299 && accepted(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
            ok
        } catch (_: Exception) { false }
    }
}
