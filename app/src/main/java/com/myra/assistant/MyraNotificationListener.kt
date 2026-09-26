package com.myra.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MyraNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: ""
        val extras = sbn.notification.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isNotEmpty() && text.isNotEmpty()) {
            if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b" || pkg.contains("mms") || pkg.contains("instagram")) {
                val announcement = "$title का संदेश आया है: $text"
                BackgroundAssistantService.speakOut(announcement)
            }
        }
    }
}
