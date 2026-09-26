package com.myra.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tvStatus: TextView
    private lateinit var btnMic: Button
    private lateinit var btnPermission: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnMic = findViewById(R.id.btnMic)
        btnPermission = findViewById(R.id.btnPermission)

        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        checkPermissions()

        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnMic.setOnClickListener {
            startListening()
        }

        setupSpeechRecognizer()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS
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
                tvStatus.text = "सुन रही हूँ, बोलिए..."
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0].lowercase(Locale.ROOT)
                    handleCommand(command)
                }
            }

            override fun onError(error: Int) {
                tvStatus.text = "माइक एरर या आवाज़ नहीं आई।"
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
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleCommand(command: String) {
        tvStatus.text = "कमांड मिला: $command"

        when {
            command.contains("call cut") || command.contains("कॉल काटो") || command.contains("कॉल कट") || command.contains("disconnect") -> {
                speak("कॉल काटी जा रही है")
                MyraAccessibilityService.instance?.endCall()
            }
            command.contains("youtube") || command.contains("यूट्यूब") -> {
                speak("यूट्यूब खोल रही हूँ")
                val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                if (intent != null) startActivity(intent)
            }
            else -> {
                speak("मुझे समझ आया: $command")
            }
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
