package com.cheq.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BillAccessibilityService : AccessibilityService() {
    private val forbidden = Regex("(?i)(otp|cvv|pin|password|passcode|one time password|verification code)")
    private val dueDate = Regex("(?i)due\\s*date\\s*[-:]?\\s*([0-9]{1,2}\\s+[a-z]{3,9}\\s+[0-9]{4})")
    private val money = Regex("₹\\s*[0-9,]+(?:\\.[0-9]{1,2})?")
    private var lastAutoClickAt = 0L
    private var gestureScheduled = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val prefs = getSharedPreferences("bill", MODE_PRIVATE)

        if (!pkg.contains("paytm", true) && pkg != "net.one97.paytm") return
        prefs.edit().putString("debug", "Paytm accessibility event received").apply()

        val root = rootInActiveWindow

        if (prefs.getBoolean("auto_open_credit_card", false)) {
            if (root == null) {
                if (!gestureScheduled && SystemClock.elapsedRealtime() - lastAutoClickAt > 1200) {
                    gestureScheduled = true
                    lastAutoClickAt = SystemClock.elapsedRealtime()
                    val dm = resources.displayMetrics
                    val tapX = dm.widthPixels * 0.34f
                    val tapY = dm.heightPixels * 0.455f
                    val path = Path().apply { moveTo(tapX, tapY) }
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                        .build()
                    val sent = dispatchGesture(gesture, object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            prefs.edit()
                                .putBoolean("auto_open_credit_card", false)
                                .putString("debug", "AUTO OK: fallback tap sent without Paytm nodes")
                                .apply()
                            gestureScheduled = false
                        }
                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            prefs.edit().putString("debug", "AUTO fallback tap cancelled").apply()
                            gestureScheduled = false
                        }
                    }, null)
                    if (!sent) {
                        prefs.edit().putString("debug", "AUTO fallback tap could not be dispatched").apply()
                        gestureScheduled = false
                    }
                }
                prefs.edit().putString("debug", "AUTO: no Paytm nodes; using screen-position tap").apply()
                return
            }
            val targets = root.findAccessibilityNodeInfosByText("Credit Card Bill") + root.findAccessibilityNodeInfosByText("Credit Card Payment")
            val target = targets.firstOrNull { it.isVisibleToUser }
            if (target != null && SystemClock.elapsedRealtime() - lastAutoClickAt > 1500) {
                lastAutoClickAt = SystemClock.elapsedRealtime()
                var n: AccessibilityNodeInfo? = target
                var clicked = false
                var hops = 0
                while (n != null && hops < 6 && !clicked) {
                    if (n.isClickable) clicked = n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    n = n.parent
                    hops++
                }
                if (clicked) {
                    prefs.edit().putBoolean("auto_open_credit_card", false).putString("debug", "AUTO OK: Credit Card Bill clicked").apply()
                    return
                }
            }
            if (!gestureScheduled && SystemClock.elapsedRealtime() - lastAutoClickAt > 1200) {
                gestureScheduled = true
                lastAutoClickAt = SystemClock.elapsedRealtime()
                val dm = resources.displayMetrics
                val tapX = dm.widthPixels * 0.34f
                val tapY = dm.heightPixels * 0.455f
                val path = Path().apply { moveTo(tapX, tapY) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                    .build()
                val sent = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        prefs.edit()
                            .putBoolean("auto_open_credit_card", false)
                            .putString("debug", "AUTO gesture sent to Credit Card Bill tile")
                            .apply()
                        gestureScheduled = false
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        prefs.edit().putString("debug", "AUTO gesture was cancelled").apply()
                        gestureScheduled = false
                    }
                }, null)
                if (!sent) {
                    prefs.edit().putString("debug", "AUTO gesture could not be dispatched").apply()
                    gestureScheduled = false
                }
            }
            prefs.edit().putString("debug", "AUTO: Paytm home; tapping Credit Card Bill").apply()
        }

        if (root == null) {
            prefs.edit().putString("debug", "Paytm detected; no readable nodes").apply()
            return
        }

        val lines = mutableListOf<String>()
        collectVisibleText(root, lines)
        val safe = lines.map { it.trim() }
            .filter { it.length in 1..180 }
            .filterNot { forbidden.containsMatchIn(it) }
            .distinct()

        prefs.edit().putString("debug", "Paytm nodes read: ${safe.size}").apply()

        val dateLine = safe.firstOrNull { dueDate.containsMatchIn(it) }
            ?: safe.firstOrNull { it.contains("Due Date", true) }

        val totalIndex = safe.indexOfFirst { it.equals("Total Due", true) || it.contains("Total Due", true) }
        val minIndex = safe.indexOfFirst { it.equals("Minimum Due", true) || it.contains("Minimum Due", true) }

        fun amountNear(index: Int): String? {
            if (index < 0) return null
            for (i in index..minOf(index + 4, safe.lastIndex)) {
                money.find(safe[i])?.value?.let { return it }
            }
            return null
        }

        val total = amountNear(totalIndex)
        val minimum = amountNear(minIndex)
        val card = safe.firstOrNull { it.contains("ICICI Bank", true) || Regex("(?i)xx\\s*\\d{4}").containsMatchIn(it) }

        val result = mutableListOf<String>()
        card?.let { result.add(it) }
        dateLine?.let { result.add(it) }
        total?.let { result.add("Total Due: $it") }
        minimum?.let { result.add("Minimum Due: $it") }

        if (result.isNotEmpty()) {
            prefs.edit()
                .putString("result", result.joinToString("\n"))
                .putString("debug", "Bill fields captured successfully")
                .apply()
        }
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        if (!node.isVisibleToUser || node.isPassword) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                collectVisibleText(it, out)
                it.recycle()
            }
        }
    }

    override fun onInterrupt() {}
}