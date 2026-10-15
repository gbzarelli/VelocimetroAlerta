package br.com.helpdev.velocimetroalerta

import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import br.com.helpdev.velocimetroalerta.dialogs.DialogHR
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class BluetoothScannerLifecycleTest {
    @Test
    fun detachingPreferenceDismissesItsActiveScannerDialog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(ConfigActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.fragments
                    .filterIsInstance<ConfigActivityFragment>()
                    .single()
                val preference = requireNotNull(
                    fragment.findPreference<DialogHR>(context.getString(R.string.pref_hr_sensor))
                )
                val dialog = AlertDialog.Builder(activity)
                    .setTitle(R.string.title_hr_sensor)
                    .setMessage(R.string.search_sensor)
                    .create()
                dialog.show()

                val scanDialogField = DialogHR::class.java.getDeclaredField("scanDialog")
                scanDialogField.isAccessible = true
                scanDialogField.set(preference, dialog)

                preference.onDetached()

                assertFalse("Detaching the preference must dismiss the active scanner dialog", dialog.isShowing)
            }
        }
    }

    @Test
    fun adapterOffDuringDismissStillClearsScannerState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(ConfigActivity::class.java).use { scenario ->
            lateinit var preference: DialogHR
            lateinit var dialog: AlertDialog
            lateinit var scanDialogField: java.lang.reflect.Field
            lateinit var listViewField: java.lang.reflect.Field
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.fragments
                    .filterIsInstance<ConfigActivityFragment>()
                    .single()
                preference = requireNotNull(
                    fragment.findPreference<DialogHR>(context.getString(R.string.pref_hr_sensor))
                )
                dialog = AlertDialog.Builder(activity)
                    .setTitle(R.string.title_hr_sensor)
                    .setMessage(R.string.search_sensor)
                    .create()
                dialog.setOnDismissListener {
                    preference.cleanupScannerAfterDismiss(dialog) {
                        throw IllegalStateException("BT Adapter is not turned ON")
                    }
                }

                scanDialogField = DialogHR::class.java.getDeclaredField("scanDialog")
                scanDialogField.isAccessible = true
                scanDialogField.set(preference, dialog)
                listViewField = DialogHR::class.java.getDeclaredField("listView")
                listViewField.isAccessible = true
                listViewField.set(preference, android.widget.ListView(activity))
                dialog.show()

                dialog.dismiss()
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertNull(scanDialogField.get(preference))
                assertNull(listViewField.get(preference))
                assertFalse("The scanner dialog must be dismissed even when Bluetooth is off", dialog.isShowing)
            }
        }
    }
}
