package com.example.childtrackingappiot.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.childtrackingappiot.data.model.AuthResponse
import com.example.childtrackingappiot.repository.AuthRepository
import com.example.childtrackingappiot.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                repository.login(email, password).fold(
                    onSuccess = { response ->
                        preferencesManager.saveToken(response.token)
                        _authState.value = AuthState.Success(response)
                    },
                    onFailure = { exception ->
                        _authState.value = AuthState.Error(exception.message ?: "Unknown error")
                    }
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                repository.register(name, email, password).fold(
                    onSuccess = { response ->
                        preferencesManager.saveToken(response.token)
                        _authState.value = AuthState.Success(response)
                    },
                    onFailure = { exception ->
                        _authState.value = AuthState.Error(exception.message ?: "Unknown error")
                    }
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val response: AuthResponse) : AuthState()
    data class Error(val message: String) : AuthState()
}