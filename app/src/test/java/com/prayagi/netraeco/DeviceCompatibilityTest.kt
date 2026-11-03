package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceCompatibilityTest {
    @Test fun unknownMinSdkIsAllowed() {
        assertTrue(sdkCompatible(24, null))
        assertTrue(sdkCompatible(1, null))
    }
    @Test fun boundaryIsInclusive() {
        assertTrue(sdkCompatible(24, 24))
        assertTrue(sdkCompatible(35, 24))
        assertFalse(sdkCompatible(23, 24))
    }
    @Test fun latestJsonMinSdkParsed() {
        val json = """{"tag":"v1.0.0","versionName":"1.0.0","versionCode":1,"minSdk":24,"sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","size":100,"notes":"x"}"""
        assertEquals(24, Net.parseLatest(json, "o/r")?.minSdk)
    }
    @Test fun latestJsonWithoutMinSdkIsNull() {
        val json = """{"tag":"v1.0.0","versionName":"1.0.0","versionCode":1,"sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","size":100,"notes":"x"}"""
        assertNull(Net.parseLatest(json, "o/r")?.minSdk)
    }
    @Test fun invalidMinSdkIsDropped() {
        val json = """{"tag":"v1.0.0","versionName":"1.0.0","versionCode":1,"minSdk":99,"sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","size":100,"notes":"x"}"""
        assertNull(Net.parseLatest(json, "o/r")?.minSdk)
    }
    @Test fun bodyMinSdkParsed() {
        assertEquals(24, Net.parseBodyMinSdk("versionCode: 109
minSdk: 24

notes"))
        assertNull(Net.parseBodyMinSdk("versionCode: 109

notes"))
    }
}
