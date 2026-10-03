package com.prayagi.netraeco

/** One Netra app listed in the catalog (read from the Eco site's projects.json). */
data class CatalogApp(
    val id: String,
    val name: String,
    val type: String,
    val summary: String,
    val repo: String,
    val packageName: String,
    val latestJsonUrl: String,
    val siteUrl: String
)

/** The newest release of an app, from its latest.json. */
data class LatestRelease(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val notes: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long
)

enum class Status { NotInstalled, UpdateAvailable, UpToDate, InstalledNewer, Unavailable }

/**
 * Status shown on a card. When the latest version could not be read we say Unavailable,
 * we never guess from stale data.
 */
fun statusFor(installedCode: Long?, latestCode: Long?): Status = when {
    latestCode == null -> Status.Unavailable
    installedCode == null -> Status.NotInstalled
    latestCode > installedCode -> Status.UpdateAvailable
    latestCode == installedCode -> Status.UpToDate
    else -> Status.InstalledNewer
}

fun isValidTag(tag: String): Boolean = Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+$").matches(tag)

fun isValidSha256(sha: String): Boolean = Regex("^[0-9a-f]{64}$").matches(sha)

/** Only these two places may serve an APK or a catalog: this site and GitHub releases of this organization. */
const val ORG = "prayagi-store-and-services"

fun isTrustedRepo(repo: String): Boolean = Regex("^" + Regex.escape(ORG) + "/[A-Za-z0-9._-]+$").matches(repo)

fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "Unavailable"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}

/**
 * Release notes worth showing. Empty when the text is only the generic "Backup update source" line
 * that some release files carry, so we hide it instead of showing a meaningless note.
 */
fun usefulNotes(raw: String): String {
    val t = raw.trim()
    if (t.isBlank() || t.startsWith("Backup update source", ignoreCase = true)) return ""
    return t
}

/** Short label for the status pill on a card. */
fun statusLabel(status: Status): String = when (status) {
    Status.NotInstalled -> "Not installed"
    Status.UpdateAvailable -> "Update available"
    Status.UpToDate -> "Up to date"
    Status.InstalledNewer -> "Newer than published"
    Status.Unavailable -> "Unavailable"
}
