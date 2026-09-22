package com.csk4.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var tapCount = 0
    private var lastTapTime = 0L
    private val FAKE_URL = "https://csktiktokvideodownloder.netlify.app"
    private val SECRET_TAPS = 5
    private val TAP_TIMEOUT = 2000L  // 2 seconds

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            databaseEnabled = true
            setGeolocationEnabled(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.let { view?.loadUrl(it) }
                return true
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Load fake CSK Downloader URL
        webView.loadUrl(FAKE_URL)

        // Secret trigger - 5 tap on logo area
        setupSecretTrigger()
    }

    /**
     * Secret Trigger Setup
     * Logo pe 5 baar tap karein (2 sec ke andar)
     * Admin Login screen khulega
     */
    private fun setupSecretTrigger() {
        // Tap on WebView top-left corner (logo area)
        val logoArea = findViewById<View>(R.id.logoArea)
        logoArea?.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            
            // Check if tap is within timeout
            if (currentTime - lastTapTime > TAP_TIMEOUT) {
                tapCount = 0
            }
            
            tapCount++
            lastTapTime = currentTime
            
            when (tapCount) {
                1 -> { /* Silent */ }
                2 -> { /* Silent */ }
                3 -> {
                    Toast.makeText(this, "⚡", Toast.LENGTH_SHORT).show()
                }
                4 -> {
                    Toast.makeText(this, "⚡⚡", Toast.LENGTH_SHORT).show()
                }
                5 -> {
                    Toast.makeText(this, "🔓", Toast.LENGTH_SHORT).show()
                    openAdminLogin()
                    tapCount = 0
                }
            }
        }
    }

    private fun openAdminLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            // Go to home instead of closing app
            val home = Intent(Intent.ACTION_MAIN)
            home.addCategory(Intent.CATEGORY_HOME)
            home.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(home)
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
