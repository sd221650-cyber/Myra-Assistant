package com.myra.assistant.ai

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val apiKey: String,
    private val listener: LiveClientListener
) {
    interface LiveClientListener {
        fun onConnected()
        fun onAudioReceived(audioData: ByteArray)
        fun onTextReceived(text: String)
        fun onError(message: String)
        fun onDisconnected()
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("MYRA_AI", "Connected to Gemini Live")
                listener.onConnected()
                sendInitialConfig()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("serverContent")) {
                        val serverContent = json.getJSONObject("serverContent")
                        if (serverContent.has("modelTurn")) {
                            val parts = serverContent.getJSONObject("modelTurn").getJSONArray("parts")
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                if (part.has("text")) {
                                    listener.onTextReceived(part.getString("text"))
                                }
                                if (part.has("inlineData")) {
                                    val base64Data = part.getJSONObject("inlineData").getString("data")
                                    val audioBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                    listener.onAudioReceived(audioBytes)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MYRA_AI", "Parse error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("MYRA_AI", "WebSocket failure", t)
                listener.onError(t.localizedMessage ?: "Connection error")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDisconnected()
            }
        })
    }

    private fun sendInitialConfig() {
        val setupMsg = """
        {
            "setup": {
                "model": "models/gemini-2.0-flash-exp",
                "generationConfig": {
                    "responseModalities": ["AUDIO", "TEXT"],
                    "speechConfig": {
                        "voiceConfig": {
                            "prebuiltVoiceConfig": {
                                "voiceName": "Aoede"
                            }
                        }
                    }
                }
            }
        }
        """.trimIndent()
        webSocket?.send(setupMsg)
    }

    fun sendAudioChunk(pcmData: ByteArray) {
        val base64Audio = android.util.Base64.encodeToString(pcmData, android.util.Base64.NO_WRAP)
        val msg = """
        {
            "realtimeInput": {
                "mediaChunks": [
                    {
                        "mimeType": "audio/pcm;rate=16000",
                        "data": "$base64Audio"
                    }
                ]
            }
        }
        """.trimIndent()
        webSocket?.send(msg)
    }

    fun disconnect() {
        webSocket?.close(1000, "App closed")
    }
}
