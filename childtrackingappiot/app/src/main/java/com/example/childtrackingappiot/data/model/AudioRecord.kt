package com.example.childtrackingappiot.data.model

data class AudioRecord(
    val id: String,
    val deviceId: String,
    val startTime: String,
    val endTime: String?,
    val duration: Int?,
    val fileUrl: String?
) 