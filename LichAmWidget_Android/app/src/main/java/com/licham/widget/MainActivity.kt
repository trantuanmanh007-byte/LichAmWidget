package com.licham.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var spinnerCity: Spinner
    private lateinit var switchTransparent: SwitchCompat
    private var spinnerInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        spinnerCity = findViewById(R.id.spinner_city)
        switchTransparent = findViewById(R.id.switch_transparent)
        val btnGps: Button = findViewById(R.id.btn_gps)
        val btnRefresh: Button = findViewById(R.id.btn_refresh)

        // Setup City Spinner
        val cityNames = WidgetPrefs.VN_CITIES.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cityNames)
        spinnerCity.adapter = adapter

        val savedCity = WidgetPrefs.getCityName(this)
        val savedIdx = WidgetPrefs.VN_CITIES.indexOfFirst { it.name == savedCity }
        if (savedIdx >= 0) {
            spinnerCity.setSelection(savedIdx)
        }

        spinnerCity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!spinnerInitialized) {
                    spinnerInitialized = true
                    return
                }
                val preset = WidgetPrefs.VN_CITIES[position]
                WidgetPrefs.saveLocation(this@MainActivity, preset.name, preset.lat, preset.lon)
                refreshWeatherNow()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Transparent background toggle
        switchTransparent.isChecked = WidgetPrefs.isTransparentBg(this)
        switchTransparent.setOnCheckedChangeListener { _, isChecked ->
            WidgetPrefs.setTransparentBg(this, isChecked)
            LichAmWidgetProvider.updateAllWidgets(this)
        }

        btnGps.setOnClickListener { requestGpsLocation() }
        btnRefresh.setOnClickListener { refreshWeatherNow() }

        updateStatusText()
        WeatherUpdateWorker.schedulePeriodic(this)
    }

    private fun refreshWeatherNow() {
        Toast.makeText(this, "Đang cập nhật thời tiết & widget...", Toast.LENGTH_SHORT).show()
        Thread {
            WeatherRepository.fetchAndSave(applicationContext)
            LichAmWidgetProvider.updateAllWidgets(applicationContext)
            runOnUiThread {
                updateStatusText()
                Toast.makeText(this, "Đã cập nhật widget xong!", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun requestGpsLocation() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION
            )
            return
        }
        detectCityFromLastLocation()
    }

    @Suppress("DEPRECATION")
    private fun detectCityFromLastLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        val bestLoc = providers.mapNotNull { provider ->
            try {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    lm.getLastKnownLocation(provider)
                } else null
            } catch (_: Exception) {
                null
            }
        }.maxByOrNull { it.time }

        if (bestLoc == null) {
            Toast.makeText(
                this,
                "Chưa lấy được tọa độ GPS gần nhất. Hãy bật Vị trí hoặc chọn tỉnh/thành thủ công.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val lat = bestLoc.latitude
        val lon = bestLoc.longitude
        Thread {
            var resolvedName = "Vị trí hiện tại"
            try {
                val geocoder = Geocoder(this, Locale("vi", "VN"))
                val list = geocoder.getFromLocation(lat, lon, 1)
                if (!list.isNullOrEmpty()) {
                    val addr = list[0]
                    resolvedName = addr.adminArea ?: addr.subAdminArea ?: addr.locality ?: resolvedName
                    resolvedName = resolvedName
                        .replace("Tỉnh ", "")
                        .replace("Thành phố ", "")
                        .trim()
                }
            } catch (_: Exception) {
            }
            WidgetPrefs.saveLocation(applicationContext, resolvedName, lat, lon)
            WeatherRepository.fetchAndSave(applicationContext)
            LichAmWidgetProvider.updateAllWidgets(applicationContext)
            runOnUiThread {
                updateStatusText()
                Toast.makeText(this, "Đã cập nhật vị trí: $resolvedName", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            detectCityFromLastLocation()
        } else {
            Toast.makeText(this, "Bạn có thể chọn tỉnh/thành ở danh sách phía trên.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusText() {
        val now = Calendar.getInstance()
        val lunar = LunarCalendar.convertSolar2Lunar(
            now.get(Calendar.DAY_OF_MONTH),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.YEAR)
        )
        val city = WidgetPrefs.getCityName(this)
        val lastMs = WidgetPrefs.getLastUpdated(this)
        val lastStr = if (lastMs > 0L) {
            SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(lastMs))
        } else {
            "Chưa cập nhật"
        }
        val weather = WeatherRepository.loadCached(this)
        val wStr = if (weather != null) {
            "${weather.currentTemp}°C • ${WeatherRepository.conditionText(weather.currentCode)}"
        } else {
            "Chưa có dữ liệu"
        }
        tvStatus.text = "Hôm nay: ${lunar.badgeLabel()}\nVị trí: $city\nThời tiết: $wStr\nCập nhật lúc: $lastStr"
    }

    companion object {
        private const val REQ_LOCATION = 2001
    }
}
