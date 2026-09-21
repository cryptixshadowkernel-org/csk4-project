package com.csk4.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File

class VideoRecorder(private val ctx: Context) {

    private val TAG = "CSK4_VID"

    fun recordVideo(frontCamera: Boolean, durationSec: Int, onResult: (File?, String?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            onResult(null, "no_perm"); return
        }

        val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val targetFacing = if (frontCamera)
            CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK

        var cameraId: String? = null
        for (id in manager.cameraIdList) {
            if (manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == targetFacing) {
                cameraId = id; break
            }
        }
        if (cameraId == null) cameraId = manager.cameraIdList.firstOrNull()
        if (cameraId == null) { onResult(null, "no_camera"); return }

        val bgThread = HandlerThread("CSK4Video").also { it.start() }
        val bgHandler = Handler(bgThread.looper)

        val file = File(
            ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "video_${System.currentTimeMillis()}.mp4"
        )

        try {
            val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            val chars = manager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.getOutputSizes(MediaRecorder::class.java)?.firstOrNull()
                ?: android.util.Size(1280, 720)

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoSize(size.width, size.height)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(5_000_000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()

            val surface = recorder.surface

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        req.addTarget(surface)

                        camera.createCaptureSession(listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        session.setRepeatingRequest(req.build(), null, bgHandler)
                                        recorder.start()
                                        Log.d(TAG, "Recording started")

                                        Handler(bgHandler.looper).postDelayed({
                                            try {
                                                recorder.stop(); recorder.release()
                                                camera.close()
                                                onResult(file, null)
                                            } catch (e: Exception) {
                                                onResult(null, "stop_fail: ${e.message}")
                                            }
                                        }, durationSec * 1000L)
                                    } catch (e: Exception) {
                                        onResult(null, "start_fail: ${e.message}")
                                    }
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    onResult(null, "config_fail"); camera.close()
                                }
                            }, bgHandler)
                    } catch (e: Exception) {
                        onResult(null, "req_fail: ${e.message}"); camera.close()
                    }
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close(); onResult(null, "disc")
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); onResult(null, "err_$error")
                }
            }, bgHandler)

        } catch (e: Exception) {
            onResult(null, "exception: ${e.message}")
        }
    }
}