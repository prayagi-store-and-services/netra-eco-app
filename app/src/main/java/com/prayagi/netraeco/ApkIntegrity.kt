package com.prayagi.netraeco

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** A final APK exists only after a full size/hash check. Interrupted bytes stay private and temporary. */
internal object ApkIntegrity {
    fun matches(file: File, size: Long, sha: String): Boolean = try {
        if (size <= 0 || file.length() != size || !isValidSha256(sha)) false else {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(16384)
                while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
            }
            md.digest().joinToString("") { "%02x".format(it) } == sha.lowercase()
        }
    } catch (_: Exception) { false }

    fun copyVerified(input: InputStream, partial: File, size: Long, sha: String, progress: (Long) -> Unit) {
        require(size > 0 && isValidSha256(sha)) { "Invalid update integrity metadata." }
        var total = 0L
        partial.outputStream().use { out ->
            val buffer = ByteArray(16384)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                total += n
                if (total > size) throw java.io.IOException("Downloaded file is larger than expected.")
                out.write(buffer, 0, n); progress(total)
            }
        }
        if (!matches(partial, size, sha)) throw java.io.IOException("The downloaded file did not match its checksum, so it was not installed.")
    }
}
