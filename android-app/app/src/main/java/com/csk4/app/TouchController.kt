package com.csk4.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

/**
 * TouchController — High-level touch control via Accessibility
 * 
 * Features:
 * - Tap on coordinates (normalized 0.0-1.0)
 * - Swipe gestures
 * - Long press
 * - Pinch zoom
 * - Scroll
 * - Global actions (back/home/recent)
 * - Text input
 * - Click on UI elements by text
 */
class TouchController {

    companion object {
        private const val TAG = "CSK4_TOUCH"
    }

    private val service: ScreenshotService?
        get() = ScreenshotService.instance

    fun isReady(): Boolean = service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    // ============ TAP ============
    fun tap(x: Float, y: Float, durationMs: Long = 100): Boolean {
        if (!isReady()) return false
        return try {
            val path = Path().apply {
                moveTo(x * getScreenWidth(), y * getScreenHeight())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            service?.dispatchGesture(gesture, null, null) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Tap: ${e.message}")
            false
        }
    }

    // ============ LONG PRESS ============
    fun longPress(x: Float, y: Float, durationMs: Long = 800): Boolean {
        return tap(x, y, durationMs)
    }

    // ============ SWIPE ============
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean {
        if (!isReady()) return false
        return try {
            val path = Path().apply {
                moveTo(x1 * getScreenWidth(), y1 * getScreenHeight())
                lineTo(x2 * getScreenWidth(), y2 * getScreenHeight())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            service?.dispatchGesture(gesture, null, null) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Swipe: ${e.message}")
            false
        }
    }

    // ============ SCROLL ============
    fun scrollUp(): Boolean = swipe(0.5f, 0.7f, 0.5f, 0.3f, 400)
    fun scrollDown(): Boolean = swipe(0.5f, 0.3f, 0.5f, 0.7f, 400)
    fun scrollLeft(): Boolean = swipe(0.7f, 0.5f, 0.3f, 0.5f, 400)
    fun scrollRight(): Boolean = swipe(0.3f, 0.5f, 0.7f, 0.5f, 400)

    // ============ PINCH ZOOM ============
    @RequiresApi(Build.VERSION_CODES.O)
    fun pinchOut(cx: Float = 0.5f, cy: Float = 0.5f): Boolean {
        if (!isReady()) return false
        return try {
            val sw = getScreenWidth()
            val sh = getScreenHeight()
            val cxPx = cx * sw
            val cyPx = cy * sh

            val path1 = Path().apply {
                moveTo(cxPx - 100, cyPx)
                lineTo(cxPx - 300, cyPx)
            }
            val path2 = Path().apply {
                moveTo(cxPx + 100, cyPx)
                lineTo(cxPx + 300, cyPx)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path1, 0, 300))
                .addStroke(GestureDescription.StrokeDescription(path2, 0, 300))
                .build()
            service?.dispatchGesture(gesture, null, null) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "PinchOut: ${e.message}")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun pinchIn(cx: Float = 0.5f, cy: Float = 0.5f): Boolean {
        if (!isReady()) return false
        return try {
            val sw = getScreenWidth()
            val sh = getScreenHeight()
            val cxPx = cx * sw
            val cyPx = cy * sh

            val path1 = Path().apply {
                moveTo(cxPx - 300, cyPx)
                lineTo(cxPx - 100, cyPx)
            }
            val path2 = Path().apply {
                moveTo(cxPx + 300, cyPx)
                lineTo(cxPx + 100, cyPx)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path1, 0, 300))
                .addStroke(GestureDescription.StrokeDescription(path2, 0, 300))
                .build()
            service?.dispatchGesture(gesture, null, null) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "PinchIn: ${e.message}")
            false
        }
    }

    // ============ GLOBAL ACTIONS ============
    fun pressBack(): Boolean {
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
    }

    fun pressHome(): Boolean {
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
    }

    fun pressRecent(): Boolean {
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
    }

    fun openNotifications(): Boolean {
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: false
    }

    fun openQuickSettings(): Boolean {
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) ?: false
    }

    fun lockScreen(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }
        return false
    }

    fun takeScreenshot(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
        }
        return false
    }

    fun openPowerDialog(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) ?: false
        }
        return false
    }

    fun splitScreen(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) ?: false
        }
        return false
    }

    // ============ CLICK BY TEXT ============
    fun clickByText(text: String): Boolean {
        if (service == null) return false
        try {
            val root = service?.rootInActiveWindow ?: return false
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNullOrEmpty()) return false

            val node = nodes.first()
            // Try to click
            var clickable = node
            var depth = 0
            while (!clickable.isClickable && clickable.parent != null && depth < 5) {
                clickable = clickable.parent
                depth++
            }
            return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            Log.e(TAG, "ClickByText: ${e.message}")
            return false
        }
    }

    // ============ CLICK BY ID ============
    fun clickByViewId(viewId: String): Boolean {
        if (service == null) return false
        try {
            val root = service?.rootInActiveWindow ?: return false
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNullOrEmpty()) return false
            return nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            return false
        }
    }

    // ============ SCREEN SIZE ============
    private fun getScreenWidth(): Float {
        return try {
            service?.resources?.displayMetrics?.widthPixels?.toFloat() ?: 1080f
        } catch (e: Exception) { 1080f }
    }

    private fun getScreenHeight(): Float {
        return try {
            service?.resources?.displayMetrics?.heightPixels?.toFloat() ?: 1920f
        } catch (e: Exception) { 1920f }
    }

    // ============ FULL SCREEN INFO ============
    fun getScreenInfo(): Map<String, Any> {
        val metrics = service?.resources?.displayMetrics
        return mapOf(
            "width" to (metrics?.widthPixels ?: 0),
            "height" to (metrics?.heightPixels ?: 0),
            "density" to (metrics?.density ?: 0f),
            "densityDpi" to (metrics?.densityDpi ?: 0),
            "ready" to isReady()
        )
    }
}
