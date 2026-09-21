package com.csk4.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import java.io.File

class AudioRecorder(private val ctx: Context) {

    fun record(durationSec: Int, onResult: (File?, String?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            onResult(null, "no_audio_perm"); return
        }
        try {
            val file = File(ctx.getExternalFilesDir(null), "audio_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    recorder.stop(); recorder.release()
                    onResult(file, null)
                } catch (e: Exception) {
                    onResult(null, "stop_fail: ${e.message}")
                }
            }, durationSec * 1000L)
        } catch (e: Exception) {
            onResult(null, "audio_fail: ${e.message}")
        }
    }
}