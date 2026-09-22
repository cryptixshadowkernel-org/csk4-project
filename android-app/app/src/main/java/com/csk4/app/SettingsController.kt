package com.csk4.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * SettingsController — Control device settings remotely
 * 
 * Features:
 * - Brightness (0-100)
 * - Volume (media, ring, notification, alarm)
 * - Ring mode (silent/vibrate/normal)
 * - Screen timeout
 * - Auto-rotate on/off
 * - WiFi/Bluetooth open settings
 * - Airplane mode open settings
 */
class SettingsController(private val ctx: Context) {

    companion object {
        private const val TAG = "CSK4_SETTINGS"
    }

    private val audioManager: AudioManager
        get() = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ============ BRIGHTNESS ============
    fun setBrightness(value: Int): Boolean {
        return try {
            val brightness = (value.coerceIn(0, 100) * 255 / 100)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(ctx)) {
                    // Request permission
                    requestWriteSettings()
                    return false
                }
            }
            
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness
            )
            
            Log.d(TAG, "✅ Brightness set to $value%")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Brightness: ${e.message}")
            false
        }
    }

    fun getBrightness(): Int {
        return try {
            val brightness = Settings.System.getInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            brightness * 100 / 255
        } catch (e: Exception) { 50 }
    }

    private fun requestWriteSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:${ctx.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Write settings request: ${e.message}")
        }
    }

    // ============ VOLUME ============
    fun setVolume(stream: Int, value: Int): Boolean {
        return try {
            val max = audioManager.getStreamMaxVolume(stream)
            val vol = (value.coerceIn(0, 100) * max / 100)
            audioManager.setStreamVolume(stream, vol, 0)
            Log.d(TAG, "✅ Volume stream=$stream set to $value%")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Volume: ${e.message}")
            false
        }
    }

    fun setMediaVolume(value: Int) = setVolume(AudioManager.STREAM_MUSIC, value)
    fun setRingVolume(value: Int) = setVolume(AudioManager.STREAM_RING, value)
    fun setNotificationVolume(value: Int) = setVolume(AudioManager.STREAM_NOTIFICATION, value)
    fun setAlarmVolume(value: Int) = setVolume(AudioManager.STREAM_ALARM, value)
    fun setCallVolume(value: Int) = setVolume(AudioManager.STREAM_VOICE_CALL, value)

    fun setAllVolumes(value: Int): Boolean {
        return try {
            setMediaVolume(value)
            setRingVolume(value)
            setNotificationVolume(value)
            setAlarmVolume(value)
            true
        } catch (e: Exception) { false }
    }

    fun getVolume(stream: Int): Int {
        return try {
            val current = audioManager.getStreamVolume(stream)
            val max = audioManager.getStreamMaxVolume(stream)
            current * 100 / max
        } catch (e: Exception) { 0 }
    }

    // ============ RING MODE ============
    fun setRingMode(mode: String): Boolean {
        return try {
            when (mode.lowercase()) {
                "silent", "mute" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                "vibrate", "vibration" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                "normal", "sound" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
                else -> return false
            }
            Log.d(TAG, "✅ Ring mode set: $mode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ring mode: ${e.message}")
            false
        }
    }

    fun getRingMode(): String {
        return try {
            when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                AudioManager.RINGER_MODE_NORMAL -> "normal"
                else -> "unknown"
            }
        } catch (e: Exception) { "unknown" }
    }

    // ============ SCREEN TIMEOUT ============
    fun setScreenTimeout(seconds: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(ctx)) {
                requestWriteSettings()
                return false
            }
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                seconds * 1000
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Timeout: ${e.message}")
            false
        }
    }

    // ============ AUTO-ROTATE ============
    fun setAutoRotate(enabled: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(ctx)) {
                requestWriteSettings()
                return false
            }
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Auto-rotate: ${e.message}")
            false
        }
    }

    // ============ WIFI ============
    fun isWifiEnabled(): Boolean {
        return try {
            val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.isWifiEnabled
        } catch (e: Exception) { false }
    }

    fun openWifiSettings() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Wifi settings: ${e.message}")
        }
    }

    // ============ BLUETOOTH ============
    fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "BT settings: ${e.message}")
        }
    }

    // ============ AIRPLANE ============
    fun openAirplaneModeSettings() {
        try {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Airplane settings: ${e.message}")
        }
    }

    // ============ LOCATION ============
    fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Location settings: ${e.message}")
        }
    }

    // ============ DND MODE ============
    fun setDndMode(enabled: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 23) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (nm.isNotificationPolicyAccessGranted) {
                    if (enabled) {
                        nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
                    } else {
                        nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                    true
                } else {
                    // Open DND access settings
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    ctx.startActivity(intent)
                    false
                }
            } else false
        } catch (e: Exception) { false }
    }

    // ============ GET ALL STATUS ============
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "brightness" to getBrightness(),
            "mediaVolume" to getVolume(AudioManager.STREAM_MUSIC),
            "ringVolume" to getVolume(AudioManager.STREAM_RING),
            "notificationVolume" to getVolume(AudioManager.STREAM_NOTIFICATION),
            "ringMode" to getRingMode(),
            "wifiEnabled" to isWifiEnabled()
        )
    }
}
