package com.example.childtrackingappiot.repository

import com.example.childtrackingappiot.data.api.LocationApi
import com.example.childtrackingappiot.data.model.Location
import com.example.childtrackingappiot.utils.Resource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(private val locationApi : LocationApi){
    suspend fun getCurrentLocation(deviceId: String): Resource<Location>{
        return try {
            val response = locationApi.getCurrentLocation(deviceId)
            if (response.isSuccessful) {
                response.body()?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Empty response body")
            } else {
                Resource.Error("Failed to get location: ${response.message()}")
            }
        }catch (e:Exception){
            Resource.Error("Netword error: ${e.localizedMessage}")
        }
    }

    suspend fun getLatestLocation(deviceId: String): Resource<Location> {
        return try {
            val response = locationApi.getLatestLocation(deviceId)
            if (response.isSuccessful) {
                response.body()?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Empty response body")
            } else {
                Resource.Error("Failed to get location: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }

    suspend fun getLocationHistory(deviceId: String): Resource<List<Location>> {
        return try {
            val response = locationApi.getLocationHistory(deviceId)
            if (response.isSuccessful) {
                response.body()?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Empty response body")
            } else {
                Resource.Error("Failed to get location history: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }
}