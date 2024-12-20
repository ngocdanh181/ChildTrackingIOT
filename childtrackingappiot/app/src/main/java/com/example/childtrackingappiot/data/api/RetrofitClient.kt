package com.example.childtrackingappiot.data.api

import android.content.Context
import com.example.childtrackingappiot.utils.PrefsManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://192.168.1.5:3000/"
    private var prefsManager: PrefsManager? = null

    fun init(context: Context) {
        prefsManager = PrefsManager.getInstance(context)
    }

    fun setToken(newToken: String) {
        prefsManager?.saveToken(newToken)
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .apply {
                prefsManager?.getToken()?.let {
                    addHeader("Authorization", "Bearer $it")
                }
            }
            .build()
        
        val response = chain.proceed(request)
        
        // Handle unauthorized response
        if (response.code == 401) {
            prefsManager?.clearToken()
        }
        
        response
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val locationApi: LocationApi = retrofit.create(LocationApi::class.java)
    val audioApi: AudioApi = retrofit.create(AudioApi::class.java)
}