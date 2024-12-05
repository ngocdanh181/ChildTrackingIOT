package com.example.childtrackingappiot.data.model

// Data/User.kt
data class User(
    val id: String,
    val name: String,
    val email: String
)

// Data/AuthRequest.kt
data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

// Data/AuthResponse.kt
data class AuthResponse(
    val success: Boolean,
    val token: String?,
    val error: String?
)
