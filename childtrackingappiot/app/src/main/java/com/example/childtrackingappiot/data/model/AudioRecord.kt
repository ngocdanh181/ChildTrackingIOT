package com.example.childtrackingappiot.data.model

data class AudioStreamResponse(
    val success: Boolean,
    val data: AudioStreamStatus,
    val message: String? = null
)

data class AudioStreamStatus(
    val isListening: Boolean,
    val sampleRate: Int = 16000,
    val channelCount: Int = 1,
    val encoding: Int = 2
) 