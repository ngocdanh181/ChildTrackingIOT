package com.example.childtrackingappiot.audio

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer

class AudioWebSocket(
    serverUri: URI,
    private val deviceId: String,
    private val onAudioDataReceived: (ByteArray) -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit
) : WebSocketClient(serverUri) {

    override fun onOpen(handshakedata: ServerHandshake?) {
        Log.d(TAG, "WebSocket Connected to: $uri")
        onConnectionStateChanged(true)
    }

    override fun onMessage(message: String) {
        // Handle text messages if needed
        Log.d(TAG, "Received text message: $message")
    }

    override fun onMessage(bytes: ByteBuffer) {
        try {
            val audioData = bytes.array()
            Log.d(TAG, "Received audio data: ${audioData.size} bytes")
            onAudioDataReceived(audioData)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio data: ${e.message}")
        }
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