package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat

class CallMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "MyraCallChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRA Call Monitor")
            .setContentText("Monitoring system calls & controls")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
        startForeground(101, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("ACTION")
        if (action == "END_CALL") {
            endCall()
        }
        return START_STICKY
    }

    private fun endCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            try {
                telecomManager?.endCall()
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRA Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
