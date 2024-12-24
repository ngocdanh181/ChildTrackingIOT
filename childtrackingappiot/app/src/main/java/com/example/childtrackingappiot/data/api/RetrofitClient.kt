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
    private var baseUrl: String = ""
    private var retrofit: Retrofit? = null
    private var prefsManager: PrefsManager? = null

    fun init(context: Context) {
        prefsManager = PrefsManager.getInstance(context)
        baseUrl = prefsManager?.getHttpServer() ?: ""
    }

    fun updateBaseUrl(newBaseUrl: String) {
        baseUrl = newBaseUrl
        retrofit = null  // Force rebuild with new URL
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

    private fun getRetrofit(): Retrofit {
        if (retrofit == null) {
            if (baseUrl.isEmpty()) {
                throw IllegalStateException("Base URL not initialized. Call init() first")
            }
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
        }
        return retrofit!!
    }

    val authApi: AuthApi by lazy { getRetrofit().create(AuthApi::class.java) }
    val locationApi: LocationApi by lazy { getRetrofit().create(LocationApi::class.java) }
    val audioApi: AudioApi by lazy { getRetrofit().create(AudioApi::class.java) }
}