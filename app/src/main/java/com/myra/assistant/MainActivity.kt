package com.myra.assistant

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("MyraPrefs", Context.MODE_PRIVATE)

        tts = TextToSpeech(this, this)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050813"))
            setPadding(45, 60, 45, 45)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val titleView = TextView(this).apply {
            text = "MYRA SYSTEM CORE"
            textSize = 24f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            setShadowLayer(15f, 0f, 0f, Color.parseColor("#00E5FF"))
        }
        rootLayout.addView(titleView)

        val subTitle = TextView(this).apply {
            text = "Google Gemini AI Assistant"
            textSize = 13f
            setTextColor(Color.parseColor("#78909C"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 40)
        }
        rootLayout.addView(subTitle)

        val keyLabel = TextView(this).apply {
            text = "Gemini API Key दर्ज करें:"
            textSize = 14f
            setTextColor(Color.parseColor("#E0E0E0"))
            setPadding(0, 10, 0, 10)
        }
        rootLayout.addView(keyLabel)

        val keyInput = EditText(this).apply {
            hint = "AIzaSy..."
            setHintTextColor(Color.parseColor("#546E7A"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setText(prefs.getString("GEMINI_API_KEY", ""))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10192B"))
                setStroke(2, Color.parseColor("#00E5FF"))
                cornerRadius = 16f
            }
            setPadding(30, 25, 30, 25)
        }
        rootLayout.addView(keyInput)

        val saveBtn = Button(this).apply {
            text = "SAVE GEMINI KEY"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setOnClickListener {
                val key = keyInput.text.toString().trim()
                prefs.edit().putString("GEMINI_API_KEY", key).apply()
                Toast.makeText(this@MainActivity, "Gemini Key सुरक्षित कर ली गई!", Toast.LENGTH_SHORT).show()
                speakNatural("नमस्ते, Gemini AI सिस्टम सक्रिय कर दिया गया है।")
            }
        }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 25, 0, 30) }
        rootLayout.addView(saveBtn, btnParams)

        val startBtn = Button(this).apply {
            text = "START HUD ASSISTANT"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1A237E"))
            setOnClickListener {
                checkOverlayPermission()
            }
        }
        rootLayout.addView(startBtn, btnParams)

        setContentView(rootLayout)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setPitch(1.05f)
            tts.setSpeechRate(0.98f)
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

    private fun speakNatural(text: String) {
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MYRA_SPEAK")
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            val intent = Intent(this, FloatingService::class.java)
            startService(intent)
            Toast.makeText(this, "HUD सिस्टम चालू हो गया", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
