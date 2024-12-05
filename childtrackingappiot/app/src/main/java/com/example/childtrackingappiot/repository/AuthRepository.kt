package com.example.childtrackingappiot.repository

import com.example.childtrackingappiot.data.api.AuthApi
import com.example.childtrackingappiot.data.model.AuthResponse
import com.example.childtrackingappiot.data.model.LoginRequest
import com.example.childtrackingappiot.data.model.RegisterRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(private val authApi: AuthApi) {
    suspend fun login(email: String, password: String): Result<AuthResponse>{
        return try{
            val response = authApi.login(LoginRequest(email,password))
            if (response.isSuccessful){
                Result.success(response.body()!!)
            }else{
                Result.failure(Exception(response.errorBody()?.string()))
            }
        }catch(e:Exception){
            Result.failure(e)
        }
    }

    suspend fun register(name: String,email: String, password:String): Result<AuthResponse>{
        return try {
            val response = authApi.register(RegisterRequest(name,email,password))
            if(response.isSuccessful){
                Result.success(response.body()!!)
            }else{
                Result.failure(Exception(response.errorBody()?.string()))
            }
        }catch (e:Exception){
            Result.failure(e)
        }
    }
}