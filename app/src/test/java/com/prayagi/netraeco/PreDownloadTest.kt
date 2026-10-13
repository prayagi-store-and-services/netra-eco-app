package com.prayagi.netraeco

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreDownloadTest {
    private val mb = 1024L * 1024
    private fun d(online: Boolean = true, unmetered: Boolean = false, roaming: Boolean = false, saver: Boolean = false, low: Boolean = false, saved: Boolean = false, size: Long = 20 * mb) =
        UpdateAlert.shouldPreDownload(online, unmetered, roaming, saver, low, saved, size)

    @Test fun wifiDownloadsAnySize() = assertTrue(d(unmetered = true, size = 300 * mb))
    @Test fun mobileDataSmallFileDownloads() = assertTrue(d(size = 20 * mb))
    @Test fun mobileDataAtTheLimitDownloads() = assertTrue(d(size = 100 * mb))
    @Test fun mobileDataOverTheLimitWaitsForWifi() = assertFalse(d(size = 100 * mb + 1))
    @Test fun mobileDataUnknownSizeWaits() = assertFalse(d(size = 0))
    @Test fun roamingOnMobileDataNeverDownloads() = assertFalse(d(roaming = true))
    @Test fun offlineNeverDownloads() = assertFalse(d(online = false, unmetered = true))
    @Test fun dataSaverStops() = assertFalse(d(unmetered = true, saver = true))
    @Test fun lowBatteryStops() = assertFalse(d(unmetered = true, low = true))
    @Test fun alreadySavedIsNotDownloadedAgain() = assertFalse(d(unmetered = true, saved = true))
}
