package com.example.childtrackingappiot.repository

import android.content.Context
import android.util.Log
import com.example.childtrackingappiot.data.api.AudioApi
import com.example.childtrackingappiot.data.api.RetrofitClient
import com.example.childtrackingappiot.utils.Resource

class AudioRepository private constructor(private val context: Context) {
    private val audioApi: AudioApi = RetrofitClient.audioApi
    private val TAG = "AudioRepository"

    suspend fun startListening(deviceId: String): Resource<Unit> {
        return try {
            Log.d(TAG, "Starting listening for device: $deviceId")
            val response = audioApi.startListening(deviceId)
            if (response.isSuccessful) {
                Log.d(TAG, "Successfully started listening")
                Resource.Success(Unit)
            } else {
                Log.e(TAG, "Failed to start listening: ${response.message()}")
                Resource.Error("Failed to start listening: ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error while starting listening", e)
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }

    suspend fun stopListening(deviceId: String): Resource<Unit> {
        return try {
            Log.d(TAG, "Stopping listening for device: $deviceId")
            val response = audioApi.stopListening(deviceId)
            if (response.isSuccessful) {
                Log.d(TAG, "Successfully stopped listening")
                Resource.Success(Unit)
            } else {
                Log.e(TAG, "Failed to stop listening: ${response.message()}")
                Resource.Error("Failed to stop listening: ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error while stopping listening", e)
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