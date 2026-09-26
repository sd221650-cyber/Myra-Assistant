package com.myra.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
    private lateinit var prefs: SharedPreferences
    private var isFlashOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        AIBrain.geminiApiKey = prefs.getString("gemini_key", "") ?: ""
        AIBrain.openAiApiKey = prefs.getString("chatgpt_key", "") ?: ""
        AIBrain.preferredEngine = prefs.getString("engine", "GEMINI") ?: "GEMINI"

        tvStatus = findViewById(R.id.tvStatus)
        reactorContainer = findViewById(R.id.reactorContainer)
        tvSettingsPrompt = findViewById(R.id.tvSettingsPrompt)

        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        startCoreAnimation()
        checkPermissions()

        tvSettingsPrompt.setOnClickListener {
            showApiDialog()
        }

        reactorContainer.setOnClickListener {
            startListening()
        }

        setupSpeechRecognizer()

        // बैकग्राउंड वेक-वर्ड सर्विस चालू करना
        val bgIntent = Intent(this, BackgroundAssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(bgIntent)
        } else {
            startService(bgIntent)
        }

        // फ्लोटिंग HUD
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingService::class.java))
        }

        handleIntentCommand(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntentCommand(intent)
    }

    private fun handleIntentCommand(intent: Intent?) {
        val cmd = intent?.getStringExtra("command_text")
        if (!cmd.isNullOrEmpty()) {
            handleJarvisCommand(cmd)
        }
    }

    private fun showApiDialog() {
        val input = EditText(this).apply {
            hint = "Gemini / ChatGPT Key"
            setText(if (AIBrain.geminiApiKey.isNotEmpty()) AIBrain.geminiApiKey else AIBrain.openAiApiKey)
        }

        AlertDialog.Builder(this)
            .setTitle("MYRA AI Settings")
            .setView(input)
            .setPositiveButton("Gemini Save") { _, _ ->
                val key = input.text.toString().trim()
                prefs.edit().putString("gemini_key", key).putString("engine", "GEMINI").apply()
                AIBrain.geminiApiKey = key
                AIBrain.preferredEngine = "GEMINI"
                speak("Gemini Brain सक्रिय किया गया।")
            }
            .setNeutralButton("ChatGPT Save") { _, _ ->
                val key = input.text.toString().trim()
                prefs.edit().putString("chatgpt_key", key).putString("engine", "CHATGPT").apply()
                AIBrain.openAiApiKey = key
                AIBrain.preferredEngine = "CHATGPT"
                speak("ChatGPT Brain सक्रिय किया गया।")
            }
            .setNegativeButton("Permissions") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .show()
    }

    private fun startCoreAnimation() {
        val rotate = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 5000
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
            Manifest.permission.READ_CONTACTS,
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
                tvStatus.text = "> JARVIS सक्रिय: आदेश दीजिए..."
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    handleJarvisCommand(matches[0])
                }
            }

            override fun onError(error: Int) {
                tvStatus.text = "> स्टैंडबाय। कोर पर टैप करें।"
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS Brain Active...")
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleJarvisCommand(rawText: String) {
        tvStatus.text = "> $rawText"
        val cmd = rawText.lowercase(Locale.ROOT)
        val acc = MyraAccessibilityService.instance

        when {
            // 1. स्क्रीनशॉट लेना
            cmd.contains("screenshot") || cmd.contains("स्क्रीनशॉट") -> {
                speak("स्क्रीनशॉट लिया जा रहा है।")
                acc?.takeScreenCapture()
            }

            // 2. कॉल लगाना (डायरेक्ट कॉन्टैक्ट सर्च)
            cmd.contains("call") || cmd.contains("कॉल लगाओ") || cmd.contains("कॉल करो") || cmd.contains("फोन लगाओ") -> {
                val name = cmd.replace("call", "").replace("कॉल लगाओ", "").replace("कॉल करो", "")
                    .replace("फोन लगाओ", "").replace("फोन करो", "").replace("को", "").trim()
                if (name.isNotEmpty()) {
                    makePhoneCall(name)
                } else {
                    speak("किसे कॉल लगाना है, कृपया नाम बताइए।")
                }
            }

            // 3. कॉल काटना / उठाना
            cmd.contains("call cut") || cmd.contains("कॉल काटो") || cmd.contains("काट दो") || cmd.contains("फोन काट") -> {
                speak("कॉल काटी जा रही है।")
                acc?.endCall()
            }
            cmd.contains("answer") || cmd.contains("कॉल उठाओ") || cmd.contains("फोन उठाओ") || cmd.contains("रिसीव") -> {
                speak("कॉल उठा रही हूँ।")
                acc?.answerCall()
            }

            // 4. कैमरा & सेल्फी
            cmd.contains("selfie") || cmd.contains("सेल्फी") -> {
                speak("सेल्फी कैमरा खोल रही हूँ।")
                openCamera(true)
            }
            cmd.contains("camera") || cmd.contains("कैमरा") || cmd.contains("फोटो खींचो") -> {
                speak("कैमरा चालू कर रही हूँ।")
                openCamera(false)
            }

            // 5. मेमोरी फ़ीचर (याद रखो / क्या याद है)
            cmd.contains("याद रखो") || cmd.contains("remember") -> {
                val memory = rawText.replace("याद रखो", "").replace("remember", "").trim()
                val existing = prefs.getString("memories", "") ?: ""
                prefs.edit().putString("memories", "$existing\n• $memory").apply()
                speak("समझ गई सर, मैंने इसे याद रख लिया है।")
            }
            cmd.contains("क्या याद है") || cmd.contains("मेरी मेमोरी") || cmd.contains("what do you remember") -> {
                val memories = prefs.getString("memories", "") ?: ""
                if (memories.isNotEmpty()) {
                    speak("मुझे आपकी ये बातें याद हैं: $memories")
                } else {
                    speak("अभी मेमोरी में कुछ भी सुरक्षित नहीं है।")
                }
            }

            // 6. जेस्चर स्क्रॉल
            cmd.contains("scroll down") || cmd.contains("नीचे करो") || cmd.contains("नीचे स्क्रॉल") -> {
                speak("स्क्रीन नीचे स्क्रॉल कर रही हूँ।")
                acc?.scrollDown()
            }
            cmd.contains("scroll up") || cmd.contains("ऊपर करो") || cmd.contains("ऊपर स्क्रॉल") -> {
                speak("स्क्रीन ऊपर स्क्रॉल कर रही हूँ।")
                acc?.scrollUp()
            }

            // 7. टॉर्च & वॉल्यूम
            cmd.contains("torch on") || cmd.contains("टॉर्च जलाओ") || cmd.contains("लाइट ऑन") -> {
                toggleFlashlight(true)
                speak("टॉर्च चालू कर दी है।")
            }
            cmd.contains("torch off") || cmd.contains("टॉर्च बंद") || cmd.contains("लाइट बंद") -> {
                toggleFlashlight(false)
                speak("टॉर्च बंद कर दी है।")
            }
            cmd.contains("volume up") || cmd.contains("आवाज बढ़ाओ") || cmd.contains("वॉल्यूम बढ़ाओ") -> {
                adjustVolume(AudioManager.ADJUST_RAISE)
                speak("आवाज़ बढ़ा दी गई है।")
            }
            cmd.contains("volume down") || cmd.contains("आवाज कम करो") || cmd.contains("वॉल्यूम कम करो") -> {
                adjustVolume(AudioManager.ADJUST_LOWER)
                speak("आवाज़ कम कर दी गई है।")
            }

            // 8. यूट्यूब ऑटो-प्ले
            cmd.contains("play") || cmd.contains("प्ले") || cmd.contains("चलाओ") || cmd.contains("लगाओ") ||
            cmd.contains("यूट्यूब") || cmd.contains("युटुब") || cmd.contains("youtube") || cmd.contains("सॉन्ग") || cmd.contains("गाना") -> {
                val query = cleanYouTubeQuery(cmd)
                speak("यूट्यूब पर $query चला रही हूँ।")
                MyraAccessibilityService.targetVideoTitle = query
                playYouTube(query)
            }

            // 9. AI Brain (Gemini / ChatGPT)
            else -> {
                tvStatus.text = "> AI Brain सोच रहा है..."
                AIBrain.askAI(rawText) { reply ->
                    runOnUiThread {
                        tvStatus.text = "> $reply"
                        speak(reply)
                    }
                }
            }
        }
    }

    private fun makePhoneCall(name: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            speak("कॉन्टैक्ट्स पढ़ने की अनुमति नहीं है।")
            return
        }

        var number: String? = null
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            }
        }

        if (!number.isNullOrEmpty()) {
            speak("$name को कॉल लगाया जा रहा है।")
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${number?.replace(" ", "")}"))
            startActivity(callIntent)
        } else {
            speak("माफ़ कीजिए, $name नाम से कोई नंबर नहीं मिला।")
        }
    }

    private fun openCamera(isFront: Boolean) {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        if (isFront) {
            intent.putExtra("android.intent.extras.CAMERA_FACING", 1)
        }
        startActivity(intent)
    }

    private fun cleanYouTubeQuery(cmd: String): String {
        var q = cmd
            .replace("youtube", "").replace("यूट्यूब", "").replace("युटुब", "").replace("यू ट्यूब", "")
            .replace("play", "").replace("प्ले करो", "").replace("प्ले", "")
            .replace("चलाओ", "").replace("चला दो", "").replace("लगाओ", "").replace("लगा दो", "")
            .replace("सर्च करो", "").replace("सर्च", "").replace("खोजो", "")
            .replace("पर", "").replace("में", "").replace("वाली", "").replace("वाला", "")
            .replace("वीडियो", "").replace("गाना", "").replace("सॉन्ग", "").replace("song", "")
            .trim()
        return if (q.isEmpty()) "trending music" else q
    }

    private fun playYouTube(query: String) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val appUri = Uri.parse("vnd.youtube.launch://results?search_query=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, appUri).apply {
            setPackage("com.google.android.youtube")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(webIntent)
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
            tts.setPitch(1.1f)
            tts.setSpeechRate(0.98f)
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
