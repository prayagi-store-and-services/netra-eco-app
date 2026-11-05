package com.prayagi.netraeco

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Net {
    const val CATALOG_URL = "https://raw.githubusercontent.com/prayagi-store-and-services/netra-eco/main/projects.json"

    private fun open(url: String, fresh: Boolean = false): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        if (fresh) { c.useCaches = false; c.setRequestProperty("Cache-Control", "no-cache") }
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "netra-eco-app")
        return c
    }

    const val CATALOG_FALLBACK = "https://prayagi-store-and-services.github.io/netra-eco/projects.json"
    const val ROADMAP_FALLBACK = "https://prayagi-store-and-services.github.io/netra-eco/roadmap.json"

    /** Only these two public, same-owner data files have a fallback. APK/update trust is unchanged. */
    internal fun publicDataFallback(url: String): String? = when (url) {
        CATALOG_URL -> CATALOG_FALLBACK
        Roadmap.URL -> ROADMAP_FALLBACK
        else -> null
    }

    internal fun fetchWithFallback(url: String, loader: (String) -> String?): String? {
        fun valid(body: String?): String? {
            if (body == null) return null
            if (publicDataFallback(url) == null) return body
            return runCatching {
                val root = JSONObject(body)
                if (url == CATALOG_URL) { root.getJSONArray("projects"); root.getJSONArray("upcoming") }
                else { root.getJSONArray("apps"); root.getJSONArray("items") }
                body
            }.getOrNull()
        }
        return valid(loader(url)) ?: publicDataFallback(url)?.let { valid(loader(it)) }
    }

    /** Small text download. Public data comes directly from GitHub raw; direct GitHub Pages is the fallback. */
    fun fetchText(url: String, fresh: Boolean = false): String? =
        fetchWithFallback(url) { fetchOne(it, fresh) }

    private fun fetchOne(url: String, fresh: Boolean): String? {
        var c: HttpURLConnection? = null
        return try {
            c = open(url, fresh)
            if (c.responseCode != 200) null else c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 1024 * 1024) return null
                    out.write(buffer, 0, count)
                }
                out.toString("UTF-8")
            }
        } catch (_: Exception) { null }
        finally { c?.disconnect() }
    }

    /** "sha256:abc..." from the GitHub API to a plain lowercase hash, or null when it is not a SHA-256. */
    fun shaFromDigest(digest: String): String? =
        digest.removePrefix("sha256:").lowercase().takeIf { digest.startsWith("sha256:") && isValidSha256(it) }

    /**
     * Newest release, read fresh: GitHub's release API first (a cache-busting parameter and no-cache header, so a just
     * published release shows within seconds), then that tag's latest.json, then the older cached latest.json file.
     * A release without a latest.json file (Battery Sentinel) is built from the API's size and SHA-256, with no version code.
     */
    fun fetchLatest(app: CatalogApp, context: Context? = null, textLoader: (String, Boolean) -> String? = ::fetchText): LatestRelease? {
        if (HumanSafetyHold.matches(app.repo, app.packageName)) {
            if (app.repo != HumanSafetyHold.REPO || app.packageName != HumanSafetyHold.PACKAGE) return null
            val raw = textLoader("https://api.github.com/repos/${HumanSafetyHold.REPO}/releases/tags/${HumanSafetyHold.TAG}", true)
            val fresh = raw?.let { HumanSafetyHold.parseRelease(it) }
            if (fresh != null) {
                if (context != null && raw != null) HumanSafetyHold.saveVerified(context, raw)
                return fresh
            }
            // Transient fetch failure (offline or GitHub rate limit): fall back to the last verified release.
            return context?.let { HumanSafetyHold.loadVerified(it) }
        }
        val fromApi = try {
            val body = textLoader("https://api.github.com/repos/${app.repo}/releases/latest", true)
            if (body == null) null else {
                val o = JSONObject(body)
                val tag = o.getString("tag_name")
                if (!isValidTag(tag) || o.optBoolean("draft") || o.optBoolean("prerelease")) null else {
                    val assets = o.getJSONArray("assets")
                    var latestJsonUrl: String? = null
                    var apkSize = 0L
                    var apkSha: String? = null
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        when (a.optString("name")) {
                            "latest.json" -> latestJsonUrl = "https://github.com/${app.repo}/releases/download/$tag/latest.json"
                            "app-release.apk" -> { apkSize = a.optLong("size"); apkSha = shaFromDigest(a.optString("digest")) }
                        }
                    }
                    val viaJson = latestJsonUrl?.let { textLoader(it, true) }?.let { parseLatest(it, app.repo) }
                    when {
                        viaJson != null && viaJson.tag == tag -> viaJson.copy(publishedAtMillis = releaseTimeMillis(o.optString("published_at")))
                        apkSha != null && apkSize > 0L -> LatestRelease(
                            tag = tag, versionName = tag.removePrefix("v"), versionCode = 0L, notes = o.optString("body"),
                            apkUrl = "https://github.com/${app.repo}/releases/download/$tag/app-release.apk",
                            sha256 = apkSha, size = apkSize, publishedAtMillis = releaseTimeMillis(o.optString("published_at")),
                            minSdk = parseBodyMinSdk(o.optString("body"))
                        )
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
        return fromApi ?: textLoader(app.latestJsonUrl, true)?.let { parseLatest(it, app.repo) }
    }

    /** minSdk from a release body line "minSdk: N" (Battery releases carry no latest.json). */
    fun parseBodyMinSdk(body: String): Int? =
        Regex("(?m)^minSdk: ([0-9]+)\r?$").find(body)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..40 }

    fun parseCatalog(json: String): List<CatalogApp> = try {
        val arr = JSONObject(json).getJSONArray("projects")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val repo = o.optString("repo").ifBlank { if (o.optString("id") == "netra-charge" && o.optString("package") == "com.netracharge.app") "prayagi-store-and-services/netra-charge" else "" }
            val pkg = o.optString("package")
            if (!isTrustedRepo(repo) || pkg.isBlank()) null else CatalogApp(
                id = o.getString("id"),
                name = o.getString("name"),
                type = o.optString("type"),
                summary = o.optString("summary"),
                repo = repo,
                packageName = pkg,
                latestJsonUrl = o.optString("latestJson").ifBlank { "https://github.com/$repo/releases/latest/download/latest.json" },
                siteUrl = o.optString("site"),
                privacyUrl = catalogPrivacyUrl(o.optString("privacy")),
                categories = o.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun parseLatest(json: String, repo: String): LatestRelease? = try {
        val o = JSONObject(json)
        val tag = o.getString("tag")
        val sha = o.getString("sha256").lowercase()
        val size = o.getLong("size")
        val code = o.getLong("versionCode")
        val apkFile = o.optString("apkFile", "app-release.apk")
        require(Regex("^[A-Za-z0-9._-]+\\.apk$").matches(apkFile))
        val minSdk = if (o.has("minSdk")) o.getInt("minSdk").takeIf { it in 1..40 } else null
        if (!isValidTag(tag) || !isValidSha256(sha) || size <= 0L || code <= 0L) null else LatestRelease(
            tag = tag,
            versionName = o.optString("versionName", tag.removePrefix("v")),
            versionCode = code,
            notes = o.optString("notes", "").trim(),
            apkUrl = "https://github.com/$repo/releases/download/$tag/$apkFile",
            sha256 = sha,
            size = size,
            publishedAtMillis = releaseTimeMillis(o.optString("published_at")),
            minSdk = minSdk
        )
    } catch (e: Exception) {
        null
    }

    /** Installed version code and name, or null when the app is not installed. */
    fun installed(context: Context, packageName: String): Pair<Long, String>? = try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        Pair(PackageInfoCompat.getLongVersionCode(info), info.versionName ?: "")
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** Netra Ecosystem itself, so the manual "Check for update" button can use the same download and install flow. */
    const val SELF_REPO = "prayagi-store-and-services/netra-eco-app"

    fun selfApp(context: Context) = CatalogApp(
        id = "netra-eco", name = "Netra Ecosystem", type = "", summary = "", repo = SELF_REPO,
        packageName = context.packageName,
        latestJsonUrl = "https://github.com/$SELF_REPO/releases/latest/download/latest.json",
        siteUrl = ""
    )

    /** Number of downloads running right now; installer files are only cleaned when it is 0. */
    private val activeDownloads = java.util.concurrent.atomic.AtomicInteger(0)

    /** True while any download (from a screen or from the update notification) is running. */
    fun downloadsRunning(): Boolean = activeDownloads.get() != 0

    /** Deletes every file in the folder and returns how many were removed. */
    fun cleanDir(dir: File?): Int {
        var n = 0
        dir?.listFiles()?.forEach { if (it.delete()) n++ }
        return n
    }

    /**
     * Deletes downloaded installer files so nothing stays in storage after an install. Called when the
     * screen comes back (for example after the system installer closes) and never during a download.
     */
    fun cleanLeftovers(context: Context) {
        if (activeDownloads.get() != 0) return
        try { DownloadCenter.cleanFinished(context) } catch (_: Exception) {}
    }

    /** Downloads the APK and checks size and SHA-256. The file is deleted and an error thrown if anything is off. */
    fun download(context: Context, app: CatalogApp, release: LatestRelease, onProgress: ((Long, Long, Long?) -> Unit)? = null): File {
        HumanSafetyHold.requireAllowed(app.repo, app.packageName, release)
        check(!release.apkUrl.startsWith("https://github.com/" + HumanSafetyHold.REPO + "/") ||
            (app.repo == HumanSafetyHold.REPO && app.packageName == HumanSafetyHold.PACKAGE && HumanSafetyHold.allowed(release))) { HumanSafetyHold.MESSAGE }
        activeDownloads.incrementAndGet()
        try {
            return downloadInternal(context, app, release, onProgress)
        } finally {
            activeDownloads.decrementAndGet()
        }
    }

    private fun downloadInternal(context: Context, app: CatalogApp, release: LatestRelease, onProgress: ((Long, Long, Long?) -> Unit)?): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Only this app's older files go; other apps' downloads (running or finished) stay.
        dir.listFiles()?.filter { it.name.startsWith(app.id + "-") }?.forEach { it.delete() }
        val file = File(dir, "${app.id}-${release.tag}.apk")
        val c = open(release.apkUrl)
        if (c.responseCode != 200) throw IllegalStateException("Download failed (server answered ${c.responseCode}).")
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val tracker = SpeedTracker()
        var lastReport = 0L
        c.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(16384)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > release.size) {
                        file.delete()
                        throw IllegalStateException("Downloaded file is larger than expected.")
                    }
                    md.update(buf, 0, n)
                    out.write(buf, 0, n)
                    val now = System.currentTimeMillis()
                    tracker.add(now, total)
                    if (onProgress != null && now - lastReport >= 300L) {
                        lastReport = now
                        onProgress(total, release.size, tracker.etaSeconds(total, release.size))
                    }
                }
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (total != release.size || actual != release.sha256) {
            file.delete()
            throw IllegalStateException("The downloaded file did not match its checksum, so it was not installed.")
        }
        try { InstallerGate.remember(context, app, release) } catch (e: Exception) { file.delete(); throw e }
        DownloadCount.countFinishedDownload(context, app.repo)
        return file
    }

    /** Opens the system installer. Android installs only if the app is signed with the same key as the installed one. */
    suspend fun install(context: Context, file: File) {
        withContext(Dispatchers.IO) {
        // Inspect the APK itself as well as the legacy cache filename. Metadata/catalog removal cannot bypass this.
        val archive = InstallerGate.archive(context, file)
            ?: run { file.delete(); throw IllegalStateException("The installer package could not be verified. Download it again from its app card.") }
        try {
            HumanSafetyHold.requireInstaller(file, archive.packageName, archive.versionName, PackageInfoCompat.getLongVersionCode(archive))
            InstallerGate.requireValid(context, file, archive)
        } catch (e: Exception) { file.delete(); throw e }
        }
        withContext(Dispatchers.Main.immediate) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            throw IllegalStateException("Allow installs from Netra Ecosystem, then tap the button again.")
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        }
    }
}
