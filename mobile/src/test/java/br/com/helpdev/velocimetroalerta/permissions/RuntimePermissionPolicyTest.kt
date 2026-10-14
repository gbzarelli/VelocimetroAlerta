package br.com.helpdev.velocimetroalerta.permissions

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimePermissionPolicyTest {
    @Test
    fun locationPermissionsFollowRuntimePermissionApi() {
        assertEquals(emptySet<String>(), RuntimePermissionPolicy.permissionsFor(RuntimePermissionPolicy.Feature.LOCATION, 22))
        assertEquals(
            setOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            RuntimePermissionPolicy.permissionsFor(RuntimePermissionPolicy.Feature.LOCATION, 23)
        )
    }

    @Test
    fun bluetoothScanUsesLocationBeforeAndroid12AndScanAfter() {
        assertEquals(
            setOf(Manifest.permission.ACCESS_FINE_LOCATION),
            RuntimePermissionPolicy.permissionsFor(RuntimePermissionPolicy.Feature.BLUETOOTH_SCAN, 30)
        )
        assertEquals(
            setOf(Manifest.permission.BLUETOOTH_SCAN),
            RuntimePermissionPolicy.permissionsFor(RuntimePermissionPolicy.Feature.BLUETOOTH_SCAN, 31)
        )
    }

    @Test
    fun bluetoothConnectUsesAndroid12Permission() {
        assertEquals(
            emptySet<String>(),
            RuntimePermissionPolicy.permissionsFor(RuntimePermissionPolicy.Feature.BLUETOOTH_CONNECT, 30)
        )
        assertEquals(
            setOf(Manifest.permission.BLUETOOTH_CONNECT),
            RuntimePermissionPolicy.permissionsFor(RuntimePermissionPolicy.Feature.BLUETOOTH_CONNECT, 31)
        )
    }

    @Test
    fun notificationsBecomeRuntimePermissionOnAndroid13() {
        assertEquals(
            emptySet<String>(),
            RuntimePermissionPolicy.permissionsFor(RuntimePermissionPolicy.Feature.POST_NOTIFICATIONS, 32)
        )
        assertEquals(
            setOf(Manifest.permission.POST_NOTIFICATIONS),
            RuntimePermissionPolicy.permissionsFor(RuntimePermissionPolicy.Feature.POST_NOTIFICATIONS, 33)
        )
    }
}
