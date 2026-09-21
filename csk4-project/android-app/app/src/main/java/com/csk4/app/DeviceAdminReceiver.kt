package com.csk4.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class DeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "CSK4 Device Admin enabled", Toast.LENGTH_SHORT).show()
    }
    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "CSK4 Device Admin disabled", Toast.LENGTH_SHORT).show()
    }
}