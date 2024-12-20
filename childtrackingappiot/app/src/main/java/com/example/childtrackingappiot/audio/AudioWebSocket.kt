package com.example.childtrackingappiot.audio

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer

class AudioWebSocket(
    private val serverUri: URI,
    private val deviceId: String,
    private val onAudioDataReceived: (ByteArray) -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit
) : WebSocketClient(serverUri) {

    init {
        addHeader("type", "android")
        addHeader("deviceId", deviceId)
    }

    override fun onOpen(handshakedata: ServerHandshake?) {
        Log.d(TAG, "WebSocket Connected")
        onConnectionStateChanged(true)
    }

    override fun onMessage(message: String) {
        // Handle text messages if needed
    }

    override fun onMessage(bytes: ByteBuffer) {
        // Handle binary audio data
        val audioData = bytes.array()
        onAudioDataReceived(audioData)
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        Log.d(TAG, "WebSocket Closed: $reason")
        onConnectionStateChanged(false)
    }

    override fun onError(ex: Exception?) {
        Log.e(TAG, "WebSocket Error: ${ex?.message}")
        onConnectionStateChanged(false)
    }

    companion object {
        private const val TAG = "AudioWebSocket"
    }
} 