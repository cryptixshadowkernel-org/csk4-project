package com.csk4.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

/**
 * ============================================
 * CSK4 PRO v4.0 - Touch Controller
 * ============================================
 * High-level touch control via Accessibility
 * 
 * Features:
 * - Tap on coordinates
 * - Swipe gestures
 * - Long press
 * - Pinch zoom
 * - Scroll
 * - Global actions (back/home/recent)
 * - Click by text
 * - Click by view ID
 * - Auto-check accessibility
 * ============================================
 */
class TouchController {

    companion object {
        private const val TAG = "CSK4_TOUCH"

        /**
         * Check karo Accessibility ON hai ya nahi
         */
        fun isAccessibilityEnabled(context: Context): Boolean {
            // Direct check
            if (ScreenshotService.instance != null) return true
            
            // Settings check
            try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                return enabled?.contains(context.packageName) == true
            } catch (e: Exception) {
                Log.e(TAG, "Check accessibility error: ${e.message}")
            }
            return false
        }

        /**
         * Auto-restart accessibility service
         * (User ko settings kholne ki zaroorat nahi)
         */
        fun autoRestartAccessibility(context: Context): Boolean {
            try {
                // Just verify if it's actually running
                if (ScreenshotService.instance == null) {
                    Log.w(TAG, "⚠️ Accessibility not running")
                    return false
                }
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Auto-restart error: ${e.message}")
                return false
            }
        }
    }

    // ============ SERVICE ============
    private val service: ScreenshotService?
        get() = ScreenshotService.instance

    // ============ READY CHECK ============
    fun isReady(): Boolean {
        val ready = service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        if (!ready) {
            Log.w(TAG, "Touch not ready: service=${service != null}, sdk=${Build.VERSION.SDK_INT}")
        }
        return ready
    }

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

    // ============ TAP AT PIXEL ============
    fun tapPixel(x: Float, y: Float, durationMs: Long = 100): Boolean {
        if (!isReady()) return false
        return try {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            service?.dispatchGesture(gesture, null, null) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "TapPixel: ${e.message}")
            false
        }
    }

    // ============ LONG PRESS ============
    fun longPress(x: Float, y: Float, durationMs: Long = 800): Boolean {
        return tap(x, y, durationMs)
    }

    // ============ DOUBLE TAP ============
    fun doubleTap(x: Float, y: Float): Boolean {
        if (!isReady()) return false
        try {
            val sw = getScreenWidth()
            val sh = getScreenHeight()
            val path = Path().apply {
                moveTo(x * sw, y * sh)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .addStroke(GestureDescription.StrokeDescription(path, 150, 50))
                .build()
            return service?.dispatchGesture(gesture, null, null) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "DoubleTap: ${e.message}")
            return false
        }
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
            val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "ClickByText '$text': $clicked")
            return clicked
        } catch (e: Exception) {
            Log.e(TAG, "ClickByText: ${e.message}")
            return false
        }
    }

    // ============ CLICK BY VIEW ID ============
    fun clickByViewId(viewId: String): Boolean {
        if (service == null) return false
        try {
            val root = service?.rootInActiveWindow ?: return false
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNullOrEmpty()) return false
            val clicked = nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "ClickByViewId '$viewId': $clicked")
            return clicked
        } catch (e: Exception) {
            Log.e(TAG, "ClickByViewId: ${e.message}")
            return false
        }
    }

    // ============ GET SCREEN SIZE ============
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

    // ============ DUMP SCREEN (for debugging) ============
    fun dumpScreen(): String {
        if (service == null) return "Service not running"
        return try {
            val root = service?.rootInActiveWindow ?: return "No active window"
            val sb = StringBuilder()
            dumpNode(root, sb, 0)
            sb.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun dumpNode(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 10) return
        try {
            val indent = "  ".repeat(depth)
            sb.append("$indent${node.className} ")
            if (!node.text.isNullOrEmpty()) sb.append("text='${node.text}' ")
            if (!node.viewIdResourceName.isNullOrEmpty()) sb.append("id='${node.viewIdResourceName}' ")
            if (node.isClickable) sb.append("[CLICKABLE] ")
            sb.append("\n")

            for (i in 0 until node.childCount) {
                dumpNode(node.getChild(i), sb, depth + 1)
            }
        } catch (e: Exception) {}
    }
}
