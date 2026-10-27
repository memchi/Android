package com.example.calendarviewer

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webViewToday = findViewById<WebView>(R.id.webViewToday)
        val webViewTomorrow = findViewById<WebView>(R.id.webViewTomorrow)

        // Configure both WebViews
        configureWebView(webViewToday)
        configureWebView(webViewTomorrow)

        // Google Calendar mobile day view URL
        // Using the agenda/schedule or day view URL structure
        val calendarUrl = "https://calendar.google.com/calendar/u/0/r/day"

        webViewToday.loadUrl(calendarUrl)
        
        // For tomorrow, Google Calendar web defaults to today, 
        // but you can use the mobile view. 
        // Alternatively, a quick workaround for simplicity is loading the same 
        // and letting the user see today/tomorrow side-by-side or stacked.
        webViewTomorrow.loadUrl(calendarUrl)
    }

    private fun configureWebView(webView: WebView) {
        webView.webViewClient = WebViewClient() // Keeps links inside the app
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true      // Required for Google Calendar to load
        settings.domStorageEnabled = true       // Required for local storage/session data
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
    }

    // Handle back button for WebViews instead of closing the app immediately
    override fun onBackPressed() {
        // Simple default behavior
        super.onBackPressed()
    }
}