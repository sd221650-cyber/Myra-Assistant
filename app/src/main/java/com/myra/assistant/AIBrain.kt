package com.myra.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AIBrain {

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
                callback("नेटवर्क या प्रोसेसिंग में समस्या आई।")
            }
        }.start()
    }

    private fun callGemini(prompt: String, callback: (String) -> Unit) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiApiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 20000

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

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(json.toString())
            writer.flush()
            writer.close()

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val res = reader.readText()
                reader.close()

                val obj = JSONObject(res)
                val text = obj.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                callback(text.trim())
            } else {
                callback("Gemini API से कनेक्ट नहीं हो सका।")
            }
        } catch (e: Exception) {
            callback("Gemini अनुरोध विफल रहा।")
        }
    }

    private fun callChatGPT(prompt: String, callback: (String) -> Unit) {
        try {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $openAiApiKey")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 20000

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

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(json.toString())
            writer.flush()
            writer.close()

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val res = reader.readText()
                reader.close()

                val obj = JSONObject(res)
                val reply = obj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                callback(reply.trim())
            } else {
                callback("ChatGPT API से कनेक्ट नहीं हो सका।")
            }
        } catch (e: Exception) {
            callback("ChatGPT अनुरोध विफल रहा।")
        }
    }
}
