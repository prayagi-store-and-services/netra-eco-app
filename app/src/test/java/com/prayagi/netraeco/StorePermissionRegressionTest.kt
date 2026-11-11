package com.prayagi.netraeco

import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Minimum-permission regression, not a claim that Google has cleared this APK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorePermissionRegressionTest {
 @Test fun storeDoesNotRequestRemovedLocationOrSensitiveFraudPermissions() {
  val c: Context = RuntimeEnvironment.getApplication()
  val permissions = c.packageManager.getPackageInfo(c.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
  listOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION", "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.BIND_ACCESSIBILITY_SERVICE", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "android.permission.QUERY_ALL_PACKAGES").forEach { assertFalse(it, permissions.contains(it)) }
  assertTrue(permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
 }
}
