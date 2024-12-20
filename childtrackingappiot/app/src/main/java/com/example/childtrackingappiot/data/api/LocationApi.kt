package com.example.childtrackingappiot.data.api

import com.example.childtrackingappiot.data.model.LocationResponse
import com.example.childtrackingappiot.data.model.TrackingStatusResponse
import retrofit2.Response
import retrofit2.http.*

interface LocationApi {
    @GET("api/locations/latest/{deviceId}")   
    suspend fun getLatestLocation(@Path("deviceId") deviceId: String): Response<LocationResponse>

    @GET("api/locations/track/{deviceId}/status")
    suspend fun getTrackingStatus(@Path("deviceId") deviceId: String): Response<TrackingStatusResponse>

    @POST("api/locations/track/{deviceId}/start")
    suspend fun startTracking(
        @Path("deviceId") deviceId: String,
        @Body interval: Map<String, Int>
    ): Response<TrackingStatusResponse>

    @POST("api/locations/track/{deviceId}/stop")
    suspend fun stopTracking(@Path("deviceId") deviceId: String): Response<TrackingStatusResponse>
}