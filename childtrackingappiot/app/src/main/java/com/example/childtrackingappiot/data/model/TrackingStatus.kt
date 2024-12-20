package com.example.childtrackingappiot.data.model

data class TrackingStatus(
    val isTracking: Boolean,
    val trackingInterval: Int,
    val lastSeen: String
)

data class TrackingStatusResponse(
    val success: Boolean,
    val data: TrackingStatus,
    val message: String? = null
) 