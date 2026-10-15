package br.com.helpdev.velocimetroalerta.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackPointExtensionJsonParserTest {
    @Test
    fun parsesSensorValuesAndMissingOptionalFields() {
        val result = TrackPointExtensionJsonParser.parse("""{"cad":"82","atemp":"21","rhu":null,"hr":"144"}""")

        assertEquals("82", result?.cad)
        assertEquals("21", result?.atemp)
        assertNull(result?.rhu)
        assertEquals("144", result?.hr)
    }

    @Test
    fun returnsNullForJsonNull() {
        assertNull(TrackPointExtensionJsonParser.parse("null"))
    }
}
