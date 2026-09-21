package com.csk4.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl(Config.ADMIN_PANEL_URL)
    }

    override fun onBackPressed() {
        val webView = findViewById<WebView>(R.id.webView)
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}