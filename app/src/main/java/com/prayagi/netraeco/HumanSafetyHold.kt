package com.prayagi.netraeco

/** Local emergency quarantine. No server response, cached catalog or newer version may lift it.
 * Removal requires a separately reviewed build after the owner verifies the fix.
 * This does not revoke APKs outside this app or affect previously installed clients.
 */
object HumanSafetyHold {
    const val REPO = "prayagi-store-and-services/netra-hub"
    const val PACKAGE = "com.aistudio.netrasensorhub.kxmpzq"
    const val MESSAGE = "Filhaal Netra Human Safety ko install ya use na karein. Phone garam hone aur battery tezi se ghatne ki reports mili hain. Installed ho toh abhi uninstall kar dein. Jaancha hua fix verify hone tak download band rahega; sirf naya version aa jana fix ka saboot nahi hai."
    fun matches(repo: String = "", packageName: String = ""): Boolean = repo == REPO || packageName == PACKAGE
    fun installerName(name: String): Boolean = name.startsWith("netra-hub-v") && name.endsWith(".apk")
    fun requireAllowed(repo: String = "", packageName: String = "") {
        check(!matches(repo, packageName)) { MESSAGE }
    }
}
