package com.myra.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tvStatus: TextView
    private lateinit var reactorContainer: FrameLayout
    private lateinit var tvSettingsPrompt: TextView
    private var isFlashOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        reactorContainer = findViewById(R.id.reactorContainer)
        tvSettingsPrompt = findViewById(R.id.tvSettingsPrompt)

        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        startCoreAnimation()
        checkPermissions()

        tvSettingsPrompt.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        reactorContainer.setOnClickListener {
            startListening()
        }

        setupSpeechRecognizer()

        if (intent.getBooleanExtra("auto_listen", false)) {
            startListening()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startService(Intent(this, FloatingService::class.java))
            }
        } else {
            startService(Intent(this, FloatingService::class.java))
        }
    }

    private fun startCoreAnimation() {
        val rotate = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 6000
            repeatCount = Animation.INFINITE
            interpolator = LinearInterpolator()
        }
        reactorContainer.startAnimation(rotate)
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CAMERA
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                tvStatus.text = "> LISTENING TO VOICE STREAM..."
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    handleJarvisCommand(matches[0])
                }
            }

            override fun onError(error: Int) {
                tvStatus.text = "> STANDBY. TAP CORE TO TALK."
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS Core Active...")
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleJarvisCommand(rawText: String) {
        tvStatus.text = "> EXECUTING: $rawText"
        val cmd = rawText.lowercase(Locale.ROOT)
        val acc = MyraAccessibilityService.instance

        when {
            cmd.contains("call cut") || cmd.contains("कॉल काटो") || cmd.contains("काट दो") || cmd.contains("फोन काट") -> {
                speak("Call disconnected.")
                acc?.endCall()
            }
            cmd.contains("answer") || cmd.contains("कॉल उठाओ") || cmd.contains("फोन उठाओ") || cmd.contains("रिसीव") -> {
                speak("Call accepted.")
                acc?.answerCall()
            }
            cmd.contains("scroll down") || cmd.contains("नीचे करो") || cmd.contains("नीचे स्क्रॉल") || cmd.contains("स्क्रॉल डाउन") -> {
                speak("Scrolling down.")
                acc?.scrollDown()
            }
            cmd.contains("scroll up") || cmd.contains("ऊपर करो") || cmd.contains("ऊपर स्क्रॉल") || cmd.contains("स्क्रॉल अप") -> {
                speak("Scrolling up.")
                acc?.scrollUp()
            }
            cmd.contains("torch on") || cmd.contains("टॉर्च जलाओ") || cmd.contains("लाइट ऑन") -> {
                toggleFlashlight(true)
                speak("Flashlight activated.")
            }
            cmd.contains("torch off") || cmd.contains("टॉर्च बंद") || cmd.contains("लाइट बंद") -> {
                toggleFlashlight(false)
                speak("Flashlight deactivated.")
            }
            cmd.contains("volume up") || cmd.contains("आवाज बढ़ाओ") || cmd.contains("वॉल्यूम बढ़ाओ") -> {
                adjustVolume(AudioManager.ADJUST_RAISE)
                speak("Volume increased.")
            }
            cmd.contains("volume down") || cmd.contains("आवाज कम करो") || cmd.contains("वॉल्यूम कम करो") -> {
                adjustVolume(AudioManager.ADJUST_LOWER)
                speak("Volume decreased.")
            }
            cmd.contains("play") || cmd.contains("प्ले") || cmd.contains("चलाओ") || cmd.contains("यूट्यूब") || cmd.contains("युटुब") -> {
                var query = cmd
                    .replace("youtube", "").replace("यूट्यूब", "").replace("युटुब", "")
                    .replace("play", "").replace("प्ले करो", "").replace("प्ले", "")
                    .replace("चलाओ", "").replace("लगाओ", "").replace("सर्च", "")
                    .replace("पर", "").replace("में", "").replace("वाली", "").replace("वाला", "")
                    .replace("वीडियो", "").replace("गाना", "").replace("song", "")
                    .trim()

                if (query.isEmpty()) query = "trending"

                speak("Locating and playing $query on YouTube.")
                MyraAccessibilityService.targetVideoTitle = query

                val appIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    startActivity(appIntent)
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))))
                }
            }
            cmd.contains("whatsapp") || cmd.contains("व्हाट्सएप") -> {
                speak("Opening WhatsApp.")
                val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (intent != null) startActivity(intent) else speak("WhatsApp not installed.")
            }
            cmd.contains("instagram") || cmd.contains("इंस्टाग्राम") || cmd.contains("insta") -> {
                speak("Opening Instagram Direct.")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/_u//direct")).apply {
                    setPackage("com.instagram.android")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/direct/inbox/")))
                }
            }
            else -> {
                speak("Searching online database.")
                val searchUrl = "https://www.google.com/search?q=" + URLEncoder.encode(rawText, "UTF-8")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
            }
        }
    }

    private fun toggleFlashlight(status: Boolean) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        try {
            val cameraId = cameraManager?.cameraIdList?.get(0)
            if (cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, status)
                isFlashOn = status
            }
        } catch (e: Exception) {}
    }

    private fun adjustVolume(direction: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setPitch(1.05f)
            tts.setSpeechRate(1.0f)
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
