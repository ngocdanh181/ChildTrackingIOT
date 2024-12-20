package com.example.childtrackingappiot.repository

import android.content.Context
import com.example.childtrackingappiot.data.api.LocationApi
import com.example.childtrackingappiot.data.api.RetrofitClient
import com.example.childtrackingappiot.data.model.Location
import com.example.childtrackingappiot.data.model.TrackingStatus
import com.example.childtrackingappiot.utils.Resource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LocationRepository private constructor(private val context: Context) {
    private val locationApi: LocationApi = RetrofitClient.locationApi

    suspend fun getLatestLocation(deviceId: String): Resource<Location> {
        return try {
            val response = locationApi.getLatestLocation(deviceId)
            if (response.isSuccessful) {
                response.body()?.let { locationResponse ->
                    if (locationResponse.success) {
                        Resource.Success(locationResponse.data)
                    } else {
                        Resource.Error("Failed to get location")
                    }
                } ?: Resource.Error("Empty response body")
            } else {
                Resource.Error("Failed to get location: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }

    suspend fun startTracking(deviceId: String, interval: Int = 10): Resource<TrackingStatus> {
        return try {
            val response = locationApi.startTracking(deviceId, mapOf("interval" to interval))
            if (response.isSuccessful) {
                response.body()?.let { statusResponse ->
                    if (statusResponse.success) {
                        Resource.Success(statusResponse.data)
                    } else {
                        Resource.Error(statusResponse.message ?: "Failed to start tracking")
                    }
                } ?: Resource.Error("Empty response body")
            } else {
                Resource.Error("Failed to start tracking: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }

    suspend fun stopTracking(deviceId: String): Resource<TrackingStatus> {
        return try {
            val response = locationApi.stopTracking(deviceId)
            if (response.isSuccessful) {
                response.body()?.let { statusResponse ->
                    if (statusResponse.success) {
                        Resource.Success(statusResponse.data)
                    } else {
                        Resource.Error(statusResponse.message ?: "Failed to stop tracking")
                    }
                } ?: Resource.Error("Empty response body")
            } else {
                Resource.Error("Failed to stop tracking: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }

    // Flow để liên tục lấy vị trí mới nhất
    fun trackLocationUpdates(deviceId: String, interval: Long = 5000L): Flow<Resource<Location>> = flow {
        while (true) {
            emit(getLatestLocation(deviceId))
            delay(interval)
        }
    }

    companion object {
        @Volatile
        private var instance: LocationRepository? = null

        fun getInstance(context: Context): LocationRepository {
            return instance ?: synchronized(this) {
                instance ?: LocationRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}