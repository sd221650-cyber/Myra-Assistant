package com.myra.assistant

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AIBrain {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    var geminiApiKey: String = ""
    var openAiApiKey: String = ""
    var preferredEngine: String = "GEMINI"

    fun askAI(prompt: String, callback: (String) -> Unit) {
        Thread {
            try {
                if (preferredEngine == "CHATGPT" && openAiApiKey.isNotEmpty()) {
                    callChatGPT(prompt, callback)
                } else if (geminiApiKey.isNotEmpty()) {
                    callGemini(prompt, callback)
                } else {
                    callback("AI API Key सेट नहीं है। कृपया Settings में Key दर्ज करें।")
                }
            } catch (e: Exception) {
                callback("प्रोसेसिंग में समस्या आई।")
            }
        }.start()
    }

    private fun callGemini(prompt: String, callback: (String) -> Unit) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiApiKey"
        val json = JSONObject().apply {
            val contents = JSONArray().apply {
                val partObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { 
                            put("text", "You are JARVIS/MYRA assistant. Reply shortly in Hindi: $prompt") 
                        })
                    }
                    put("parts", parts)
                }
                put(partObj)
            }
            put("contents", contents)
        }

        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val resStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val obj = JSONObject(resStr)
                val text = obj.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                callback(text.trim())
            } else {
                callback("Gemini कनेक्ट नहीं हुआ।")
            }
        }
    }

    private fun callChatGPT(prompt: String, callback: (String) -> Unit) {
        val url = "https://api.openai.com/v1/chat/completions"
        val json = JSONObject().apply {
            put("model", "gpt-4o-mini")
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are JARVIS assistant. Reply shortly in Hindi.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
            put("max_tokens", 100)
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $openAiApiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val resStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val obj = JSONObject(resStr)
                val reply = obj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                callback(reply.trim())
            } else {
                callback("ChatGPT कनेक्ट नहीं हुआ।")
            }
        }
    }
}
