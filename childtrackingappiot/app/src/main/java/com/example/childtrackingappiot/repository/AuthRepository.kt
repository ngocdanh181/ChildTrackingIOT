package com.example.childtrackingappiot.repository

import android.content.Context
import com.example.childtrackingappiot.data.api.RetrofitClient
import com.example.childtrackingappiot.data.model.LoginRequest
import com.example.childtrackingappiot.data.model.RegisterRequest
import com.example.childtrackingappiot.utils.Resource

class AuthRepository private constructor(private val context: Context) {
    private val authApi = RetrofitClient.authApi

    suspend fun login(email: String, password: String): Resource<String> {
        return try {
            val response = authApi.login(LoginRequest(email, password))
            if (response.isSuccessful) {
                response.body()?.let { authResponse ->
                    if (authResponse.success) {
                        Resource.Success(authResponse.token)
                    } else {
                        Resource.Error("Login failed")
                    }
                } ?: Resource.Error("Empty response body")
            } else {
                Resource.Error("Login failed: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }

    suspend fun register(name: String, email: String, password: String): Resource<String> {
        return try {
            val response = authApi.register(RegisterRequest(name, email, password))
            if (response.isSuccessful) {
                response.body()?.let { authResponse ->
                    if (authResponse.success) {
                        Resource.Success(authResponse.token)
                    } else {
                        Resource.Error("Registration failed")
                    }
                } ?: Resource.Error("Empty response body")
            } else {
                Resource.Error("Registration failed: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }

    companion object {
        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}