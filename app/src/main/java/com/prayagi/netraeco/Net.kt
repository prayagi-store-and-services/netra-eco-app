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

object Net {
    const val CATALOG_URL = "https://prayagi-store-and-services.github.io/netra-eco/projects.json"

    private fun open(url: String, fresh: Boolean = false): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        if (fresh) { c.useCaches = false; c.setRequestProperty("Cache-Control", "no-cache") }
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "netra-eco-app")
        return c
    }

    /** Small text download (catalog or latest.json). Returns null when offline or the server says no. */
    fun fetchText(url: String, fresh: Boolean = false): String? = try {
        val c = open(url, fresh)
        if (c.responseCode != 200) null else {
            val bytes = c.inputStream.use { readBounded(it, 1024 * 1024) }
            if (bytes.size > 1024 * 1024) null else String(bytes, Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    internal fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size() + 1))
            if (n < 0) return out.toByteArray()
            if (out.size() + n > limit) throw java.io.IOException("Response exceeds limit")
            out.write(buffer, 0, n)
        }
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
            val repo = o.optString("repo")
            val pkg = o.optString("package")
            if (!isTrustedRepo(repo) || !(UpdateArtifactPolicy.catalogMatches(o.optString("id"), pkg, repo) || (o.optString("id") == "netra-hub" && HumanSafetyHold.matches(repo, pkg)))) null else CatalogApp(
                id = o.getString("id"),
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

    fun parseLatest(json: String, repo: String): LatestRelease? = try {
        val o = JSONObject(json)
        val tag = o.getString("tag")
        val sha = o.getString("sha256").lowercase()
        val size = o.getLong("size")
        val code = o.getLong("versionCode")
        if (!isValidTag(tag) || !isValidSha256(sha) || size <= 0L || code <= 0L) null else LatestRelease(
            tag = tag,
            versionName = o.optString("versionName", tag.removePrefix("v")),
            versionCode = code,
            notes = o.optString("notes", "").trim(),
            apkUrl = "https://github.com/$repo/releases/download/$tag/app-release.apk",
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

    /** Netra Eco itself, so the manual "Check for update" button can use the same download and install flow. */
    const val SELF_REPO = "prayagi-store-and-services/netra-eco-app"

    fun selfApp(context: Context) = CatalogApp(
        id = "netra-eco", name = "Netra Ecosystem", type = "", summary = "", repo = SELF_REPO,
        packageName = context.packageName,
        latestJsonUrl = "https://github.com/$SELF_REPO/releases/latest/download/latest.json",
        siteUrl = ""
    )

    /** Number of downloads running right now; installer files are only cleaned when it is 0. */
    private val activeDownloads = java.util.concurrent.atomic.AtomicInteger(0)

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
        activeDownloads.incrementAndGet()
        try {
            return downloadInternal(context, app, release, onProgress)
        } finally {
            activeDownloads.decrementAndGet()
        }
    }

    private fun downloadInternal(context: Context, app: CatalogApp, release: LatestRelease, onProgress: ((Long, Long, Long?) -> Unit)?): File {
        check(UpdateArtifactPolicy.catalogMatches(app.id, app.packageName, app.repo)) { "Update catalog identity did not match." }
        check(UpdateArtifactPolicy.expectedSigner(app.packageName) != null) { "This app is unknown or on safety hold. No update was downloaded." }
        check(Regex("^[A-Za-z0-9._-]+$").matches(app.id) && isValidTag(release.tag)) { "Invalid update identity." }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "${app.id}-${release.tag}.apk")
        val partial = File.createTempFile("pending-", ".part", dir)
        val c = open(release.apkUrl)
        try {
            if (c.responseCode != 200) throw IllegalStateException("Download failed (server answered ${c.responseCode}).")
            val tracker = SpeedTracker()
            var lastReport = 0L
            c.inputStream.use { input ->
                ApkIntegrity.copyVerified(input, partial, release.size, release.sha256) { total ->
                    val now = System.currentTimeMillis()
                    tracker.add(now, total)
                    if (onProgress != null && now - lastReport >= 300L) {
                        lastReport = now
                        onProgress(total, release.size, tracker.etaSeconds(total, release.size))
                    }
                }
            }
            java.nio.file.Files.move(partial.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            try {
                verifyArchive(context, file, app.packageName, release)
                // Only verified metadata is persisted. Old/unregistered cached APKs must be downloaded again.
                check(context.getSharedPreferences("verified_update_artifacts", Context.MODE_PRIVATE).edit()
                    .putString(file.name, JSONObject().put("package", app.packageName).put("size", release.size).put("sha256", release.sha256)
                        .put("code", release.versionCode).put("version", release.versionName).toString()).commit())
            } catch (e: Exception) { file.delete(); throw e }
            DownloadCount.countFinishedDownload(context, app.repo)
            return file
        } finally {
            c.disconnect()
            partial.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures?.map { signature ->
            java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }?.toSet().orEmpty()
    }

    @Suppress("DEPRECATION")
    internal fun verifyArchive(context: Context, file: File, expectedPackage: String, release: LatestRelease) {
        check(ApkIntegrity.matches(file, release.size, release.sha256)) { "Update checksum changed. Download the update again." }
        val flags = if (android.os.Build.VERSION.SDK_INT >= 28) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else android.content.pm.PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IllegalStateException("Update package could not be read.")
        val current = try { pm.getPackageInfo(expectedPackage, flags) } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
        val installedCode = current?.let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        check(UpdateArtifactPolicy.identityMatches(expectedPackage, archive.packageName, installedCode, code,
            release.versionCode, release.versionName, archive.versionName, current?.let { signerDigests(it) }, signerDigests(archive))) {
            "Update package, version or signing identity did not match the expected app. Nothing was installed."
        }
    }

    /** Re-check the exact downloaded self-update before asking Android for its ordinary install UI. */
    fun install(context: Context, file: File) {
        val archivePackage = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.packageName
            ?: throw IllegalStateException("The installer package could not be verified.")
        check(!HumanSafetyHold.installerName(file.name)) { HumanSafetyHold.MESSAGE }
        HumanSafetyHold.requireAllowed(packageName = archivePackage)
        val root = File(context.cacheDir, "updates").canonicalFile
        check(file.canonicalFile.parentFile == root) { "Unknown update file." }
        try {
            val raw = context.getSharedPreferences("verified_update_artifacts", Context.MODE_PRIVATE).getString(file.name, null)
                ?: throw IllegalStateException("This cached update needs fresh verification. Delete it and download again.")
            val pin = JSONObject(raw)
            val release = LatestRelease("", pin.getString("version"), pin.getLong("code"), "", "", pin.getString("sha256"), pin.getLong("size"))
            HumanSafetyHold.requireAllowed(packageName = pin.getString("package"))
            verifyArchive(context, file, pin.getString("package"), release)
        } catch (e: Exception) {
            file.delete()
            context.getSharedPreferences("verified_update_artifacts", Context.MODE_PRIVATE).edit().remove(file.name).apply()
            throw e
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            // This is Android's per-source installer permission, not a Play Protect override.
            // It is reached only after an explicit Install tap and strict file verification.
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            throw IllegalStateException("Android needs your choice for this install source. No install was started. Keep Play Protect enabled; do not bypass a security warning.")
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
