package com.example.childtrackingappiot.data.api

import com.example.childtrackingappiot.data.model.Location
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface LocationApi {
    @GET("api/location/current/{deviceId}")
    suspend fun getCurrentLocation(@Path("deviceId") deviceId: String): Response<Location>

    @GET("api/location/latest/{deviceId}")
    suspend fun getLatestLocation(@Path("deviceId") deviceId: String): Response<Location>

    @GET("api/location/history/{deviceId}")
    suspend fun getLocationHistory(@Path("deviceId") deviceId: String): Response<List<Location>>
} 