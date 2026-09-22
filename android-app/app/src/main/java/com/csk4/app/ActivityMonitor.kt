package com.csk4.app

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ActivityMonitor — Tracks every phone activity
 * 
 * Features:
 * - Screen ON/OFF events
 * - Battery changes
 * - App opens
 * - Charging events
 * - Unlock events
 * - Sends email alerts for important activities
 * - Auto-saves to local DB
 */
class ActivityMonitor(private val ctx: Context) {

    companion object {
        private const val TAG = "CSK4_ACTIVITY"
        private const val CHECK_INTERVAL = 60 * 1000L  // 1 minute
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastAppPackage = ""
    private var lastBatteryLevel = -1
    private var lastChargingState = false
    private var lastScreenState = true

    // ============ START MONITORING ============
    fun start() {
        if (isRunning) return
        isRunning = true

        try {
            // Register screen state receiver
            registerScreenReceiver()

            // Register battery receiver
            registerBatteryReceiver()

            // Start periodic app check
            handler.post(appChecker)

            // Log initial state
            logInitialState()

            Log.d(TAG, "✅ Activity monitor started")
        } catch (e: Exception) {
            Log.e(TAG, "Start fail: ${e.message}")
        }
    }

    // ============ STOP MONITORING ============
    fun stop() {
        isRunning = false
        try {
            handler.removeCallbacks(appChecker)
            unregisterScreenReceiver()
            unregisterBatteryReceiver()
            Log.d(TAG, "Activity monitor stopped")
        } catch (e: Exception) {}
    }

    // ============ PERIODIC APP CHECK ============
    private val appChecker = object : Runnable {
        override fun run() {
            try {
                checkCurrentApp()
                checkScreenState()
                checkBatteryState()
            } catch (e: Exception) {
                Log.e(TAG, "Check: ${e.message}")
            }
            handler.postDelayed(this, CHECK_INTERVAL)
        }
    }

    // ============ CHECK CURRENT APP ============
    private fun checkCurrentApp() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

            val usageStatsManager = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 60000  // Last 1 min

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

            if (stats.isNullOrEmpty()) return

            // Find most recent app
            val recent = stats.maxByOrNull { it.lastTimeUsed } ?: return
            val pkg = recent.packageName ?: return

            if (pkg != lastAppPackage && pkg != ctx.packageName) {
                lastAppPackage = pkg
                val appName = getAppName(pkg)
                logActivity("app_open", JSONObject().apply {
                    put("package", pkg)
                    put("name", appName)
                })
                Log.d(TAG, "📱 App opened: $appName")
            }
        } catch (e: Exception) {}
    }

    // ============ CHECK SCREEN STATE ============
    private fun checkScreenState() {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                pm.isInteractive
            } else {
                @Suppress("DEPRECATION")
                pm.isScreenOn
            }

            if (isOn != lastScreenState) {
                lastScreenState = isOn
                val type = if (isOn) "screen_on" else "screen_off"
                logActivity(type, JSONObject().apply {
                    put("time", System.currentTimeMillis())
                })
                Log.d(TAG, "🖥️ Screen: $type")
            }
        } catch (e: Exception) {}
    }

    // ============ CHECK BATTERY STATE ============
    private fun checkBatteryState() {
        try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            if (level != lastBatteryLevel && (level % 5 == 0 || Math.abs(level - lastBatteryLevel) >= 5)) {
                lastBatteryLevel = level
                logActivity("battery_change", JSONObject().apply {
                    put("level", level)
                })
            }
        } catch (e: Exception) {}
    }

    // ============ SCREEN RECEIVER ============
    private var screenReceiver: BroadcastReceiver? = null

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        logActivity("screen_on", JSONObject())
                        Log.d(TAG, "💡 Screen ON")
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        logActivity("screen_off", JSONObject())
                        Log.d(TAG, "🌙 Screen OFF")
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        logActivity("unlock", JSONObject())
                        Log.d(TAG, "🔓 Unlocked")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ctx.registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenReceiver() {
        try { screenReceiver?.let { ctx.unregisterReceiver(it) } } catch (e: Exception) {}
    }

    // ============ BATTERY RECEIVER ============
    private var batteryReceiver: BroadcastReceiver? = null

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                when (action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                        val pct = level * 100 / scale

                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                        status == BatteryManager.BATTERY_STATUS_FULL

                        if (isCharging != lastChargingState) {
                            lastChargingState = isCharging
                            val type = if (isCharging) "charging_started" else "charging_stopped"
                            logActivity(type, JSONObject().apply {
                                put("level", pct)
                            })
                            Log.d(TAG, "🔌 Charging: $type ($pct%)")
                        }

                        // Low battery alert
                        if (pct <= 15 && (pct % 5 == 0)) {
                            logActivity("battery_low", JSONObject().apply {
                                put("level", pct)
                            })
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ctx.registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryReceiver() {
        try { batteryReceiver?.let { ctx.unregisterReceiver(it) } } catch (e: Exception) {}
    }

    // ============ LOG ACTIVITY ============
    private fun logActivity(type: String, data: JSONObject) {
        try {
            val deviceId = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ANDROID_ID)

            // 1. Save to local DB (offline support)
            LocalStorage(ctx).addActivity(type, data.toString())

            // 2. Upload to server immediately
            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("type", type)
                put("data", data)
                put("token", Config.DEVICE_TOKEN)
            }

            Volley.newRequestQueue(ctx).add(JsonObjectRequest(
                Request.Method.POST,
                "${Config.SERVER_URL}/api/device/activity",
                json,
                { Log.d(TAG, "✅ Activity: $type") },
                { Log.e(TAG, "❌ Activity fail: ${it.message}") }
            ))
        } catch (e: Exception) {
            Log.e(TAG, "logActivity: ${e.message}")
        }
    }

    // ============ GET APP NAME ============
    private fun getAppName(packageName: String): String {
        return try {
            val pm = ctx.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    // ============ INITIAL STATE LOG ============
    private fun logInitialState() {
        try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            logActivity("monitor_started", JSONObject().apply {
                put("battery", level)
                put("model", Build.MODEL)
                put("android", Build.VERSION.RELEASE)
                put("time", System.currentTimeMillis())
            })
        } catch (e: Exception) {}
    }
}
