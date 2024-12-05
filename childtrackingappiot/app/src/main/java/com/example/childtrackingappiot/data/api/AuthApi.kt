package com.example.childtrackingappiot.data.api

import com.example.childtrackingappiot.data.model.AuthResponse
import com.example.childtrackingappiot.data.model.LoginRequest
import com.example.childtrackingappiot.data.model.RegisterRequest
import com.example.childtrackingappiot.data.model.User
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi{
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @GET("api/auth/me")
    suspend fun getUserProfile(@Header("Authorization") token: String): Response<User>

}
