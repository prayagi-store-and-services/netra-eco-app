package com.prayagi.netraeco

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupTest {
    @Test fun removesEveryInstallerFile() {
        val dir = java.nio.file.Files.createTempDirectory("updates").toFile()
        File(dir, "kbc-v1.apk").writeText("a")
        File(dir, "hub-v1.apk").writeText("b")
        assertEquals(2, Net.cleanDir(dir))
        assertTrue(dir.listFiles()!!.isEmpty())
        dir.delete()
    }

    @Test fun missingFolderIsFine() {
        assertEquals(0, Net.cleanDir(File("/nonexistent-folder-for-test")))
        assertEquals(0, Net.cleanDir(null))
    }
}
