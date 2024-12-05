package com.example.childtrackingappiot.data.model

data class Location(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val accuracy: Float? = null,
    val speed: Float? = null
)

sealed class LocationState {
    object Idle : LocationState()
    object Loading : LocationState()
    data class Success(val location: Location) : LocationState()
    data class HistorySuccess(val locations: List<Location>) : LocationState()
    data class Error(val message: String) : LocationState()
}