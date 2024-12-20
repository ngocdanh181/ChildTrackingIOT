/*package com.example.childtrackingappiot.ui.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AudioPlayer {
    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    fun initialize(sampleRate: Int = 16000, channelCount: Int = 1) {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            println("AudioTrack initialization:")
            println("Sample rate: $sampleRate")
            println("Channel count: $channelCount")
            println("Min buffer size: $minBufferSize")

            // Tăng buffer size lên để xử lý tốt hơn
            val bufferSize = minBufferSize * 2

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            println("AudioTrack created with buffer size: $bufferSize")
        } catch (e: Exception) {
            println("Error initializing AudioTrack: ${e.message}")
            e.printStackTrace()
        }
    }

    fun startPlayback() {
        try {
            audioTrack?.play()
            _isPlaying.value = true
            println("AudioTrack playback started")
        } catch (e: Exception) {
            println("Error starting playback: ${e.message}")
            e.printStackTrace()
        }
    }

    fun processAudioChunk(base64Audio: String) {
        if (!_isPlaying.value) return

        scope.launch {
            try {
                val audioData = Base64.decode(base64Audio, Base64.DEFAULT)
                println("Processing audio chunk:")
                println("Base64 input length: ${base64Audio.length}")
                println("Decoded audio length: ${audioData.size}")

                val result = audioTrack?.write(audioData, 0, audioData.size) ?: -1
                println("AudioTrack write result: $result")

                if (result < 0) {
                    println("Error writing to AudioTrack: $result")
                }
            } catch (e: Exception) {
                println("Error processing audio chunk: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun stopPlayback() {
        try {
            audioTrack?.stop()
            _isPlaying.value = false
            println("AudioTrack playback stopped")
        } catch (e: Exception) {
            println("Error stopping playback: ${e.message}")
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            stopPlayback()
            audioTrack?.release()
            audioTrack = null
            println("AudioTrack released")
        } catch (e: Exception) {
            println("Error releasing AudioTrack: ${e.message}")
            e.printStackTrace()
        }
    }
}*/