package com.licham.widget

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

data class DailyForecast(
    val minTemp: Int,
    val maxTemp: Int,
    val weatherCode: Int
)

data class WeatherData(
    val currentTemp: Int,
    val currentCode: Int,
    val daily: List<DailyForecast>
)

object WeatherRepository {

    private val FRAMES_CLEAR = intArrayOf(
        R.drawable.ic_w3d_clear_f0,
        R.drawable.ic_w3d_clear_f1,
        R.drawable.ic_w3d_clear_f2,
        R.drawable.ic_w3d_clear_f3,
        R.drawable.ic_w3d_clear_f4,
        R.drawable.ic_w3d_clear_f5
    )

    private val FRAMES_PARTLY_CLOUDY = intArrayOf(
        R.drawable.ic_w3d_partly_cloudy_f0,
        R.drawable.ic_w3d_partly_cloudy_f1,
        R.drawable.ic_w3d_partly_cloudy_f2,
        R.drawable.ic_w3d_partly_cloudy_f3,
        R.drawable.ic_w3d_partly_cloudy_f4,
        R.drawable.ic_w3d_partly_cloudy_f5
    )

    private val FRAMES_CLOUDY = intArrayOf(
        R.drawable.ic_w3d_cloudy_f0,
        R.drawable.ic_w3d_cloudy_f1,
        R.drawable.ic_w3d_cloudy_f2,
        R.drawable.ic_w3d_cloudy_f3,
        R.drawable.ic_w3d_cloudy_f4,
        R.drawable.ic_w3d_cloudy_f5
    )

    private val FRAMES_FOG = intArrayOf(
        R.drawable.ic_w3d_fog_f0,
        R.drawable.ic_w3d_fog_f1,
        R.drawable.ic_w3d_fog_f2,
        R.drawable.ic_w3d_fog_f3,
        R.drawable.ic_w3d_fog_f4,
        R.drawable.ic_w3d_fog_f5
    )

    private val FRAMES_RAIN = intArrayOf(
        R.drawable.ic_w3d_rain_f0,
        R.drawable.ic_w3d_rain_f1,
        R.drawable.ic_w3d_rain_f2,
        R.drawable.ic_w3d_rain_f3,
        R.drawable.ic_w3d_rain_f4,
        R.drawable.ic_w3d_rain_f5
    )

    private val FRAMES_STORM = intArrayOf(
        R.drawable.ic_w3d_storm_f0,
        R.drawable.ic_w3d_storm_f1,
        R.drawable.ic_w3d_storm_f2,
        R.drawable.ic_w3d_storm_f3,
        R.drawable.ic_w3d_storm_f4,
        R.drawable.ic_w3d_storm_f5
    )

    fun fetchAndSave(context: Context): WeatherData? {
        val lat = WidgetPrefs.getLatitude(context)
        val lon = WidgetPrefs.getLongitude(context)
        val urlStr = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=Asia%%2FBangkok&forecast_days=5",
            lat, lon
        )
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val parsed = parseWeather(response)
                if (parsed != null) {
                    WidgetPrefs.saveWeatherJson(context, response)
                }
                parsed
            } else {
                loadCached(context)
            }
        } catch (_: Exception) {
            loadCached(context)
        } finally {
            conn?.disconnect()
        }
    }

    fun loadCached(context: Context): WeatherData? {
        val raw = WidgetPrefs.getCachedWeatherJson(context) ?: return null
        return parseWeather(raw)
    }

    private fun parseWeather(rawJson: String): WeatherData? {
        return try {
            val root = JSONObject(rawJson)
            val current = root.getJSONObject("current")
            val curTemp = current.getDouble("temperature_2m").roundToInt()
            val curCode = current.getInt("weather_code")

            val dailyObj = root.getJSONObject("daily")
            val codes = dailyObj.getJSONArray("weather_code")
            val maxArr = dailyObj.getJSONArray("temperature_2m_max")
            val minArr = dailyObj.getJSONArray("temperature_2m_min")

            val count = minOf(5, codes.length(), maxArr.length(), minArr.length())
            val list = ArrayList<DailyForecast>(count)
            for (i in 0 until count) {
                list.add(
                    DailyForecast(
                        minTemp = minArr.getDouble(i).roundToInt(),
                        maxTemp = maxArr.getDouble(i).roundToInt(),
                        weatherCode = codes.getInt(i)
                    )
                )
            }
            WeatherData(curTemp, curCode, list)
        } catch (_: Exception) {
            null
        }
    }

    fun conditionText(code: Int): String {
        return when (code) {
            0 -> "Trời quang"
            1 -> "Ít mây"
            2 -> "Có mây"
            3 -> "Nhiều mây"
            45, 48 -> "Có sương mù"
            51, 53, 55, 56, 57 -> "Mưa phùn"
            61, 63, 65, 66, 67 -> "Có mưa"
            80, 81, 82 -> "Mưa rào"
            95, 96, 99 -> "Dông bão"
            else -> "Có mây"
        }
    }

    fun weatherAnimFrames(code: Int): IntArray {
        return when (code) {
            0 -> FRAMES_CLEAR
            1, 2 -> FRAMES_PARTLY_CLOUDY
            3 -> FRAMES_CLOUDY
            45, 48 -> FRAMES_FOG
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> FRAMES_RAIN
            95, 96, 99 -> FRAMES_STORM
            else -> FRAMES_CLOUDY
        }
    }

    fun weatherIconRes(code: Int): Int {
        return weatherAnimFrames(code)[0]
    }
}
