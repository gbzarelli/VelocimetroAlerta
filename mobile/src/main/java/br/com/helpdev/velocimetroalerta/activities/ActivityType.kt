package br.com.helpdev.velocimetroalerta.activities

import android.content.Context
import br.com.helpdev.velocimetroalerta.R
import com.google.gson.annotations.SerializedName

enum class ActivityType(
    val stableKey: String,
    val labelResource: Int,
    val stravaSportType: String?
) {
    @SerializedName(value = "bike", alternate = ["BIKE"])
    BIKE("bike", R.string.activity_type_bike, "Ride"),
    @SerializedName(value = "ebike", alternate = ["E_BIKE"])
    E_BIKE("ebike", R.string.activity_type_ebike, "EBikeRide"),
    @SerializedName(value = "mountain_bike", alternate = ["MOUNTAIN_BIKE"])
    MOUNTAIN_BIKE("mountain_bike", R.string.activity_type_mountain_bike, "MountainBikeRide"),
    @SerializedName(value = "gravel", alternate = ["GRAVEL"])
    GRAVEL("gravel", R.string.activity_type_gravel, "GravelRide"),
    @SerializedName(value = "emountain_bike", alternate = ["E_MOUNTAIN_BIKE"])
    E_MOUNTAIN_BIKE("emountain_bike", R.string.activity_type_emountain_bike, "EMountainBikeRide"),
    @SerializedName(value = "virtual_bike", alternate = ["VIRTUAL_BIKE"])
    VIRTUAL_BIKE("virtual_bike", R.string.activity_type_virtual_bike, "VirtualRide"),
    @SerializedName(value = "run", alternate = ["RUN"])
    RUN("run", R.string.activity_type_run, "Run"),
    @SerializedName(value = "walk", alternate = ["WALK"])
    WALK("walk", R.string.activity_type_walk, "Walk"),
    @SerializedName(value = "hike", alternate = ["HIKE"])
    HIKE("hike", R.string.activity_type_hike, "Hike"),
    @SerializedName(value = "vehicle", alternate = ["VEHICLE"])
    VEHICLE("vehicle", R.string.activity_type_vehicle, null);

    fun label(context: Context): String = context.getString(labelResource)

    companion object {
        val selectable: List<ActivityType> = entries.toList()
        val stravaSupported: List<ActivityType> = entries.filter { it.stravaSportType != null }

        fun fromStableKey(key: String?): ActivityType? = entries.firstOrNull { it.stableKey == key || it.name == key }
    }
}
