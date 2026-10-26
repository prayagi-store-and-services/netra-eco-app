package com.prayagi.netraeco

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import org.json.JSONObject

/** Only this audited Human Safety release may pass. Other/older Hub bytes remain blocked. */
object HumanSafetyHold {
    const val REPO = "prayagi-store-and-services/netra-hub"
    const val PACKAGE = "com.aistudio.netrasensorhub.kxmpzq"
    const val TAG = "v1.5.2"
    const val VERSION = "1.5.2"
    const val CODE = 78L
    const val SIZE = 4439086L
    const val SHA = "0f07aa1681888893b4e27ad194783d0ab41afc0d05787de14cd0f189e85f4245"
    const val URL = "https://github.com/prayagi-store-and-services/netra-hub/releases/download/v1.5.2/app-release.apk"
    const val MESSAGE = "Only the checked Human Safety v1.5.2 file is allowed. Older or unverified installers stay paused."
    const val ADVISORY = "Human Safety v1.5.2 only. Owner reported heating/battery resolved on the tested build; no measured battery or thermal guarantee. Updates are handled through Netra Ecosystem only."
    fun matches(repo: String = "", packageName: String = ""): Boolean = repo == REPO || packageName == PACKAGE
    fun installerName(name: String): Boolean = name.startsWith("netra-hub-v") && name.endsWith(".apk")
    fun allowed(release: LatestRelease?): Boolean = release != null && release.tag == TAG &&
        release.versionName == VERSION && release.versionCode == CODE && release.size == SIZE &&
        release.sha256 == SHA && release.apkUrl == URL
    fun requireAllowed(repo: String = "", packageName: String = "", release: LatestRelease? = null) {
        check(!matches(repo, packageName) || (repo == REPO && packageName == PACKAGE && allowed(release))) { MESSAGE }
    }
    fun parseRelease(raw: String): LatestRelease? = try {
        val x = JSONObject(raw)
        if (x.getString("tag_name") != TAG || x.getBoolean("draft") || x.getBoolean("prerelease")) null
        else {
            Instant.parse(x.getString("published_at"))
            val assets = x.getJSONArray("assets")
            val valid = (0 until assets.length()).any { i ->
                val a = assets.getJSONObject(i)
                a.optString("name") == "app-release.apk" && a.optString("state") == "uploaded" &&
                    a.optLong("size") == SIZE && a.optString("digest") == "sha256:$SHA" &&
                    a.optString("browser_download_url") == URL
            }
            if (!valid) null else LatestRelease(TAG, VERSION, CODE,
                "App-local updater removed. Owner reported heating/battery resolved on the tested build; not a measured guarantee.", URL, SHA, SIZE)
        }
    } catch (_: Exception) { null }
    fun fileAllowed(file: File): Boolean = try {
        file.isFile && file.length() == SIZE && file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16384)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it) } == SHA
        }
    } catch (_: Exception) { false }
    fun requireInstaller(file: File, packageName: String, versionName: String?, versionCode: Long) {
        check(!(matches(packageName = packageName) || installerName(file.name)) ||
            (packageName == PACKAGE && versionName == VERSION && versionCode == CODE && fileAllowed(file))) { MESSAGE }
    }
}
