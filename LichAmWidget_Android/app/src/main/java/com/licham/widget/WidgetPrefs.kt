package com.licham.widget

import android.content.Context

data class CityPreset(val name: String, val lat: Double, val lon: Double)

object WidgetPrefs {
    private const val PREF_NAME = "lich_am_widget_prefs"
    private const val KEY_CITY = "city_name"
    private const val KEY_LAT = "latitude"
    private const val KEY_LON = "longitude"
    private const val KEY_TRANSPARENT = "transparent_bg"
    private const val KEY_WEATHER_JSON = "weather_json"
    private const val KEY_LAST_UPDATED = "last_updated"

    val VN_CITIES = listOf(
        CityPreset("Hà Nội", 21.0285, 105.8542),
        CityPreset("Hưng Yên", 20.6464, 106.0511),
        CityPreset("Hải Phòng", 20.8449, 106.6881),
        CityPreset("Hải Dương", 20.9373, 106.3146),
        CityPreset("Bắc Ninh", 21.1861, 106.0763),
        CityPreset("Quảng Ninh", 20.9599, 107.0425),
        CityPreset("Nam Định", 20.4388, 106.1621),
        CityPreset("Thái Bình", 20.4463, 106.3366),
        CityPreset("Ninh Bình", 20.2506, 105.9745),
        CityPreset("Thanh Hóa", 19.8067, 105.7852),
        CityPreset("Vinh (Nghệ An)", 18.6796, 105.6813),
        CityPreset("Huế", 16.4637, 107.5909),
        CityPreset("Đà Nẵng", 16.0544, 108.2022),
        CityPreset("Nha Trang", 12.2388, 109.1967),
        CityPreset("Đà Lạt", 11.9404, 108.4583),
        CityPreset("TP. Hồ Chí Minh", 10.8231, 106.6297),
        CityPreset("Bình Dương", 10.9804, 106.6519),
        CityPreset("Biên Hòa", 10.9574, 106.8427),
        CityPreset("Vũng Tàu", 10.3460, 107.0843),
        CityPreset("Cần Thơ", 10.0452, 105.7469)
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getCityName(context: Context): String =
        prefs(context).getString(KEY_CITY, "Hà Nội") ?: "Hà Nội"

    fun getLatitude(context: Context): Double =
        java.lang.Double.longBitsToDouble(
            prefs(context).getLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(21.0285))
        )

    fun getLongitude(context: Context): Double =
        java.lang.Double.longBitsToDouble(
            prefs(context).getLong(KEY_LON, java.lang.Double.doubleToRawLongBits(105.8542))
        )

    fun saveLocation(context: Context, city: String, lat: Double, lon: Double) {
        prefs(context).edit()
            .putString(KEY_CITY, city)
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(lon))
            .apply()
    }

    fun isTransparentBg(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TRANSPARENT, true)

    fun setTransparentBg(context: Context, transparent: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRANSPARENT, transparent).apply()
    }

    fun getCachedWeatherJson(context: Context): String? =
        prefs(context).getString(KEY_WEATHER_JSON, null)

    fun saveWeatherJson(context: Context, json: String) {
        prefs(context).edit()
            .putString(KEY_WEATHER_JSON, json)
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun getLastUpdated(context: Context): Long =
        prefs(context).getLong(KEY_LAST_UPDATED, 0L)
}
