package com.example.childtrackingappiot.data.model

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

data class AuthResponse(
    val success: Boolean,
    val token: String
)

data class User(
    val id: String,
    val name: String,
    val email: String
)

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val token: String) : AuthState()
    data class Error(val message: String) : AuthState()
}