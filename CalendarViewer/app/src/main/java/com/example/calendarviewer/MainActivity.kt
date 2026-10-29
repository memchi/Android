package com.example.calendarviewer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
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
        updateDateTimeHeader()
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateDateTimeHeader()
            handler.postDelayed(this, 60000)
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            requestCalendarSync()
            loadCalendarData()
            handler.postDelayed(this, 300000) // every 5 minutes
        }
    }

    private val calendarObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            loadCalendarData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateDateTimeHeader()
        checkCalendarPermission()
        requestCalendarSync()
    }

    override fun onResume() {
        super.onResume()
        updateDateTimeHeader()
        checkCalendarPermission()
        scheduleMidnightUpdate()
        requestCalendarSync()

        handler.removeCallbacks(clockRunnable)
        handler.post(clockRunnable)

        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, 300000)

        contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true,
            calendarObserver
        )
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(midnightRunnable)
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(refreshRunnable)
        contentResolver.unregisterContentObserver(calendarObserver)
    }

    private fun requestCalendarSync() {
        try {
            val accountManager = android.accounts.AccountManager.get(this)
            val accounts = accountManager.accounts
            val extras = Bundle().apply {
                putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            for (account in accounts) {
                if (android.content.ContentResolver.getIsSyncable(account, CalendarContract.AUTHORITY) > 0) {
                    android.content.ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateDateTimeHeader() {
        val now = Date()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.forLanguageTag("de-DE"))
        val restDateFormat = SimpleDateFormat(", d. MMMM yyyy", Locale.forLanguageTag("de-DE"))

        val timeStr = timeFormat.format(now)
        val dayStr = dayFormat.format(now)
        val restStr = restDateFormat.format(now)
        val fullDateStr = dayStr + restStr

        val spannable = SpannableString(fullDateStr)
        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, dayStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        findViewById<TextView>(R.id.textClock).text = timeStr
        findViewById<TextView>(R.id.textDate).text = spannable
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
            updateDateTimeHeader()
            requestCalendarSync()
        } else {
            findViewById<TextView>(R.id.textTodayEvents).text = "Berechtigung verweigert"
        }
    }

    private fun loadCalendarData() {
        val todayText = findViewById<TextView>(R.id.textTodayEvents)

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

        todayText.text = getEventsForInterval(startToday, startTomorrow)
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
