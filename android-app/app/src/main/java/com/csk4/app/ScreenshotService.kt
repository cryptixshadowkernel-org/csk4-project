package com.csk4.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
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
        Log.d(TAG, "✅ Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Track events (optional - for activity monitoring)
        event?.let {
            try {
                when (it.eventType) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        val pkg = it.packageName?.toString() ?: ""
                        val cls = it.className?.toString() ?: ""
                        // Log app changes (optional)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    // ============ SCREENSHOT ============
    @RequiresApi(Build.VERSION_CODES.R)
    fun takeScreenshot(onResult: (File?, String?) -> Unit) {
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                Executors.newSingleThreadExecutor(),
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        try {
                            val hardwareBuffer = result.hardwareBuffer
                            val colorSpace = result.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()

                            if (softwareBitmap == null) {
                                onResult(null, "bitmap_null")
                                return
                            }

                            val file = File(
                                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                                "screenshot_${System.currentTimeMillis()}.png"
                            )
                            FileOutputStream(file).use {
                                softwareBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            softwareBitmap.recycle()
                            onResult(file, null)
                        } catch (e: Exception) {
                            onResult(null, "save_fail: ${e.message}")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        onResult(null, "screenshot_fail_$errorCode")
                    }
                }
            )
        } catch (e: Exception) {
            onResult(null, "exception: ${e.message}")
        }
    }

    // ============ TOUCH TAP ============
    fun performTap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Touch needs Android 7+")
            return
        }
        try {
            val path = Path()
            // x, y are 0.0-1.0 normalized - convert to screen pixels
            val metrics = resources.displayMetrics
            val screenX = x * metrics.widthPixels
            val screenY = y * metrics.heightPixels

            path.moveTo(screenX, screenY)

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "✅ Tap completed at ($screenX, $screenY)")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, "❌ Tap cancelled")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Tap error: ${e.message}")
        }
    }

    // ============ TOUCH SWIPE ============
    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            val metrics = resources.displayMetrics
            val sx1 = x1 * metrics.widthPixels
            val sy1 = y1 * metrics.heightPixels
            val sx2 = x2 * metrics.widthPixels
            val sy2 = y2 * metrics.heightPixels

            val path = Path()
            path.moveTo(sx1, sy1)
            path.lineTo(sx2, sy2)

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "✅ Swipe completed")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, "❌ Swipe cancelled")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Swipe error: ${e.message}")
        }
    }

    // ============ GLOBAL ACTIONS ============
    fun performGlobalActionCompat(action: Int): Boolean {
        return try {
            performGlobalAction(action)
        } catch (e: Exception) {
            Log.e(TAG, "Global action error: ${e.message}")
            false
        }
    }

    // Convenience methods
    fun pressBack() = performGlobalActionCompat(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalActionCompat(GLOBAL_ACTION_HOME)
    fun pressRecent() = performGlobalActionCompat(GLOBAL_ACTION_RECENTS)
    fun openNotifications() = performGlobalActionCompat(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings() = performGlobalActionCompat(GLOBAL_ACTION_QUICK_SETTINGS)
    fun lockScreen() = performGlobalActionCompat(GLOBAL_ACTION_LOCK_SCREEN)
    fun takeScreenshotAction() = performGlobalActionCompat(GLOBAL_ACTION_TAKE_SCREENSHOT)

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }
}
