package com.example.childtrackingappiot.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.childtrackingappiot.utils.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URI
import java.util.ArrayDeque

class AudioPlayer(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private var webSocket: AudioWebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private var isAudioTrackPlaying = false

    private val audioBuffer = ArrayDeque<ByteArray>()
    private val bufferLock = Object()
    private var isProcessing = false

    fun initialize(deviceId: String, sampleRate: Int = 16000, channelCount: Int = 1) {
        try {
            setupAudioTrack(sampleRate, channelCount)
            setupWebSocket(deviceId)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error initializing: ${e.message}")
        }
    }

    private fun setupAudioTrack(sampleRate: Int, channelCount: Int) {
        if (audioTrack == null) {
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
        }
    }

    private fun setupWebSocket(deviceId: String) {
        if (webSocket == null) {
            val wsServer = PrefsManager.getInstance(context).getWsServer()
            if (wsServer.isNullOrEmpty()) {
                Log.e("AudioPlayer", "WebSocket server URL not configured")
                return
            }

            val wsUrl = "$wsServer/audio?type=android&deviceId=$deviceId"
            Log.d("AudioPlayer", "Initializing WebSocket: $wsUrl")
            
            webSocket = AudioWebSocket(
                URI(wsUrl),
                deviceId,
                onAudioDataReceived = { audioData ->
                    synchronized(bufferLock) {
                        audioBuffer.addLast(audioData)
                        if (!isProcessing) {
                            isProcessing = true
                            scope.launch(Dispatchers.IO) {
                                processAudioBuffer()
                            }
                        }
                    }
                },
                onConnectionStateChanged = { isConnected ->
                    Log.d("AudioPlayer", "WebSocket connection state: $isConnected")
                    _isPlaying.value = isConnected && isAudioTrackPlaying
                    
                    if (!isConnected) {
                        scope.launch {
                            delay(5000)
                            webSocket?.connect()
                        }
                    }
                }
            )
            webSocket?.connect()
        }
    }

    private suspend fun processAudioBuffer() {
        while (isAudioTrackPlaying) {
            val audioData = synchronized(bufferLock) {
                if (audioBuffer.isEmpty()) {
                    isProcessing = false
                    null
                } else {
                    audioBuffer.removeFirst()
                }
            } ?: break

            try {
                val written = audioTrack?.write(audioData, 0, audioData.size, AudioTrack.WRITE_BLOCKING)
                Log.d("AudioPlayer", "Written $written bytes to AudioTrack")
                delay(10) // Add small delay between writes
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error processing audio: ${e.message}")
                break
            }
        }
        isProcessing = false
    }

    fun startPlayback() {
        try {
            audioTrack?.play()
            isAudioTrackPlaying = true
            _isPlaying.value = webSocket?.isOpen == true
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error starting playback: ${e.message}")
        }
    }

    fun stopPlayback() {
        try {
            isAudioTrackPlaying = false
            synchronized(bufferLock) {
                audioBuffer.clear()
            }
            audioTrack?.pause()
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
            webSocket?.close()
            webSocket = null
            scope.cancel()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error releasing: ${e.message}")
        }
    }
} 