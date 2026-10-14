package br.com.helpdev.velocimetroalerta.permissions

import android.Manifest

/** Runtime permissions grouped by the feature that needs them. */
object RuntimePermissionPolicy {
    enum class Feature {
        LOCATION,
        BLUETOOTH_SCAN,
        BLUETOOTH_CONNECT,
        POST_NOTIFICATIONS
    }

    fun permissionsFor(feature: Feature, sdkInt: Int): Set<String> = when (feature) {
        Feature.LOCATION -> if (sdkInt >= 23) {
            setOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            emptySet()
        }
        Feature.BLUETOOTH_SCAN -> if (sdkInt >= 31) {
            setOf(Manifest.permission.BLUETOOTH_SCAN)
        } else if (sdkInt >= 23) {
            setOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            emptySet()
        }
        Feature.BLUETOOTH_CONNECT -> if (sdkInt >= 31) {
            setOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptySet()
        }
        Feature.POST_NOTIFICATIONS -> if (sdkInt >= 33) {
            setOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptySet()
        }
    }
}
