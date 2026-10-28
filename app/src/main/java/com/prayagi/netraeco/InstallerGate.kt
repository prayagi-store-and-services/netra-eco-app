package com.prayagi.netraeco

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** Every install entry ends here. Legacy or unbound cached files must be downloaded again. */
internal object InstallerGate {
    private const val PREFS = "verified_installer_metadata"
    private const val ORIGINAL = "543b46402206ae0022d352f65bea618386bd7aa681b4d5bcfb5ce02bac49c8c4"
    private val identities = mapOf(
        "battery-sentinel" to ("com.aistudio.batterysentinel.ntra" to "-Battery-Sentinel-Pro-Netra"),
        "kbc" to ("com.aistudio.reasoningquiz.tark" to "KBC"),
        "netra-hub" to (HumanSafetyHold.PACKAGE to "netra-hub"),
        "prayagi-privacy" to ("com.aistudio.sensorguard.prvsec" to "prayagi-Privacy-"),
        "netra-trikaal" to ("com.prayagi.netratrikaal" to "netra-trikaal"),
        "netra-player" to ("com.prayagi.netraplayer" to "netra-player"),
        "netra-eco" to ("com.prayagi.netraeco" to "netra-eco-app")
    )
    fun expectedSigner(pkg: String): String? = when {
        pkg == "com.prayagi.netratrikaal" -> "38bad1b7117f9cd94f8de94e2c9050861b6c7b482ce4241bfb9b4bf3c89fb8d8"
        identities.values.any { it.first == pkg } -> ORIGINAL
        else -> null
    }
    fun hash(file: File): String = file.inputStream().use { input ->
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16384)
        while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
        md.digest().joinToString("") { "%02x".format(it) }
    }
    fun bytesMatch(file: File, release: LatestRelease): Boolean = runCatching {
        file.isFile && file.length() == release.size && isValidSha256(release.sha256) && hash(file) == release.sha256
    }.getOrDefault(false)
    fun identityMatches(pkg: String, version: String, code: Long, archivePkg: String?, archiveVersion: String?, archiveCode: Long, signers: Set<String>): Boolean =
        pkg == archivePkg && version == archiveVersion && archiveCode > 0 && (code == 0L || archiveCode == code) &&
            expectedSigner(pkg)?.let { signers == setOf(it) } == true

    /** Snapshot comes only from the existing trusted release resolver, never from the APK itself. */
    fun remember(c: Context, app: CatalogApp, release: LatestRelease) {
        val identity = identities[app.id]
        check(identity != null && identity.first == app.packageName && app.repo == "$ORG/${identity.second}") { "This app's installer identity is not approved." }
        HumanSafetyHold.requireAllowed(app.repo, app.packageName, release)
        val o = JSONObject().put("package", app.packageName).put("version", release.versionName).put("code", release.versionCode)
            .put("size", release.size).put("sha", release.sha256)
        check(c.getSharedPreferences(PREFS, 0).edit().putString(DownloadCenter.keyFor(app, release), o.toString()).commit())
    }
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") info.signatures
        return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }
    fun requireValid(c: Context, file: File, archive: PackageInfo) {
        try {
            val raw = c.getSharedPreferences(PREFS, 0).getString(file.name, null)
                ?: error("Installer could not be verified. Download it again from its app card.")
            val o = JSONObject(raw)
            check(file.isFile && file.length() == o.getLong("size") && hash(file) == o.getString("sha")) { "Installer checksum mismatch. Download it again." }
            check(identityMatches(o.getString("package"), o.getString("version"), o.getLong("code"), archive.packageName,
                archive.versionName, PackageInfoCompat.getLongVersionCode(archive), signers(archive))) { "Installer package, version or signing certificate did not match. Download it again." }
        } catch (e: Exception) {
            file.delete()
            c.getSharedPreferences(PREFS, 0).edit().remove(file.name).apply()
            throw IllegalStateException(e.message ?: "Installer could not be verified. Download it again.", e)
        }
    }
    fun archive(c: Context, file: File): PackageInfo? = c.packageManager.getPackageArchiveInfo(file.absolutePath,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES)
}
