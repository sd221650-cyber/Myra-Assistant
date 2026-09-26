package com.myra.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyraAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyraAccessibilityService? = null
        var targetVideoTitle: String? = null
        var autoReplyEnabled = true
        var lastRepliedText = ""
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""

        // WhatsApp Autonomous Auto-Reply
        if (autoReplyEnabled && (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b")) {
            handleWhatsAppAutomation(rootInActiveWindow)
        }

        // YouTube Targeted Auto-Play
        val target = targetVideoTitle
        if (!target.isNullOrEmpty() && pkg == "com.google.android.youtube") {
            Handler(Looper.getMainLooper()).postDelayed({
                if (findAndClickTargetVideo(rootInActiveWindow, target.lowercase())) {
                    targetVideoTitle = null
                }
            }, 1000)
        }
    }

    // स्क्रीनशॉट लेना (Android 9+)
    fun takeScreenCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }

    private fun handleWhatsAppAutomation(root: AccessibilityNodeInfo?) {
        if (root == null) return

        val messageNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/message_text")
        if (!messageNodes.isNullOrEmpty()) {
            val lastIncomingMsg = messageNodes.last().text?.toString()?.trim() ?: ""

            if (lastIncomingMsg.isNotEmpty() && lastIncomingMsg != lastRepliedText) {
                val inputNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
                if (!inputNodes.isNullOrEmpty()) {
                    val inputBox = inputNodes[0]
                    lastRepliedText = lastIncomingMsg

                    val replyText = generateAutonomousReply(lastIncomingMsg)
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replyText)
                    }
                    inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

                    Handler(Looper.getMainLooper()).postDelayed({
                        val sendButtons = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                        if (!sendButtons.isNullOrEmpty()) {
                            sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                    }, 500)
                }
            }
        }
    }

    private fun generateAutonomousReply(incoming: String): String {
        val lower = incoming.lowercase()
        return when {
            lower.contains("kaha ho") || lower.contains("kahan ho") || lower.contains("kidhar") -> 
                "Abhi thoda busy hoon, thodi der me baat karta hoon. (Auto-reply by MYRA)"
            lower.contains("hi") || lower.contains("hello") || lower.contains("hey") -> 
                "Hello! Main abhi available nahi hoon, message chhod dijiye."
            lower.contains("kaam") || lower.contains("urgent") -> 
                "Koi urgent kaam hai toh batao, main call back karta hoon."
            else -> 
                "Got your message! Main free hokar connect karta hoon. - MYRA AI"
        }
    }

    private fun findAndClickTargetVideo(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val keywords = target.split(" ").filter { it.length > 2 }
        val matches = keywords.any { text.contains(it) || desc.contains(it) }

        if (matches) {
            var clickableNode: AccessibilityNodeInfo? = node
            while (clickableNode != null && !clickableNode.isClickable) {
                clickableNode = clickableNode.parent
            }
            if (clickableNode != null && clickableNode.isClickable) {
                clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }

        for (i in 0 until node.childCount) {
            if (findAndClickTargetVideo(node.getChild(i), target)) return true
        }
        return false
    }

    fun scrollDown() {
        val dm = resources.displayMetrics
        performSwipe((dm.widthPixels / 2).toFloat(), (dm.heightPixels * 0.75).toFloat(), (dm.widthPixels / 2).toFloat(), (dm.heightPixels * 0.25).toFloat())
    }

    fun scrollUp() {
        val dm = resources.displayMetrics
        performSwipe((dm.widthPixels / 2).toFloat(), (dm.heightPixels * 0.25).toFloat(), (dm.widthPixels / 2).toFloat(), (dm.heightPixels * 0.75).toFloat())
    }

    private fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
            val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 300)).build()
            dispatchGesture(gesture, null, null)
        }
    }

    fun answerCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val telecom = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            try { telecom?.acceptRingingCall() } catch (e: Exception) { clickNodeByText("Answer", "Accept", "उठाएं") }
        } else {
            clickNodeByText("Answer", "Accept", "उठाएं")
        }
    }

    fun endCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val telecom = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            try { telecom?.endCall() } catch (e: Exception) { clickNodeByText("Decline", "Reject", "End call", "Dismiss", "काटें") }
        } else {
            clickNodeByText("Decline", "Reject", "End call", "Dismiss", "काटें")
        }
    }

    private fun clickNodeByText(vararg texts: String) {
        val root = rootInActiveWindow ?: return
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                break
            }
        }
    }

    override fun onInterrupt() {}
}
