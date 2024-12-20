package com.example.childtrackingappiot.data.api

import com.example.childtrackingappiot.data.model.AuthResponse
import com.example.childtrackingappiot.data.model.LoginRequest
import com.example.childtrackingappiot.data.model.RegisterRequest
import com.example.childtrackingappiot.data.model.User
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<AuthResponse>

    @GET("api/auth/me")
    suspend fun getUserProfile(): Response<User>
}
