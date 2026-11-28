package com.licham.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.view.View
import android.widget.RemoteViews
import androidx.core.text.HtmlCompat
import java.util.Calendar
import java.util.Locale

class LichAmWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherUpdateWorker.schedulePeriodic(context)
        WeatherUpdateWorker.enqueueNow(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WeatherUpdateWorker.schedulePeriodic(context)
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
        val elapsed = System.currentTimeMillis() - WidgetPrefs.getLastUpdated(context)
        if (WidgetPrefs.getCachedWeatherJson(context) == null || elapsed >= 25 * 60 * 1000L) {
            WeatherUpdateWorker.enqueueNow(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                updateAllWidgets(context)
                WeatherUpdateWorker.enqueueNow(context)
            }
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                WeatherUpdateWorker.schedulePeriodic(context)
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.licham.widget.ACTION_REFRESH"

        private val WEATHER_FRAME_IDS = intArrayOf(
            R.id.iv_weather_f0,
            R.id.iv_weather_f1,
            R.id.iv_weather_f2,
            R.id.iv_weather_f3,
            R.id.iv_weather_f4,
            R.id.iv_weather_f5
        )

        private val WEEK_COL_IDS = intArrayOf(
            R.id.col_day_0, R.id.col_day_1, R.id.col_day_2, R.id.col_day_3,
            R.id.col_day_4, R.id.col_day_5, R.id.col_day_6
        )
        private val WEEK_LABEL_IDS = intArrayOf(
            R.id.tv_dow_0, R.id.tv_dow_1, R.id.tv_dow_2, R.id.tv_dow_3,
            R.id.tv_dow_4, R.id.tv_dow_5, R.id.tv_dow_6
        )
        private val WEEK_SOLAR_IDS = intArrayOf(
            R.id.tv_solar_0, R.id.tv_solar_1, R.id.tv_solar_2, R.id.tv_solar_3,
            R.id.tv_solar_4, R.id.tv_solar_5, R.id.tv_solar_6
        )
        private val WEEK_LUNAR_IDS = intArrayOf(
            R.id.tv_lunar_0, R.id.tv_lunar_1, R.id.tv_lunar_2, R.id.tv_lunar_3,
            R.id.tv_lunar_4, R.id.tv_lunar_5, R.id.tv_lunar_6
        )

        private val FORECAST_DAY_IDS = intArrayOf(
            R.id.tv_fc_day_0, R.id.tv_fc_day_1, R.id.tv_fc_day_2, R.id.tv_fc_day_3, R.id.tv_fc_day_4
        )
        private val FORECAST_ICON_IDS = intArrayOf(
            R.id.iv_fc_icon_0, R.id.iv_fc_icon_1, R.id.iv_fc_icon_2, R.id.iv_fc_icon_3, R.id.iv_fc_icon_4
        )
        private val FORECAST_TEMP_IDS = intArrayOf(
            R.id.tv_fc_temp_0, R.id.tv_fc_temp_1, R.id.tv_fc_temp_2, R.id.tv_fc_temp_3, R.id.tv_fc_temp_4
        )

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, LichAmWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun setAnimatedWeatherFrames(views: RemoteViews, weatherCode: Int) {
            val frames = WeatherRepository.weatherAnimFrames(weatherCode)
            for (i in WEATHER_FRAME_IDS.indices) {
                views.setImageViewResource(WEATHER_FRAME_IDS[i], frames[i % frames.size])
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_lich_am)

            val bgRes = if (WidgetPrefs.isTransparentBg(context)) {
                R.drawable.bg_widget_transparent
            } else {
                R.drawable.bg_widget_cream
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val greeting = when {
                hour in 5..10 -> "Chào buổi sáng"
                hour in 11..13 -> "Chào buổi trưa"
                hour in 14..17 -> "Chào buổi chiều"
                else -> "Chào buổi tối"
            }
            views.setTextViewText(R.id.tv_greeting, greeting)

            val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
            val month = now.get(Calendar.MONTH) + 1
            val year = now.get(Calendar.YEAR)
            val dowName = vietnameseWeekdayFull(now.get(Calendar.DAY_OF_WEEK))
            views.setTextViewText(R.id.tv_solar_date, "$dowName, $dayOfMonth Thg $month")

            val todayLunar = LunarCalendar.convertSolar2Lunar(dayOfMonth, month, year)
            views.setTextViewText(R.id.tv_lunar_badge, todayLunar.badgeLabel())

            // Full Rich Almanac, Lunar Holiday & Daily Proverb on Widget Footer
            val almanac = LunarAlmanac.getAlmanac(dayOfMonth, month, year)
            if (LunarAlmanac.isShowExtraOnWidget(context)) {
                views.setViewVisibility(R.id.layout_almanac_footer, View.VISIBLE)

                views.setTextViewText(
                    R.id.tv_widget_almanac,
                    LunarAlmanac.getWidgetTitleLine(almanac)
                )
                val titleColor = when {
                    almanac.lunarHoliday != null || almanac.lunarDay == 1 || almanac.lunarDay == 15 -> Color.parseColor("#C62828")
                    almanac.isHoangDao -> Color.parseColor("#1B5E20")
                    else -> Color.parseColor("#B45309")
                }
                views.setTextColor(R.id.tv_widget_almanac, titleColor)

                views.setTextViewText(
                    R.id.tv_widget_almanac_meta,
                    LunarAlmanac.getWidgetMetaLine(almanac, now)
                )
                views.setTextViewText(
                    R.id.tv_widget_almanac_hours,
                    LunarAlmanac.getWidgetHoursLine(almanac)
                )
                views.setTextViewText(
                    R.id.tv_widget_almanac_advice,
                    LunarAlmanac.getWidgetAdviceLine(almanac)
                )
                val adviceColor = if (almanac.badDayWarnings.isNotEmpty()) {
                    Color.parseColor("#B71C1C")
                } else {
                    Color.parseColor("#2C3034")
                }
                views.setTextColor(R.id.tv_widget_almanac_advice, adviceColor)

                // Bind upcoming Lunar Holidays & Rằm / Mùng 1 right on widget
                val upcoming = LunarAlmanac.getUpcomingLunarHolidays(now, limit = 2)
                if (upcoming.isNotEmpty()) {
                    views.setViewVisibility(R.id.layout_widget_holidays, View.VISIBLE)
                    val first = upcoming[0]
                    val prefix0 = if (first.isMajor) "★ " else "• "
                    views.setTextViewText(
                        R.id.tv_widget_hol_name_0,
                        "$prefix0${first.name} (${first.lunarLabel})"
                    )
                    views.setTextColor(
                        R.id.tv_widget_hol_name_0,
                        if (first.isMajor) Color.parseColor("#B71C1C") else Color.parseColor("#1E2328")
                    )
                    views.setTextViewText(
                        R.id.tv_widget_hol_date_0,
                        HtmlCompat.fromHtml(
                            "Dương lịch: ${first.solarLabel} - <b>còn ${first.daysLeft} ngày</b>",
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    )

                    if (upcoming.size >= 2) {
                        val second = upcoming[1]
                        val prefix1 = if (second.isMajor) "★ " else "• "
                        views.setViewVisibility(R.id.divider_widget_hol, View.VISIBLE)
                        views.setViewVisibility(R.id.col_widget_hol_1, View.VISIBLE)
                        views.setTextViewText(
                            R.id.tv_widget_hol_name_1,
                            "$prefix1${second.name} (${second.lunarLabel})"
                        )
                        views.setTextColor(
                            R.id.tv_widget_hol_name_1,
                            if (second.isMajor) Color.parseColor("#B71C1C") else Color.parseColor("#1E2328")
                        )
                        views.setTextViewText(
                            R.id.tv_widget_hol_date_1,
                            HtmlCompat.fromHtml(
                                "Dương lịch: ${second.solarLabel} - <b>còn ${second.daysLeft} ngày</b>",
                                HtmlCompat.FROM_HTML_MODE_LEGACY
                            )
                        )
                    } else {
                        views.setViewVisibility(R.id.divider_widget_hol, View.GONE)
                        views.setViewVisibility(R.id.col_widget_hol_1, View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.layout_widget_holidays, View.GONE)
                }

                views.setTextViewText(R.id.tv_widget_proverb, "“${almanac.proverb}”")
            } else {
                views.setViewVisibility(R.id.layout_almanac_footer, View.GONE)
            }

            // Battery level & temperature
            val batteryStatus = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
            val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempC = tempTenths / 10.0
            views.setTextViewText(
                R.id.tv_battery,
                String.format(Locale.US, "%d%% • %.1f°C", pct, tempC)
            )

            // Current weather & 5-day forecast
            val weather = WeatherRepository.loadCached(context)
            val cityName = WidgetPrefs.getCityName(context)
            if (weather != null && weather.daily.isNotEmpty()) {
                views.setTextViewText(R.id.tv_temp_now, "${weather.currentTemp}°")
                views.setTextViewText(
                    R.id.tv_weather_cond,
                    WeatherRepository.conditionText(weather.currentCode)
                )
                setAnimatedWeatherFrames(views, weather.currentCode)
                val todayFc = weather.daily[0]
                views.setTextViewText(
                    R.id.tv_loc_range,
                    "$cityName • ↑${todayFc.maxTemp}° ↓${todayFc.minTemp}°"
                )

                for (i in 0 until 5) {
                    val fcCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                    val label = if (i == 0) "H.NAY" else vietnameseWeekdayUpper(fcCal.get(Calendar.DAY_OF_WEEK))
                    views.setTextViewText(FORECAST_DAY_IDS[i], label)
                    if (i < weather.daily.size) {
                        val fc = weather.daily[i]
                        views.setImageViewResource(
                            FORECAST_ICON_IDS[i],
                            WeatherRepository.weatherIconRes(fc.weatherCode)
                        )
                        views.setTextViewText(
                            FORECAST_TEMP_IDS[i],
                            "${fc.minTemp}°/${fc.maxTemp}°"
                        )
                    }
                }
            } else {
                views.setTextViewText(R.id.tv_temp_now, "--°")
                views.setTextViewText(R.id.tv_weather_cond, "Chạm để cập nhật")
                setAnimatedWeatherFrames(views, 3)
                views.setTextViewText(R.id.tv_loc_range, cityName)
                for (i in 0 until 5) {
                    val fcCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                    val label = if (i == 0) "H.NAY" else vietnameseWeekdayUpper(fcCal.get(Calendar.DAY_OF_WEEK))
                    views.setTextViewText(FORECAST_DAY_IDS[i], label)
                    views.setImageViewResource(FORECAST_ICON_IDS[i], R.drawable.ic_w3d_cloudy_f0)
                    views.setTextViewText(FORECAST_TEMP_IDS[i], "--°/--°")
                }
            }

            // 7-Day Week Calendar Strip (Monday -> Sunday) with Lunar Holiday highlight
            val mondayCal = (now.clone() as Calendar).apply {
                val dow = get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY)
                add(Calendar.DAY_OF_YEAR, -daysFromMonday)
            }
            val shortLabels = arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

            for (i in 0 until 7) {
                val cellCal = (mondayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                val cDay = cellCal.get(Calendar.DAY_OF_MONTH)
                val cMonth = cellCal.get(Calendar.MONTH) + 1
                val cYear = cellCal.get(Calendar.YEAR)
                val cLunar = LunarCalendar.convertSolar2Lunar(cDay, cMonth, cYear)
                val isToday = (cDay == dayOfMonth && cMonth == month && cYear == year)
                val isSpecialLunar = LunarAlmanac.isLunarSpecialDay(cDay, cMonth, cYear)

                views.setTextViewText(WEEK_LABEL_IDS[i], shortLabels[i])
                views.setTextViewText(WEEK_SOLAR_IDS[i], "$cDay")
                views.setTextViewText(WEEK_LUNAR_IDS[i], cLunar.shortCellLabel())

                if (isToday) {
                    views.setInt(WEEK_COL_IDS[i], "setBackgroundResource", R.drawable.bg_day_today)
                    views.setTextColor(WEEK_LABEL_IDS[i], Color.parseColor("#FFE8E8"))
                    views.setTextColor(WEEK_SOLAR_IDS[i], Color.WHITE)
                    views.setTextColor(WEEK_LUNAR_IDS[i], Color.parseColor("#FFE0E0"))
                } else {
                    views.setInt(WEEK_COL_IDS[i], "setBackgroundResource", R.drawable.bg_widget_transparent)
                    views.setTextColor(WEEK_LABEL_IDS[i], Color.parseColor("#787670"))
                    views.setTextColor(WEEK_SOLAR_IDS[i], Color.parseColor("#1E2328"))
                    // Đánh dấu đỏ đậm cho ngày Lễ Âm lịch, Mùng 1 và Rằm
                    val lunarColor = if (isSpecialLunar) Color.parseColor("#C62828") else Color.parseColor("#8A8882")
                    views.setTextColor(WEEK_LUNAR_IDS[i], lunarColor)
                }
            }

            // Click handlers: tap clock / week strip / almanac footer -> open full Lich Van Nien & Settings
            val settingsIntent = Intent(context, MainActivity::class.java)
            val settingsPending = PendingIntent.getActivity(
                context,
                100,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.layout_top_left, settingsPending)
            views.setOnClickPendingIntent(R.id.layout_week_row, settingsPending)
            views.setOnClickPendingIntent(R.id.layout_almanac_footer, settingsPending)

            // Tap weather / forecast -> quick refresh
            val refreshIntent = Intent(context, LichAmWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                101,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.layout_top_right, refreshPending)
            views.setOnClickPendingIntent(R.id.layout_forecast_row, refreshPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
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

        private fun vietnameseWeekdayUpper(dow: Int): String = when (dow) {
            Calendar.MONDAY -> "THỨ 2"
            Calendar.TUESDAY -> "THỨ 3"
            Calendar.WEDNESDAY -> "THỨ 4"
            Calendar.THURSDAY -> "THỨ 5"
            Calendar.FRIDAY -> "THỨ 6"
            Calendar.SATURDAY -> "THỨ 7"
            else -> "CN"
        }
    }
}
