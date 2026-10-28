package com.prayagi.netraeco

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CatalogReleaseAlertTest {
    private lateinit var c: Application
    private val planned = """{"projects":[],"upcoming":[{"id":"future","name":"Future"}]}"""
    private val live = """{"projects":[],"upcoming":[]}"""
    private val row = AppRow(CatalogApp("future", "Future", "", "", "", "pkg.future", "", ""), null,
        LatestRelease("v1.0.0", "1.0.0", 1, "", "", "a".repeat(64), 1))
    @Before fun setup() {
        c = RuntimeEnvironment.getApplication()
        c.getSharedPreferences("catalog_release_alerts", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(c).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }
    @Test fun deniedReleaseRemainsPendingThenPostsOnceWhenAllowed() {
        CatalogReleaseAlert.observe(c, planned, emptyList())
        shadowOf(c).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        CatalogReleaseAlert.observe(c, live, listOf(row))
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(0, shadowOf(nm).size())
        assertTrue(c.getSharedPreferences("catalog_release_alerts", 0).getStringSet("pending", emptySet())!!.contains("future"))
        shadowOf(c).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        CatalogReleaseAlert.observe(c, live, listOf(row))
        assertEquals(1, shadowOf(nm).size())
        assertTrue(c.getSharedPreferences("catalog_release_alerts", 0).getStringSet("pending", emptySet())!!.isEmpty())
        val posted = nm.activeNotifications.single().notification
        assertEquals(CatalogReleaseAlert.ACTION_CURRENT, shadowOf(posted.contentIntent).savedIntent.action)
        nm.cancelAll()
        CatalogReleaseAlert.observe(c, live, listOf(row))
        assertEquals(0, shadowOf(nm).size())
    }
    @Test fun disabledChannelDoesNotConsumePendingRelease() {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("netra_new_app_releases", "New Netra apps", NotificationManager.IMPORTANCE_NONE))
        CatalogReleaseAlert.observe(c, planned, emptyList())
        CatalogReleaseAlert.observe(c, live, listOf(row))
        assertEquals(0, shadowOf(nm).size())
        assertTrue(c.getSharedPreferences("catalog_release_alerts", 0).getStringSet("pending", emptySet())!!.contains("future"))
    }
    @Test fun firstInstallDoesNotAnnounceAllOldAppsAndFailedReadsDoNotErasePlans() {
        CatalogReleaseAlert.observe(c, live, listOf(row))
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(0, shadowOf(nm).size())
        CatalogReleaseAlert.observe(c, planned, emptyList())
        CatalogReleaseAlert.observe(c, "broken", emptyList())
        CatalogReleaseAlert.observe(c, live, listOf(row.copy(latest = null)))
        assertEquals(0, shadowOf(nm).size())
        CatalogReleaseAlert.observe(c, live, listOf(row))
        assertEquals(1, shadowOf(nm).size())
    }
}
