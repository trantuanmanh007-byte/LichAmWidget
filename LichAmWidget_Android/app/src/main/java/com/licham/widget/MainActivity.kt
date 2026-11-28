package com.licham.widget

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
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
import androidx.core.text.HtmlCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvAlmanacSolar: TextView
    private lateinit var tvAlmanacLunar: TextView
    private lateinit var tvAlmanacDayType: TextView
    private lateinit var tvAlmanacHoliday: TextView
    private lateinit var tvAlmanacProverb: TextView
    private lateinit var tvAlmanacDetails: TextView
    private lateinit var tvUpcomingHolidays: TextView

    private lateinit var tvStatus: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var btnUpdateAction: Button
    private lateinit var spinnerCity: Spinner
    private lateinit var switchTransparent: SwitchCompat
    private lateinit var switchWidgetAlmanac: SwitchCompat
    private var spinnerInitialized = false

    private val selectedDate: Calendar = Calendar.getInstance()

    private var availableRelease: ReleaseInfo? = null
    private var pendingInstallApk: File? = null
    private var isUpdating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Almanac views
        tvAlmanacSolar = findViewById(R.id.tv_almanac_solar)
        tvAlmanacLunar = findViewById(R.id.tv_almanac_lunar)
        tvAlmanacDayType = findViewById(R.id.tv_almanac_day_type)
        tvAlmanacHoliday = findViewById(R.id.tv_almanac_holiday)
        tvAlmanacProverb = findViewById(R.id.tv_almanac_proverb)
        tvAlmanacDetails = findViewById(R.id.tv_almanac_details)
        tvUpcomingHolidays = findViewById(R.id.tv_upcoming_holidays)

        val btnPrevDay: Button = findViewById(R.id.btn_prev_day)
        val btnPickDate: Button = findViewById(R.id.btn_pick_date)
        val btnToday: Button = findViewById(R.id.btn_today)
        val btnNextDay: Button = findViewById(R.id.btn_next_day)

        btnPrevDay.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, -1)
            renderAlmanacForSelectedDate()
        }
        btnNextDay.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, 1)
            renderAlmanacForSelectedDate()
        }
        btnToday.setOnClickListener {
            val now = Calendar.getInstance()
            selectedDate.set(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
            )
            renderAlmanacForSelectedDate()
        }
        btnPickDate.setOnClickListener { openDatePicker() }

        // Existing status, settings & update views
        tvStatus = findViewById(R.id.tv_status)
        tvUpdateStatus = findViewById(R.id.tv_update_status)
        btnUpdateAction = findViewById(R.id.btn_update_action)
        spinnerCity = findViewById(R.id.spinner_city)
        switchTransparent = findViewById(R.id.switch_transparent)
        switchWidgetAlmanac = findViewById(R.id.switch_widget_almanac)
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

        // Show Almanac / Holiday / Proverb on Widget toggle
        switchWidgetAlmanac.isChecked = LunarAlmanac.isShowExtraOnWidget(this)
        switchWidgetAlmanac.setOnCheckedChangeListener { _, isChecked ->
            LunarAlmanac.setShowExtraOnWidget(this, isChecked)
            LichAmWidgetProvider.updateAllWidgets(this)
        }

        btnGps.setOnClickListener { requestGpsLocation() }
        btnRefresh.setOnClickListener { refreshWeatherNow() }

        btnUpdateAction.setOnClickListener {
            val release = availableRelease
            if (release != null) {
                startDownloadAndInstall(release)
            } else {
                checkForUpdates(manual = true)
            }
        }

        renderAlmanacForSelectedDate()
        renderUpcomingHolidays()
        updateStatusText()
        WeatherUpdateWorker.schedulePeriodic(this)
        checkForUpdates(manual = false)
    }

    override fun onResume() {
        super.onResume()
        val apk = pendingInstallApk
        if (apk != null && apk.exists() && AppUpdater.canInstallPackages(this)) {
            pendingInstallApk = null
            AppUpdater.launchInstaller(this, apk)
        }
    }

    private fun openDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                renderAlmanacForSelectedDate()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun renderAlmanacForSelectedDate() {
        val d = selectedDate.get(Calendar.DAY_OF_MONTH)
        val m = selectedDate.get(Calendar.MONTH) + 1
        val y = selectedDate.get(Calendar.YEAR)
        val dowStr = vietnameseWeekdayFull(selectedDate.get(Calendar.DAY_OF_WEEK))
        val info = LunarAlmanac.getAlmanac(d, m, y)

        tvAlmanacSolar.text = "$dowStr, %02d/%02d/%d".format(d, m, y)
        val leapTag = if (info.isLeapMonth) " (Nhuận)" else ""
        tvAlmanacLunar.text =
            "Âm lịch: %02d/%02d/%d$leapTag\nNgày ${info.dayCanChi} • Tháng ${info.monthCanChi} • Năm ${info.yearCanChi}"
                .format(info.lunarDay, info.lunarMonth, info.lunarYear)

        if (info.isHoangDao) {
            tvAlmanacDayType.text = "✦ NGÀY HOÀNG ĐẠO — ${info.dayStarName}"
            tvAlmanacDayType.setBackgroundResource(R.drawable.bg_badge_good)
            tvAlmanacDayType.setTextColor(Color.parseColor("#1B5E20"))
        } else {
            tvAlmanacDayType.text = "☾ NGÀY HẮC ĐẠO — ${info.dayStarName}"
            tvAlmanacDayType.setBackgroundResource(R.drawable.bg_badge_bad)
            tvAlmanacDayType.setTextColor(Color.parseColor("#B45309"))
        }

        val holidayParts = ArrayList<String>()
        info.lunarHoliday?.let { holidayParts.add("★ $it (Âm lịch)") }
        if (info.lunarHoliday == null && info.lunarDay == 1) {
            holidayParts.add("★ Ngày Mùng 1 (Sóc) tháng ${info.lunarMonth} ÂL")
        } else if (info.lunarHoliday == null && info.lunarDay == 15) {
            holidayParts.add("★ Ngày Rằm (Vọng) tháng ${info.lunarMonth} ÂL")
        }
        info.solarHoliday?.let { holidayParts.add("• $it (Dương lịch)") }

        if (holidayParts.isNotEmpty()) {
            tvAlmanacHoliday.visibility = View.VISIBLE
            tvAlmanacHoliday.text = holidayParts.joinToString("\n")
        } else {
            tvAlmanacHoliday.visibility = View.GONE
        }

        tvAlmanacProverb.text = "“${info.proverb}”"

        val sb = StringBuilder()
        sb.append("<b>• Đánh giá chung:</b> ${info.overallRating}<br/>")
        if (info.badDayWarnings.isNotEmpty()) {
            sb.append("<b>• <font color='#C62828'>Lưu ý ngày kỵ:</font></b> ${info.badDayWarnings.joinToString("; ")}<br/>")
        }
        sb.append("<b>• Ngũ hành ngày:</b> ${info.napAm} &nbsp;|&nbsp; <b>Tiết khí:</b> ${info.solarTerm}<br/>")
        sb.append("<b>• Giờ Hoàng Đạo:</b> ${info.hoangDaoHours}<br/>")
        sb.append("<b>• Trực ${info.trucName} (${info.trucQuality}):</b> ${info.trucDesc}<br/>")
        val saoTag = if (info.sao28Good) "Tốt" else "Xấu"
        sb.append("<b>• Sao ${info.sao28Name} ($saoTag):</b> ${info.sao28Desc}<br/>")
        sb.append("<b>• Hướng xuất hành:</b> Hỷ Thần (<b>${info.hyThanDir}</b>) • Tài Thần (<b>${info.taiThanDir}</b>)<br/>")
        sb.append("<b>• Tuổi xung khắc:</b> ${info.conflictingAges}<br/>")
        sb.append("<b>• <font color='#2E7D32'>Việc nên làm:</font></b> ${info.shouldDo}<br/>")
        sb.append("<b>• <font color='#B45309'>Việc kiêng kỵ:</font></b> ${info.shouldAvoid}")

        tvAlmanacDetails.text = HtmlCompat.fromHtml(sb.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun renderUpcomingHolidays() {
        val now = Calendar.getInstance()
        val list = LunarAlmanac.getUpcomingLunarHolidays(now, limit = 6)
        if (list.isEmpty()) {
            tvUpcomingHolidays.text = "Không có dữ liệu ngày lễ."
            return
        }
        val sb = StringBuilder()
        for ((idx, item) in list.withIndex()) {
            if (idx > 0) sb.append("<br/>")
            val star = if (item.isMajor) "<font color='#C62828'>★</font>" else "<font color='#B45309'>•</font>"
            sb.append("$star <b>${item.name}</b> (${item.lunarLabel})<br/>")
            sb.append("&nbsp;&nbsp;&nbsp;Dương lịch: ${item.solarLabel} — còn <b>${item.daysLeft} ngày</b>")
        }
        tvUpcomingHolidays.text = HtmlCompat.fromHtml(sb.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun vietnameseWeekdayFull(dow: Int): String = when (dow) {
        Calendar.MONDAY -> "Thứ Hai"
        Calendar.TUESDAY -> "Thứ Ba"
        Calendar.WEDNESDAY -> "Thứ Tư"
        Calendar.THURSDAY -> "Thứ Năm"
        Calendar.FRIDAY -> "Thứ Sáu"
        Calendar.SATURDAY -> "Thứ Bảy"
        else -> "Chủ Nhật"
    }

    private fun checkForUpdates(manual: Boolean) {
        if (isUpdating) return
        val currentVer = AppUpdater.getCurrentVersionName(this)
        tvUpdateStatus.text = "Phiên bản hiện tại: v$currentVer\nĐang kiểm tra bản cập nhật mới..."
        btnUpdateAction.isEnabled = false

        Thread {
            val latest = AppUpdater.fetchLatestRelease()
            runOnUiThread {
                btnUpdateAction.isEnabled = true
                if (latest == null) {
                    availableRelease = null
                    tvUpdateStatus.text = "Phiên bản hiện tại: v$currentVer\nChưa kiểm tra được bản mới (cần kết nối mạng)."
                    btnUpdateAction.text = "Kiểm tra bản cập nhật"
                    btnUpdateAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3E454C"))
                    if (manual) {
                        Toast.makeText(this, "Không thể kết nối GitHub để kiểm tra cập nhật.", Toast.LENGTH_SHORT).show()
                    }
                } else if (AppUpdater.isNewerVersion(latest.versionName, currentVer)) {
                    availableRelease = latest
                    tvUpdateStatus.text = "Phiên bản hiện tại: v$currentVer\nCó bản mới: v${latest.versionName} (cài đè trực tiếp, không mất widget)."
                    btnUpdateAction.text = "Tải & Cài đè v${latest.versionName} ngay"
                    btnUpdateAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                } else {
                    availableRelease = null
                    tvUpdateStatus.text = "Phiên bản hiện tại: v$currentVer\nBạn đang dùng phiên bản mới nhất."
                    btnUpdateAction.text = "Kiểm tra bản cập nhật"
                    btnUpdateAction.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3E454C"))
                    if (manual) {
                        Toast.makeText(this, "Bạn đang dùng bản mới nhất (v$currentVer)!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun startDownloadAndInstall(release: ReleaseInfo) {
        if (isUpdating) return
        isUpdating = true
        btnUpdateAction.isEnabled = false
        btnUpdateAction.text = "Đang tải v${release.versionName}..."
        tvUpdateStatus.text = "Đang tải bản cập nhật v${release.versionName}... 0%"

        Thread {
            val apkFile = AppUpdater.downloadApk(applicationContext, release.downloadUrl) { pct ->
                runOnUiThread {
                    tvUpdateStatus.text = "Đang tải bản cập nhật v${release.versionName}... $pct%"
                }
            }
            runOnUiThread {
                isUpdating = false
                btnUpdateAction.isEnabled = true
                if (apkFile == null) {
                    tvUpdateStatus.text = "Tải bản cập nhật thất bại. Vui lòng kiểm tra mạng và thử lại."
                    btnUpdateAction.text = "Thử tải lại v${release.versionName}"
                    Toast.makeText(this, "Không tải được tệp cài đặt.", Toast.LENGTH_SHORT).show()
                } else {
                    tvUpdateStatus.text = "Đã tải xong v${release.versionName}. Đang mở trình cài đặt..."
                    btnUpdateAction.text = "Cài đặt v${release.versionName}"
                    if (!AppUpdater.canInstallPackages(this)) {
                        pendingInstallApk = apkFile
                        Toast.makeText(
                            this,
                            "Hãy bật 'Cho phép từ nguồn này' một lần duy nhất rồi bấm quay lại.",
                            Toast.LENGTH_LONG
                        ).show()
                        AppUpdater.openInstallPermissionSettings(this)
                    } else {
                        AppUpdater.launchInstaller(this, apkFile)
                    }
                }
            }
        }.start()
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
