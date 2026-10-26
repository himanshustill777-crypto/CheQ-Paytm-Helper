package com.cheq.helper

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BillAccessibilityService : AccessibilityService() {
 private val forbidden = Regex("(?i)(otp|cvv|pin|password|passcode)")
 private val billTerms = Regex("(?i)(total amount due|minimum amount due|amount due|due date|outstanding|bill amount)")

 override fun onAccessibilityEvent(event: AccessibilityEvent?) {
  if (event?.packageName?.toString() != "net.one97.paytm") return
  val root = rootInActiveWindow ?: return
  val lines = mutableListOf<String>(); collectVisibleText(root, lines)
  val safe = lines.filterNot { forbidden.containsMatchIn(it) }
  val result = safe.filter { billTerms.containsMatchIn(it) }.distinct().take(12)
  if (result.isNotEmpty()) getSharedPreferences("bill", MODE_PRIVATE).edit().putString("result", result.joinToString("\n")).apply()
 }

 private fun collectVisibleText(node: AccessibilityNodeInfo, out: MutableList<String>) {
  if (!node.isVisibleToUser || node.isPassword) return
  node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
  node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
  for (i in 0 until node.childCount) node.getChild(i)?.let { collectVisibleText(it, out); it.recycle() }
 }
 override fun onInterrupt() {}
}