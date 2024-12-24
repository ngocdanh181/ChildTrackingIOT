package com.example.childtrackingappiot.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.childtrackingappiot.data.api.RetrofitClient
import com.example.childtrackingappiot.data.model.AuthState
import com.example.childtrackingappiot.repository.AuthRepository
import com.example.childtrackingappiot.utils.PrefsManager
import com.example.childtrackingappiot.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository.getInstance(application)
    private val prefsManager = PrefsManager.getInstance(application)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    /*init {
        // Check if user is already logged in
        prefsManager.getToken()?.let {
            _authState.value = AuthState.Success(it)
        }
    }*/

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = repository.login(email, password)) {
                is Resource.Success -> {
                    //RetrofitClient.setToken(result.data)
                    prefsManager.saveToken(result.data)
                    _authState.value = AuthState.Success(result.data)
                }
                is Resource.Error -> {
                    _authState.value = AuthState.Error(result.message)
                }
                is Resource.Loading -> {
                    _authState.value = AuthState.Loading
                }
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = repository.register(name, email, password)) {
                is Resource.Success -> {
                    //RetrofitClient.setToken(result.data)
                    prefsManager.saveToken(result.data)
                    _authState.value = AuthState.Success(result.data)
                }
                is Resource.Error -> {
                    _authState.value = AuthState.Error(result.message)
                }
                is Resource.Loading -> {
                    _authState.value = AuthState.Loading
                }
            }
        }
    }

    fun logout() {
        prefsManager.clearToken()
        _authState.value = AuthState.Idle
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}