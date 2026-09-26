package com.myra.assistant.ai

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue

class AudioEngine(
    private val onAudioChunkReady: (ByteArray) -> Unit,
    private val onRmsCalculated: (Float) -> Unit
) {
    private val sampleRateInput = 16000
    private val sampleRateOutput = 24000
    private val bufferSize = 1024

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var isRecording = false
    private var isPlaying = false

    private val audioScope = CoroutineScope(Dispatchers.IO + Job())
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRateInput,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateInput,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, bufferSize * 2)
        )

        audioRecord?.startRecording()
        isRecording = true

        audioScope.launch {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    onAudioChunkReady(chunk)
                    calculateRms(chunk)
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun initPlayback() {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRateOutput,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateOutput)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, bufferSize * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying = true

        audioScope.launch {
            while (isPlaying) {
                val data = playbackQueue.poll()
                if (data != null) {
                    audioTrack?.write(data, 0, data.size)
                } else {
                    delay(10)
                }
            }
        }
    }

    fun enqueuePlayback(audioData: ByteArray) {
        playbackQueue.offer(audioData)
    }

    fun stopPlayback() {
        isPlaying = false
        playbackQueue.clear()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private fun calculateRms(pcm: ByteArray) {
        var sum = 0.0
        val shortCount = pcm.size / 2
        for (i in 0 until shortCount) {
            val sample = (pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = Math.sqrt(sum / shortCount).toFloat()
        onRmsCalculated(rms)
    }

    fun release() {
        stopRecording()
        stopPlayback()
        audioScope.cancel()
    }
}
