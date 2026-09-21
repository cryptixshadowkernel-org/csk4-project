package com.csk4.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class ScreenshotService : AccessibilityService() {

    companion object {
        var instance: ScreenshotService? = null
        private const val TAG = "CSK4_SS"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    @RequiresApi(Build.VERSION_CODES.R)
    fun takeScreenshot(onResult: (File?, String?) -> Unit) {
        takeScreenshot(Display.DEFAULT_DISPLAY, Executors.newSingleThreadExecutor(),
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val sw = Bitmap.createBitmap(bitmap!!)
                        result.hardwareBuffer.close()

                        val file = File(
                            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            "screenshot_${System.currentTimeMillis()}.png"
                        )
                        FileOutputStream(file).use {
                            sw.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        onResult(file, null)
                    } catch (e: Exception) {
                        onResult(null, "save_fail: ${e.message}")
                    }
                }
                override fun onFailure(errorCode: Int) {
                    onResult(null, "screenshot_fail_$errorCode")
                }
            })
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}