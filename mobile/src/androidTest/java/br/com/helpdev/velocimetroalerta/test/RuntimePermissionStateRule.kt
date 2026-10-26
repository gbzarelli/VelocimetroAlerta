package br.com.helpdev.velocimetroalerta.test

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class RuntimePermissionState(
    val granted: Array<String> = [],
    val denied: Array<String> = [],
    val locationPermissionPromptAlreadyHandled: Boolean = true
)

class RuntimePermissionStateRule(
    private val granted: Set<String> = emptySet(),
    private val denied: Set<String> = emptySet()
) : TestRule {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val annotation = description.getAnnotation(RuntimePermissionState::class.java)
            val requestedGrants = annotation?.granted?.toSet() ?: granted
            val requestedDenials = annotation?.denied?.toSet() ?: denied
            require(requestedGrants.intersect(requestedDenials).isEmpty()) {
                "A runtime permission cannot be both granted and denied"
            }

            val applicablePermissions = (requestedGrants + requestedDenials)
                .associateWith { permission ->
                    val introducedIn = runtimePermissionIntroducedIn[permission]
                        ?: error("Unsupported runtime permission in test fixture: $permission")
                    Build.VERSION.SDK_INT >= introducedIn
                }
                .filterValues { it }
                .keys.toList()

            try {
                resetTestState()
                setLaunchPermissionPromptState(annotation?.locationPermissionPromptAlreadyHandled ?: true)
                requestedGrants.intersect(applicablePermissions).forEach { assertPermissionState(it, granted = true) }
                requestedDenials.intersect(applicablePermissions).forEach { assertPermissionState(it, granted = false) }
                base.evaluate()
            } finally {
                resetTestState()
            }
        }
    }

    private fun assertPermissionState(permission: String, granted: Boolean) {
        val actual = context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        check(actual == granted) {
            "Host test harness must set $permission to granted=$granted before instrumentation starts"
        }
    }

    private fun resetTestState() {
        context.stopService(Intent(context, SpeedometerService::class.java))
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(LOCATION_DISCLOSURE_ACCEPTED)
            .commit()
    }

    private fun setLaunchPermissionPromptState(alreadyHandled: Boolean) {
        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        if (alreadyHandled) {
            editor.putBoolean(LOCATION_LAUNCH_PROMPT_HANDLED, true)
        } else {
            editor.remove(LOCATION_LAUNCH_PROMPT_HANDLED)
        }
        editor.commit()
    }

    private companion object {
        const val LOCATION_DISCLOSURE_ACCEPTED = "location_background_disclosure_accepted"
        const val LOCATION_LAUNCH_PROMPT_HANDLED = "location_launch_permission_prompt_handled"

        val runtimePermissionIntroducedIn = mapOf(
            Manifest.permission.ACCESS_COARSE_LOCATION to Build.VERSION_CODES.M,
            Manifest.permission.ACCESS_FINE_LOCATION to Build.VERSION_CODES.M,
            Manifest.permission.BLUETOOTH_SCAN to Build.VERSION_CODES.S,
            Manifest.permission.BLUETOOTH_CONNECT to Build.VERSION_CODES.S,
            Manifest.permission.POST_NOTIFICATIONS to 33
        )
    }
}
