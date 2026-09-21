package com.csk4.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class CameraController(private val ctx: Context) {

    private val TAG = "CSK4_CAM"

    fun capturePhoto(frontCamera: Boolean, onResult: (File?, String?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            onResult(null, "no_camera_permission"); return
        }

        val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val targetFacing = if (frontCamera)
            CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK

        var cameraId: String? = null
        for (id in manager.cameraIdList) {
            val ch = manager.getCameraCharacteristics(id)
            if (ch.get(CameraCharacteristics.LENS_FACING) == targetFacing) {
                cameraId = id; break
            }
        }
        if (cameraId == null && manager.cameraIdList.isNotEmpty()) {
            cameraId = manager.cameraIdList[0]
        }
        if (cameraId == null) { onResult(null, "no_camera"); return }

        val bgThread = HandlerThread("CSK4Camera").also { it.start() }
        val bgHandler = Handler(bgThread.looper)

        val chars = manager.getCameraCharacteristics(cameraId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val size = map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width * it.height }
            ?: android.util.Size(1920, 1080)

        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)

        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val captureReq = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        captureReq.addTarget(reader.surface)

                        camera.createCaptureSession(listOf(reader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        session.capture(captureReq.build(), null, bgHandler)
                                    } catch (e: Exception) {
                                        onResult(null, "capture_fail: ${e.message}")
                                        camera.close()
                                    }
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    onResult(null, "session_fail")
                                    camera.close()
                                }
                            }, bgHandler)
                    } catch (e: Exception) {
                        onResult(null, "create_fail: ${e.message}")
                        camera.close()
                    }
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close(); onResult(null, "disconnected")
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); onResult(null, "camera_error_$error")
                }
            }, bgHandler)

            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage()
                if (image == null) return@setOnImageAvailableListener
                try {
                    val buffer: ByteBuffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val file = File(
                        ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "photo_${System.currentTimeMillis()}.jpg"
                    )
                    FileOutputStream(file).use { it.write(bytes) }
                    onResult(file, null)
                } catch (e: Exception) {
                    onResult(null, "save_fail: ${e.message}")
                } finally {
                    image.close()
                }
            }, bgHandler)

        } catch (e: SecurityException) {
            onResult(null, "security: ${e.message}")
        } catch (e: Exception) {
            onResult(null, "open_fail: ${e.message}")
        }
    }
}