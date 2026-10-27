package com.example.calendarviewer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_CALENDAR = 100
    private val handler = Handler(Looper.getMainLooper())
    
    private val midnightRunnable = Runnable {
        checkCalendarPermission()
        scheduleMidnightUpdate()
    }

    private val brightnessRunnable = Runnable {
        updateBrightnessBasedOnTime()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkCalendarPermission()
        updateBrightnessBasedOnTime()
    }

    override fun onResume() {
        super.onResume()
        checkCalendarPermission()
        scheduleMidnightUpdate()
        updateBrightnessBasedOnTime()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(midnightRunnable)
        handler.removeCallbacks(brightnessRunnable)
    }

    private fun scheduleMidnightUpdate() {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = midnight.timeInMillis - now.timeInMillis
        handler.removeCallbacks(midnightRunnable)
        handler.postDelayed(midnightRunnable, delay)
    }

    private fun updateBrightnessBasedOnTime() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute

        // 22:00 = 1320 minutes, 07:00 = 420 minutes
        val isNight = currentMinutes >= 1320 || currentMinutes < 420

        val layoutParams = window.attributes
        layoutParams.screenBrightness = if (isNight) 0.2f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = layoutParams

        scheduleNextBrightnessUpdate()
    }

    private fun scheduleNextBrightnessUpdate() {
        val now = Calendar.getInstance()
        val target07 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val target22 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val nowMillis = now.timeInMillis
        val candidates = listOf(
            target07.timeInMillis,
            target22.timeInMillis,
            target07.clone().let { (it as Calendar).add(Calendar.DAY_OF_YEAR, 1); it.timeInMillis },
            target22.clone().let { (it as Calendar).add(Calendar.DAY_OF_YEAR, 1); it.timeInMillis }
        ).filter { it > nowMillis }

        val nextTarget = candidates.minOrNull() ?: (nowMillis + 60000)
        val delay = nextTarget - nowMillis

        handler.removeCallbacks(brightnessRunnable)
        handler.postDelayed(brightnessRunnable, delay)
    }

    private fun checkCalendarPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CALENDAR),
                REQUEST_CODE_CALENDAR
            )
        } else {
            loadCalendarData()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_CALENDAR && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadCalendarData()
            scheduleMidnightUpdate()
            updateBrightnessBasedOnTime()
        } else {
            findViewById<TextView>(R.id.textTodayEvents).text = "Berechtigung verweigert"
        }
    }

    private fun loadCalendarData() {
        val todayText = findViewById<TextView>(R.id.textTodayEvents)
        val tomorrowText = findViewById<TextView>(R.id.textTomorrowEvents)

        val calendar = Calendar.getInstance()
        
        // Start of Today (00:00:00)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startToday = calendar.timeInMillis

        // Start of Tomorrow
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val startTomorrow = calendar.timeInMillis

        // End of Tomorrow (23:59:59)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endTomorrow = calendar.timeInMillis

        todayText.text = getEventsForInterval(startToday, startTomorrow)
        tomorrowText.text = getEventsForInterval(startTomorrow, endTomorrow)
    }

    private fun getEventsForInterval(startTime: Long, endTime: Long): String {
        val builder = StringBuilder()
        
        // Using CalendarContract.Instances to properly capture synced Google Calendar entries & recurring events
        val builderUri: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builderUri, startTime)
        ContentUris.appendId(builderUri, endTime)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY
        )

        val cursor: Cursor? = contentResolver.query(
            builderUri.build(),
            projection,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )

        cursor?.use {
            val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val startIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)
            val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            if (it.count == 0) {
                return "Keine Termine"
            }

            while (it.moveToNext()) {
                val title = it.getString(titleIdx) ?: "Kein Titel"
                val start = it.getLong(startIdx)
                val isAllDay = it.getInt(allDayIdx) == 1

                val timeStr = if (isAllDay) "Ganztägig" else timeFormat.format(Date(start))
                builder.append("$timeStr  $title\n\n")
            }
        }

        return if (builder.isNotEmpty()) builder.toString().trim() else "Keine Termine"
    }
}
