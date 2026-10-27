package com.example.calendarviewer

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // This links the code to your XML layout file
        setContentView(R.layout.activity_main)

        val webViewToday = findViewById<WebView>(R.id.webViewToday)
        val webViewTomorrow = findViewById<WebView>(R.id.webViewTomorrow)

        // Configure both WebViews
        configureWebView(webViewToday)
        configureWebView(webViewTomorrow)

        // Google Calendar mobile day view URL
        val calendarUrl = "https://calendar.google.com/calendar/u/0/r/day"

        webViewToday.loadUrl(calendarUrl)
        webViewTomorrow.loadUrl(calendarUrl)
    }

    private fun configureWebView(webView: WebView) {
        webView.webViewClient = WebViewClient() // Keeps links inside the app
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true      // Required for Google Calendar to load
        settings.domStorageEnabled = true       // Required for session data
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
    }
}