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

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "netra-eco-app")
        return c
    }

    /** Small text download (catalog or latest.json). Returns null when offline or the server says no. */
    fun fetchText(url: String): String? = try {
        val c = open(url)
        if (c.responseCode != 200) null else {
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size > 1024 * 1024) null else String(bytes, Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    fun parseCatalog(json: String): List<CatalogApp> = try {
        val arr = JSONObject(json).getJSONArray("projects")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val repo = o.optString("repo")
            val pkg = o.optString("package")
            if (!isTrustedRepo(repo) || pkg.isBlank()) null else CatalogApp(
                id = o.getString("id"),
                name = o.getString("name"),
                type = o.optString("type"),
                summary = o.optString("summary"),
                repo = repo,
                packageName = pkg,
                latestJsonUrl = o.optString("latestJson").ifBlank { "https://github.com/$repo/releases/latest/download/latest.json" },
                siteUrl = o.optString("site")
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

    /** Downloads the APK and checks size and SHA-256. The file is deleted and an error thrown if anything is off. */
    fun download(context: Context, app: CatalogApp, release: LatestRelease): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${app.id}-${release.tag}.apk")
        val c = open(release.apkUrl)
        if (c.responseCode != 200) throw IllegalStateException("Download failed (server answered ${c.responseCode}).")
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
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
                }
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (total != release.size || actual != release.sha256) {
            file.delete()
            throw IllegalStateException("The downloaded file did not match its checksum, so it was not installed.")
        }
        return file
    }

    /** Opens the system installer. Android installs only if the app is signed with the same key as the installed one. */
    fun install(context: Context, file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            throw IllegalStateException("Allow installs from Netra Eco, then tap the button again.")
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
