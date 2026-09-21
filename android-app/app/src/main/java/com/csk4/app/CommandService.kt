package com.csk4.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class CommandService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var deviceId: String
    private lateinit var engine: CommandHandler

    private val poller = object : Runnable {
        override fun run() {
            pollCommands()
            handler.postDelayed(this, Config.COMMAND_POLL_INTERVAL)
        }
    }

    private val hb = object : Runnable {
        override fun run() {
            heartbeat()
            handler.postDelayed(this, Config.HEARTBEAT_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        engine = CommandHandler(this, deviceId)
        startForeground(1, notif())
        handler.post(poller)
        handler.post(hb)
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        handler.removeCallbacks(hb)
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    private fun pollCommands() {
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
        Volley.newRequestQueue(this).add(JsonObjectRequest(
            Request.Method.POST, "${Config.SERVER_URL}/api/device/command-done",
            JSONObject().apply { put("commandId", id); put("result", r) },
            null, null
        ))
    }

    private fun heartbeat() {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val b = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
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

    private fun notif(): Notification {
        val ch = "csk4_svc"
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(ch, "CSK4 Service", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(c)
        }
        return NotificationCompat.Builder(this, ch)
            .setContentTitle("CSK4 Active")
            .setContentText("Listening for commands")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }
}