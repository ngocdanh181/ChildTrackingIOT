package com.example.childtrackingappiot.data.api

import com.example.childtrackingappiot.data.model.AudioRecord
import retrofit2.Response
import retrofit2.http.*

interface AudioApi {
    @POST("api/audio/record/{deviceId}/start")
    suspend fun startRecording(@Path("deviceId") deviceId: String): Response<Unit>

    @POST("api/audio/record/{deviceId}/stop")
    suspend fun stopRecording(@Path("deviceId") deviceId: String): Response<Unit>

    @GET("api/audio/records/{deviceId}")
    suspend fun getRecords(@Path("deviceId") deviceId: String): Response<List<AudioRecord>>

    @GET("api/audio/status/{deviceId}")
    suspend fun getStatus(@Path("deviceId") deviceId: String): Response<Map<String, Boolean>>

    @DELETE("api/audio/{id}")
    suspend fun deleteRecord(@Path("id") id: String): Response<Unit>
} 