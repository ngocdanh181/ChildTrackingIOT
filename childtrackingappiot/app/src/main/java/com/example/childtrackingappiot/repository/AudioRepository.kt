package com.example.childtrackingappiot.repository

import android.content.Context
import com.example.childtrackingappiot.data.api.AudioApi
import com.example.childtrackingappiot.data.api.RetrofitClient
import com.example.childtrackingappiot.utils.Resource

class AudioRepository private constructor(private val context: Context) {
    private val audioApi: AudioApi = RetrofitClient.audioApi

    suspend fun startListening(deviceId: String): Resource<Unit> {
        return try {
            val response = audioApi.startListening(deviceId)
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to start listening: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }

    suspend fun stopListening(deviceId: String): Resource<Unit> {
        return try {
            val response = audioApi.stopListening(deviceId)
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to stop listening: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }

    companion object {
        @Volatile
        private var instance: AudioRepository? = null

        fun getInstance(context: Context): AudioRepository {
            return instance ?: synchronized(this) {
                instance ?: AudioRepository(context.applicationContext).also { instance = it }
            }
        }
    }
} 