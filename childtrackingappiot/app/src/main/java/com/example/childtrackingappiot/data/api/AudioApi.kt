package com.example.childtrackingappiot.data.api

import com.example.childtrackingappiot.data.model.AudioStreamResponse
import retrofit2.Response
import retrofit2.http.*

interface AudioApi {
    @POST("api/audio/listen/{deviceId}/start")
    suspend fun startListening(@Path("deviceId") deviceId: String): Response<AudioStreamResponse>

    @POST("api/audio/listen/{deviceId}/stop")
    suspend fun stopListening(@Path("deviceId") deviceId: String): Response<AudioStreamResponse>
} 