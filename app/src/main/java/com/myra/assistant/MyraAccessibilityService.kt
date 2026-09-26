package com.myra.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyraAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyraAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun endCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            try {
                telecomManager?.endCall()
            } catch (e: SecurityException) {
                // Fallback click on Reject/End Call node
                clickNodeByText("Decline", "Reject", "End call", "Dismiss")
            }
        } else {
            clickNodeByText("Decline", "Reject", "End call", "Dismiss")
        }
    }

    private fun clickNodeByText(vararg texts: String) {
        val rootNode = rootInActiveWindow ?: return
        for (text in texts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                break
            }
        }
    }
}
