package com.prayagi.netraeco

/** Explicit release signing identities from the current owning APKs, not metadata supplied by a download. */
internal object UpdateArtifactPolicy {
    private const val ORIGINAL = "543b46402206ae0022d352f65bea618386bd7aa681b4d5bcfb5ce02bac49c8c4"
    private val signers = mapOf(
        "com.aistudio.batterysentinel.ntra" to ORIGINAL,
        "com.aistudio.reasoningquiz.tark" to ORIGINAL,
        "com.aistudio.sensorguard.prvsec" to ORIGINAL,
        "com.prayagi.netraplayer" to ORIGINAL,
        "com.prayagi.netraeco" to ORIGINAL,
        "com.prayagi.netratrikaal" to "38bad1b7117f9cd94f8de94e2c9050861b6c7b482ce4241bfb9b4bf3c89fb8d8",
        "com.netracharge.app" to "814d2dd07f8ba510643795215f61083d1ebc905a5e9b3fba570faa5862381ab9"
    )
    private val catalog = mapOf(
        "battery-sentinel" to ("com.aistudio.batterysentinel.ntra" to "-Battery-Sentinel-Pro-Netra"),
        "kbc" to ("com.aistudio.reasoningquiz.tark" to "KBC"),
        "prayagi-privacy" to ("com.aistudio.sensorguard.prvsec" to "prayagi-Privacy-"),
        "netra-player" to ("com.prayagi.netraplayer" to "netra-player"),
        "netra-eco" to ("com.prayagi.netraeco" to "netra-eco-app"),
        "netra-trikaal" to ("com.prayagi.netratrikaal" to "netra-trikaal")
    )
    fun catalogMatches(id: String, packageName: String, repo: String): Boolean =
        catalog[id]?.let { it.first == packageName && repo == "prayagi-store-and-services/" + it.second } == true
    // Hub remains on the existing human-safety hold. Do not turn its cached APK into an install route.
    fun expectedSigner(packageName: String): String? = signers[packageName]
    fun identityMatches(expectedPackage: String, archivePackage: String?, installedCode: Long?,
        archiveCode: Long, expectedCode: Long, expectedName: String, archiveName: String?,
        installedSigners: Set<String>?, archiveSigners: Set<String>): Boolean {
        val pin = expectedSigner(expectedPackage) ?: return false
        return archivePackage == expectedPackage && archiveCode > (installedCode ?: 0L) &&
            expectedCode >= 0 && (expectedCode == 0L || archiveCode == expectedCode) &&
            archiveName == expectedName && archiveSigners == setOf(pin) &&
            (installedCode == null || installedSigners == setOf(pin))
    }
}
