package br.com.helpdev.velocimetroalerta

import android.graphics.Color
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpsPrecisionDisplayTest {
    @Test
    fun poorPrecisionWarningReturnsAfterStaleGpsWarningClears() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<MainActivityFragment>().single()
                fragment.setGpsSituation(SpeedometerService.CallbackGpsThread.GPS_DESATUALIZADO)
                fragment.setGpsPrecision(SpeedometerService.CallbackGpsThread.GPS_SEM_PRECISAO)
                fragment.setGpsSituation(SpeedometerService.CallbackGpsThread.GPS_ATUALIZADO)

                assertEquals(Color.RED, activity.findViewById<android.widget.TextView>(R.id.precision).currentTextColor)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tvPrecisionInfo).visibility)

                fragment.setGpsPrecision(SpeedometerService.CallbackGpsThread.GPS_PRECISAO_OK)
                assertEquals(Color.BLACK, activity.findViewById<android.widget.TextView>(R.id.precision).currentTextColor)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.tvPrecisionInfo).visibility)
            }
        }
    }

    @Test
    fun unknownPrecisionClearsPreviouslyDisplayedReading() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<MainActivityFragment>().single()
                val precision = activity.findViewById<android.widget.TextView>(R.id.precision)
                precision.text = "6.0"

                fragment.setGpsPrecision(SpeedometerService.CallbackGpsThread.GPS_PRECISAO_DESCONHECIDA)

                assertEquals(activity.getString(R.string.text_null_value), precision.text.toString())
                assertEquals(Color.BLACK, precision.currentTextColor)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.tvPrecisionInfo).visibility)
            }
        }
    }
}
