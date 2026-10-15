package br.com.helpdev.velocimetroalerta.sensors

import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension
import com.google.gson.Gson

object TrackPointExtensionJsonParser {
    private val gson = Gson()

    fun parse(json: String): TrackPointExtension? = gson.fromJson(json, TrackPointExtension::class.java)
}
