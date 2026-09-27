package com.myra.assistant

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.graphics.*
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.io.File
import java.util.Locale

class FloatingService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private var hudView: HUDCanvasView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var mediaPlayer: MediaPlayer? = null
    private var isListening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        initSpeechRecognizer()
        createHUD()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        hudView?.setStatus("सुन रही हूँ...", Color.parseColor("#00E5FF"))
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        hudView?.setPulse(rmsdB)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        hudView?.setStatus("सोच रही हूँ...", Color.parseColor("#FFD700"))
                    }
                    override fun onError(error: Int) {
                        isListening = false
                        hudView?.setStatus("टैप करके बोलें", Color.parseColor("#00E5FF"))
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val userQuery = matches[0]
                            hudView?.setStatus("प्रोसेसिंग...", Color.parseColor("#76FF03"))
                            processUserQuery(userQuery)
                        } else {
                            hudView?.setStatus("टैप करके बोलें", Color.parseColor("#00E5FF"))
                        }
                        isListening = false
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun processUserQuery(query: String) {
        val lower = query.lowercase(Locale.ROOT)
        when {
            lower.contains("youtube") -> {
                speakAudio("यूट्यूब खोल रही हूँ")
                val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                intent?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                hudView?.setStatus("टैप करके बोलें", Color.parseColor("#00E5FF"))
            }
            lower.contains("whatsapp") -> {
                speakAudio("व्हाट्सएप खोल रही हूँ")
                val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                intent?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                hudView?.setStatus("टैप करके बोलें", Color.parseColor("#00E5FF"))
            }
            else -> {
                AIBrain.askGemini(this, query) { answer ->
                    speakAudio(answer)
                    hudView?.setStatus("टैप करके बोलें", Color.parseColor("#00E5FF"))
                }
            }
        }
    }

    private fun startListening() {
        stopAudio()
        if (!isListening && speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            }
            speechRecognizer?.startListening(intent)
            isListening = true
        }
    }

    private fun createHUD() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        hudView = HUDCanvasView(this).apply {
            setOnClickListener {
                startListening()
            }
        }

        windowManager.addView(hudView, params)
    }

    private fun speakAudio(text: String) {
        stopAudio()
        // अगर प्राकृतिक TTS इंजन उपलब्ध है तो स्पष्ट आवाज़ में उत्तर दें
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MYRA_SPEAK")
        }
    }

    private fun stopAudio() {
        try {
            if (::tts.isInitialized && tts.isSpeaking) {
                tts.stop()
            }
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setPitch(1.05f)
            tts.setSpeechRate(1.0f)
            try {
                for (v in tts.voices) {
                    if (v.name.contains("hi") && !v.isNetworkConnectionRequired) {
                        tts.voice = v
                        break
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        stopAudio()
        hudView?.let { windowManager.removeView(it) }
        speechRecognizer?.destroy()
        if (::tts.isInitialized) {
            tts.shutdown()
        }
        super.onDestroy()
    }

    inner class HUDCanvasView(context: Context) : View(context) {
        private var pulseRadius = 140f
        private var statusText = "टैप करके बोलें"
        private var statusColor = Color.parseColor("#00E5FF")

        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.parseColor("#00E5FF")
        }

        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 22f
            color = Color.parseColor("#3300E5FF")
        }

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F0050A14")
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private var rotationAngle = 0f

        init {
            val animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 5000
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    rotationAngle = it.animatedValue as Float
                    invalidate()
                }
            }
            animator.start()
        }

        fun setPulse(rmsdB: Float) {
            val factor = if (rmsdB < 0) 1f else (rmsdB / 1.8f)
            pulseRadius = 140f + (factor * 14f)
            invalidate()
        }

        fun setStatus(text: String, color: Int) {
            statusText = text
            statusColor = color
            circlePaint.color = color
            glowPaint.color = Color.argb(70, Color.red(color), Color.green(color), Color.blue(color))
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f

            // ट्रांसलूसेंट डार्क बैकड्रॉप
            canvas.drawColor(Color.parseColor("#99000000"))

            // इनर कोर सर्कल
            canvas.drawCircle(cx, cy, 150f, corePaint)
            canvas.drawCircle(cx, cy, pulseRadius, glowPaint)
            canvas.drawCircle(cx, cy, pulseRadius, circlePaint)

            // नियॉन रोटेटिंग रिंग्स
            canvas.save()
            canvas.rotate(rotationAngle, cx, cy)
            circlePaint.strokeWidth = 3f
            canvas.drawCircle(cx, cy, 185f, circlePaint)
            canvas.restore()

            // स्टेटस टेक्स्ट
            textPaint.color = statusColor
            canvas.drawText(statusText, cx, cy + 260f, textPaint)
        }
    }
}
