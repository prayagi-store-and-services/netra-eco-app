package com.prayagi.netraeco
import org.junit.Assert.assertFalse
import org.junit.Test
/** APK prefetch is forbidden even on Wi-Fi; metadata checks remain allowed. */
class PreDownloadTest {
    @Test fun everyNetworkAndBatteryCombinationRequiresExplicitDownload() {
        for(online in listOf(false,true)) for(wifi in listOf(false,true))
        for(roaming in listOf(false,true)) for(saver in listOf(false,true))
        for(low in listOf(false,true)) for(saved in listOf(false,true))
        for(size in listOf(0L,20L*1024*1024,100L*1024*1024,300L*1024*1024))
            assertFalse(UpdateAlert.shouldPreDownload(online,wifi,roaming,saver,low,saved,size))
    }
}
