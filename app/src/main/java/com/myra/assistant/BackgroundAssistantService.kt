package com.myra.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class BackgroundAssistantService : Service(), TextToSpeech.OnInitListener {

    companion object {
        var isServiceRunning = false
        private var ttsInstance: TextToSpeech? = null

        fun speakOut(text: String) {
            ttsInstance?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        ttsInstance = TextToSpeech(this, this)

        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, "MYRA_BG_CHANNEL")
            .setContentTitle("MYRA Core Online")
            .setContentText("JARVIS बैकग्राउंड में सुन रहा है...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
        initBackgroundSpeech()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "MYRA_BG_CHANNEL",
                "MYRA Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun initBackgroundSpeech() {
        handler.post {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // लगातार बैकग्राउंड लिसनिंग चालू रखने के लिए ऑटो-रीस्टार्ट
                    handler.postDelayed({ startListeningAgain() }, 1500)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        handleBackgroundCommand(text)
                    }
                    handler.postDelayed({ startListeningAgain() }, 1000)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListeningAgain()
        }
    }

    private fun startListeningAgain() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {}
    }

    private fun handleBackgroundCommand(raw: String) {
        val cmd = raw.lowercase(Locale.ROOT)

        // वेक वर्ड या डायरेक्ट कमांड चेक
        if (cmd.contains("myra") || cmd.contains("मायरा") || cmd.contains("jarvis") || cmd.contains("जार्विस") ||
            cmd.contains("screenshot") || cmd.contains("स्क्रीनशॉट") || cmd.contains("कॉल") || cmd.contains("torch") || cmd.contains("लाइट")) {

            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("command_text", raw)
            }
            startActivity(intent)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInstance?.language = Locale("hi", "IN")
            ttsInstance?.setPitch(1.1f)
            ttsInstance?.setSpeechRate(0.98f)
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        speechRecognizer?.destroy()
        ttsInstance?.stop()
        ttsInstance?.shutdown()
        super.onDestroy()
    }
}
