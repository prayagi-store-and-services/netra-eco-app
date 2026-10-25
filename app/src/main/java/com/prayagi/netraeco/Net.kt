package com.prayagi.netraeco

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Net {
    const val CATALOG_URL = "https://prayagi-store-and-services.github.io/netra-eco/projects.json"

    private fun open(url: String, fresh: Boolean = false, allowRedirects: Boolean = true): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        if (fresh) { c.useCaches = false; c.setRequestProperty("Cache-Control", "no-cache") }
        c.connectTimeout = 10000
        c.readTimeout = 20000
        // GitHub release downloads redirect to their CDN, so repo apps keep redirects. A repo-less app's
        // metadata and APK must answer directly from the pinned Eco-site URL; any redirect is a failure.
        c.instanceFollowRedirects = allowRedirects
        c.setRequestProperty("User-Agent", "netra-eco-app")
        return c
    }

    /** Small text download (catalog or latest.json). Returns null when offline or the server says no. */
    fun fetchText(url: String, fresh: Boolean = false, allowRedirects: Boolean = true): String? = try {
        val c = open(url, fresh, allowRedirects)
        if (c.responseCode != 200) null else {
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size > 1024 * 1024) null else String(bytes, Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    /** "sha256:abc..." from the GitHub API to a plain lowercase hash, or null when it is not a SHA-256. */
    fun shaFromDigest(digest: String): String? =
        digest.removePrefix("sha256:").lowercase().takeIf { digest.startsWith("sha256:") && isValidSha256(it) }

    /**
     * Newest release, read fresh: GitHub's release API first (a cache-busting parameter and no-cache header, so a just
     * published release shows within seconds), then that tag's latest.json, then the older cached latest.json file.
     * A release without a latest.json file (Battery Sentinel) is built from the API's size and SHA-256, with no version code.
     */
    fun fetchLatest(app: CatalogApp): LatestRelease? {
        if (HumanSafetyHold.matches(app.repo, app.packageName)) return null
        if (app.repo.isBlank()) {
            // Repo-less app (private source, public signed APK on the Eco site): only its allowlisted latest.json, never the GitHub API.
            val direct = DIRECT_APPS[app.id]
            if (direct == null || app.packageName != direct.packageName ||
                app.directApkUrl != direct.downloadUrl || app.latestJsonUrl != direct.latestJsonUrl
            ) return null
            return fetchText(app.latestJsonUrl, fresh = true, allowRedirects = false)?.let { parseLatest(it, app.repo, app.directApkUrl) }
        }
        val fromApi = try {
            val body = fetchText("https://api.github.com/repos/${app.repo}/releases/latest?_=${System.currentTimeMillis()}", fresh = true)
            if (body == null) null else {
                val o = JSONObject(body)
                val tag = o.getString("tag_name")
                if (!isValidTag(tag)) null else {
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
                    val viaJson = latestJsonUrl?.let { fetchText(it) }?.let { parseLatest(it, app.repo) }
                    when {
                        viaJson != null && viaJson.tag == tag -> viaJson
                        apkSha != null && apkSize > 0L -> LatestRelease(
                            tag = tag, versionName = tag.removePrefix("v"), versionCode = 0L, notes = "",
                            apkUrl = "https://github.com/${app.repo}/releases/download/$tag/app-release.apk",
                            sha256 = apkSha, size = apkSize
                        )
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
        return fromApi ?: fetchText(app.latestJsonUrl, fresh = true)?.let { parseLatest(it, app.repo) }
    }

    fun parseCatalog(json: String): List<CatalogApp> = try {
        val arr = JSONObject(json).getJSONArray("projects")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val repo = o.optString("repo")
            val pkg = o.optString("package")
            val direct = DIRECT_APPS[id]
            if (repo.isBlank()) {
                // No repo: only the exact allowlisted id, package and URLs. Anything else is dropped.
                val download = o.optString("download")
                val latestJson = o.optString("latestJson")
                if (direct == null || pkg != direct.packageName ||
                    download != direct.downloadUrl || latestJson != direct.latestJsonUrl
                ) null else CatalogApp(
                    id = id,
                    name = o.getString("name"),
                    type = o.optString("type"),
                    summary = o.optString("summary"),
                    repo = "",
                    packageName = pkg,
                    latestJsonUrl = latestJson,
                    siteUrl = o.optString("site"),
                    categories = o.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                    directApkUrl = download,
                    counterCode = o.optString("code")
                )
            } else if (!isTrustedRepo(repo) || pkg.isBlank()) null else CatalogApp(
                id = id,
                name = o.getString("name"),
                type = o.optString("type"),
                summary = o.optString("summary"),
                repo = repo,
                packageName = pkg,
                latestJsonUrl = o.optString("latestJson").ifBlank { "https://github.com/$repo/releases/latest/download/latest.json" },
                siteUrl = o.optString("site"),
                categories = o.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun parseLatest(json: String, repo: String, directApkUrl: String = ""): LatestRelease? = try {
        val o = JSONObject(json)
        val tag = o.getString("tag")
        val sha = o.getString("sha256").lowercase()
        val size = o.getLong("size")
        val code = o.getLong("versionCode")
        val apkUrl = directApkUrl.ifBlank { "https://github.com/$repo/releases/download/$tag/app-release.apk" }
        if (!isValidTag(tag) || !isValidSha256(sha) || size <= 0L || code <= 0L) null else LatestRelease(
            tag = tag,
            versionName = o.optString("versionName", tag.removePrefix("v")),
            versionCode = code,
            notes = o.optString("notes", "").trim(),
            apkUrl = apkUrl,
            sha256 = sha,
            size = size
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
        HumanSafetyHold.requireAllowed(app.repo, app.packageName)
        check(!release.apkUrl.startsWith("https://github.com/" + HumanSafetyHold.REPO + "/")) { HumanSafetyHold.MESSAGE }
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
        val direct = DIRECT_APPS[app.id]
        if (direct != null && release.apkUrl != direct.downloadUrl) {
            file.delete()
            throw IllegalStateException("Unexpected download address for this app.")
        }
        val c = open(release.apkUrl, allowRedirects = direct == null)
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
        direct?.let { verifyDirectFile(context, file, it, release.size, release.sha256, release.versionCode) }
        DownloadCount.countFinishedDownload(context, app.repo, app.counterCode)
        return file
    }

    /**
     * Repo-less apps are verified against the expected metadata: size, SHA-256, package name, version code,
     * exactly one signer matching the pinned certificate, and newer than the installed copy. Runs right after
     * the download and again before every install grant, so a replaced cache file is caught. The file is
     * deleted and an error thrown on any mismatch, exactly like a checksum failure.
     */
    internal fun verifyDirectFile(context: Context, file: File, direct: DirectApp, expectedSize: Long, expectedSha256: String, expectedCode: Long) {
        fun fail(message: String): Nothing {
            file.delete()
            throw IllegalStateException(message)
        }
        if (!file.exists() || file.length() != expectedSize) {
            fail("The downloaded file changed, so it was not installed. Download it again from its app card.")
        }
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16384)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (actual != expectedSha256) {
            fail("The downloaded file changed, so it was not installed. Download it again from its app card.")
        }
        val pm = context.packageManager
        val info = (if (Build.VERSION.SDK_INT >= 28)
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        else
            @Suppress("DEPRECATION") pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
        ) ?: fail("The installer package could not be verified. Download it again from its app card.")
        if (info.packageName != direct.packageName) {
            fail("The downloaded file is not the expected app, so it was not installed.")
        }
        if (PackageInfoCompat.getLongVersionCode(info) != expectedCode) {
            fail("The downloaded file's version did not match its release info, so it was not installed.")
        }
        val installedCode = installed(context, direct.packageName)?.first
        if (installedCode != null && expectedCode <= installedCode) {
            fail("The installed version is the same or newer, so this file was not installed.")
        }
        val signers: List<ByteArray> = if (Build.VERSION.SDK_INT >= 28)
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
        else
            @Suppress("DEPRECATION") info.signatures?.map { it.toByteArray() } ?: emptyList()
        val signerOk = signers.size == 1 &&
            MessageDigest.getInstance("SHA-256").digest(signers[0]).joinToString("") { "%02x".format(it) } == direct.signerCertSha256
        if (!signerOk) {
            fail("The downloaded file's signing key did not match, so it was not installed.")
        }
    }

    /** Opens the system installer. Android installs only if the app is signed with the same key as the installed one. */
    fun install(context: Context, file: File) {
        // Inspect the APK itself as well as the legacy cache filename. Metadata/catalog removal cannot bypass this.
        val packageName = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.packageName
            ?: throw IllegalStateException("The installer package could not be verified. Download it again from its app card.")
        check(!HumanSafetyHold.installerName(file.name)) { HumanSafetyHold.MESSAGE }
        HumanSafetyHold.requireAllowed(packageName = packageName)
        // Protected namespace: ANY of three signals - the cache key starts with an allowlisted app's id, a saved
        // verified record names an allowlisted app, or the archive is an allowlisted app's package - puts this
        // file on the protected route, and then EVERY binding must agree: same allowlisted id from all present
        // signals, a saved record repeating the allowlisted package, the canonical file name for that record,
        // and the canonical cache location. Any disagreement or missing piece refuses; there is no generic
        // fallback, so a swapped-in different-package APK under a Charge key can never sail through.
        val rec = DownloadCenter.savedRecord(context, file.name)
        val recordApp = rec?.appId?.takeIf { DIRECT_APPS.containsKey(it) }
        val keyApp = DIRECT_APPS.keys.firstOrNull { file.name.startsWith("$it-") && file.name.endsWith(".apk") }
        val pkgApp = DIRECT_APPS.entries.firstOrNull { it.value.packageName == packageName }?.key
        val signals = setOfNotNull(recordApp, keyApp, pkgApp)
        if (signals.isNotEmpty()) {
            val id = signals.singleOrNull()
            val direct = id?.let { DIRECT_APPS[it] }
            val bound = id != null && direct != null && rec != null &&
                rec.appId == id && rec.packageName == direct.packageName &&
                file.name == DownloadCenter.canonicalKey(rec) &&
                runCatching { file.canonicalFile.parentFile == DownloadCenter.canonicalDir(context).canonicalFile }.getOrDefault(false)
            if (!bound || rec == null || direct == null) {
                file.delete()
                throw IllegalStateException("The installer could not be matched to a verified download. Download it again from its app card.")
            }
            verifyDirectFile(context, file, direct, rec.size, rec.sha256, rec.versionCode)
        }
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
