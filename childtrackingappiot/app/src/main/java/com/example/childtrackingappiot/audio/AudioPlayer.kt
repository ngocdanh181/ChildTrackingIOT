package com.example.childtrackingappiot.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URI

class AudioPlayer {
    private var audioTrack: AudioTrack? = null
    private var webSocket: AudioWebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    fun initialize(deviceId: String, sampleRate: Int = 16000, channelCount: Int = 1) {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

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
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // Initialize WebSocket with correct URL format
            val uri = URI("ws://192.168.1.5:3000/audio?type=android&deviceId=$deviceId")
            webSocket = AudioWebSocket(
                serverUri = uri,
                deviceId = deviceId,
                onAudioDataReceived = { audioData ->
                    processAudioData(audioData)
                },
                onConnectionStateChanged = { isConnected ->
                    _isPlaying.value = isConnected
                }
            )
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error initializing: ${e.message}")
        }
    }

    fun startPlayback() {
        try {
            audioTrack?.play()
            webSocket?.connect()
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error starting playback: ${e.message}")
        }
    }

    private fun processAudioData(audioData: ByteArray) {
        if (!_isPlaying.value) return

        scope.launch {
            try {
                audioTrack?.write(audioData, 0, audioData.size)
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error processing audio: ${e.message}")
            }
        }
    }

    fun stopPlayback() {
        try {
            audioTrack?.stop()
            webSocket?.close()
            _isPlaying.value = false
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping playback: ${e.message}")
        }
    }

    fun release() {
        try {
            stopPlayback()
            audioTrack?.release()
            audioTrack = null
            webSocket = null
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error releasing: ${e.message}")
        }
    }
} 