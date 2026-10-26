package com.cheq.helper

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BillAccessibilityService : AccessibilityService() {
    private val forbidden = Regex("(?i)(otp|cvv|pin|password|passcode|one time password|verification code)")
    private val billHints = Regex("(?i)(amount|due|bill|outstanding|minimum|total|payable|payment|date|credit card|card ending|₹|rs\\.?|inr)")
    private val moneyLike = Regex("(?i)(₹\\s?[0-9,]+(?:\\.[0-9]{1,2})?|(?:rs\\.?|inr)\\s?[0-9,]+(?:\\.[0-9]{1,2})?)")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (!pkg.contains("paytm", ignoreCase = true) && pkg != "net.one97.paytm") return

        val root = rootInActiveWindow ?: return
        val lines = mutableListOf<String>()
        collectVisibleText(root, lines)

        val safe = lines
            .map { it.trim() }
            .filter { it.length in 2..180 }
            .filterNot { forbidden.containsMatchIn(it) }
            .distinct()

        val relevant = safe.filter {
            billHints.containsMatchIn(it) || moneyLike.containsMatchIn(it)
        }.take(30)

        if (relevant.isNotEmpty()) {
            getSharedPreferences("bill", MODE_PRIVATE)
                .edit()
                .putString("result", relevant.joinToString("\n"))
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