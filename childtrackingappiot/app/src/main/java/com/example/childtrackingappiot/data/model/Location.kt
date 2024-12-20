package com.example.childtrackingappiot.data.model

data class Location(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

sealed class LocationState {
    data object Idle : LocationState()
    data object Loading : LocationState()
    data class Success(val latitude: Double, val longitude: Double, val address: String) : LocationState()
    data class HistorySuccess(val locations: List<Location>) : LocationState()
    data class Error(val message: String) : LocationState()
}