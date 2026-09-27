package com.myra.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object AIBrain {

    fun askGemini(context: Context, userQuery: String, onResponse: (String) -> Unit) {
        val prefs = context.getSharedPreferences("MyraPrefs", Context.MODE_PRIVATE)
        val geminiKey = prefs.getString("GEMINI_API_KEY", "") ?: ""

        if (geminiKey.isEmpty()) {
            Handler(Looper.getMainLooper()).post {
                onResponse("सर, कृपया सेटिंग्स में जाकर अपनी Gemini API Key दर्ज करें।")
            }
            return
        }

        thread {
            try {
                val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiKey"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 12000
                conn.readTimeout = 12000

                val systemPrompt = "तुम Myra हो, एक अत्यंत बुद्धिमता पूर्ण, फुर्तीली और विनम्र पर्सनल AI असिस्टेंट। तुम्हारा उत्तर हमेशा संक्षिप्त, स्पष्ट, दोस्ताना और हिंदी में होना चाहिए।"
                
                val body = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val userPart = JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", "$systemPrompt\n\nUser: $userQuery"))
                            })
                        }
                        put(userPart)
                    }
                    put("contents", contents)
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val jsonResponse = JSONObject(response)
                    val candidates = jsonResponse.getJSONArray("candidates")
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val answer = parts.getJSONObject(0).getString("text")

                    Handler(Looper.getMainLooper()).post {
                        onResponse(answer.trim())
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        onResponse("Gemini सर्वर त्रुटि कोड: $responseCode")
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onResponse("त्रुटि: ${e.localizedMessage}")
                }
            }
        }
    }
}
