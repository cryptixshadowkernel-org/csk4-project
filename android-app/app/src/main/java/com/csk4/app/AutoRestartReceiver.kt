package com.csk4.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * ============================================
 * CSK4 PRO v4.0 - Auto Restart Receiver
 * ============================================
 * Ye receiver 4 situations mein service restart karta hai:
 * 1. Device reboot hone par
 * 2. Service kill hone par (AlarmManager se)
 * 3. Manual restart trigger par
 * 4. App update ke baad
 * 
 * Isse service 24/7 alive rehti hai.
 * ============================================
 */
class AutoRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CSK4_RESTART"
        private const val RESTART_DELAY = 1000L       // 1 second
        private const val RESTART_INTERVAL = 15 * 60 * 1000L  // 15 minutes
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        val action = intent?.action ?: return
        Log.d(TAG, "📡 Received action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "🔄 Device booted — starting service")
                startCommandService(context)
                scheduleAlarmRestart(context)
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "🔄 App updated — starting service")
                startCommandService(context)
                scheduleAlarmRestart(context)
            }

            Intent.ACTION_PACKAGE_RESTARTED -> {
                Log.d(TAG, "🔄 Package restarted — starting service")
                startCommandService(context)
            }

            "com.csk4.app.RESTART_SERVICE" -> {
                Log.d(TAG, "🔄 Manual restart — starting service")
                startCommandService(context)
                scheduleAlarmRestart(context)
            }

            "com.csk4.app.RESTART_NOW" -> {
                Log.d(TAG, "⚡ Restart NOW — service starting")
                startCommandService(context)
            }

            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "👤 User unlocked — checking service")
                if (!isServiceRunning(context)) {
                    Log.d(TAG, "⚠️ Service not running — restarting")
                    startCommandService(context)
                }
            }

            "android.intent.action.SCREEN_ON" -> {
                Log.d(TAG, "💡 Screen ON — checking service")
                if (!isServiceRunning(context)) {
                    startCommandService(context)
                }
            }

            "android.net.conn.CONNECTIVITY_CHANGE" -> {
                Log.d(TAG, "🌐 Network change — checking service")
                if (!isServiceRunning(context)) {
                    startCommandService(context)
                }
            }

            else -> {
                Log.d(TAG, "⚠️ Unknown action: $action")
            }
        }
    }

    // ============ START COMMAND SERVICE ============
    private fun startCommandService(context: Context) {
        try {
            val serviceIntent = Intent(context, CommandService::class.java)
            serviceIntent.setPackage(context.packageName)

            if (Build.VERSION.SDK_INT >= 26) {
                // Android 8+ ke liye foreground service
                context.startForegroundService(serviceIntent)
                Log.d(TAG, "✅ Service started (foreground)")
            } else {
                context.startService(serviceIntent)
                Log.d(TAG, "✅ Service started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Start service failed: ${e.message}")
            
            // Retry after 5 seconds
            scheduleOneTimeRestart(context, 5000L)
        }
    }

    // ============ CHECK SERVICE RUNNING ============
    private fun isServiceRunning(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) 
                as android.app.ActivityManager
            val services = manager.getRunningServices(Int.MAX_VALUE)
            
            for (service in services) {
                if (service.service.className == CommandService::class.java.name) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Check service error: ${e.message}")
            false
        }
    }

    // ============ SCHEDULE ALARM RESTART (Repeating) ============
    private fun scheduleAlarmRestart(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) 
                as AlarmManager
            
            val intent = Intent(context, AutoRestartReceiver::class.java).apply {
                action = "com.csk4.app.RESTART_SERVICE"
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                Config.RESTART_ALARM_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Repeating alarm — every 15 minutes
            val triggerTime = SystemClock.elapsedRealtime() + RESTART_INTERVAL
            
            if (Build.VERSION.SDK_INT >= 23) {
                // Android 6+ — setExactAndAllowWhileIdle (better for Doze mode)
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= 19) {
                // Android 4.4+ — setExact (precise)
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // Older — setRepeating
                alarmManager.setRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    RESTART_INTERVAL,
                    pendingIntent
                )
            }

            Log.d(TAG, "⏰ Alarm scheduled in 15 minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Schedule alarm failed: ${e.message}")
        }
    }

    // ============ SCHEDULE ONE-TIME RESTART ============
    private fun scheduleOneTimeRestart(context: Context, delayMs: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) 
                as AlarmManager
            
            val intent = Intent(context, AutoRestartReceiver::class.java).apply {
                action = "com.csk4.app.RESTART_NOW"
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (Config.RESTART_ALARM_CODE + 1),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = SystemClock.elapsedRealtime() + delayMs

            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            Log.d(TAG, "⏰ One-time restart in ${delayMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "One-time restart failed: ${e.message}")
        }
    }

    // ============ CANCEL ALL RESTARTS ============
    companion object Helper {
        fun cancelAllRestarts(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) 
                    as AlarmManager
                
                val intent = Intent(context, AutoRestartReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    Config.RESTART_ALARM_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                alarmManager.cancel(pendingIntent)
                Log.d(TAG, "✅ All restarts cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Cancel failed: ${e.message}")
            }
        }

        fun forceRestart(context: Context) {
            try {
                val intent = Intent(context, AutoRestartReceiver::class.java).apply {
                    action = "com.csk4.app.RESTART_SERVICE"
                }
                context.sendBroadcast(intent)
                Log.d(TAG, "✅ Force restart triggered")
            } catch (e: Exception) {
                Log.e(TAG, "Force restart failed: ${e.message}")
            }
        }
    }
}
