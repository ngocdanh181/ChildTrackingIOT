package com.example.childtrackingappiot.ui.audio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.childtrackingappiot.audio.AudioPlayer
import com.example.childtrackingappiot.repository.AudioRepository
import com.example.childtrackingappiot.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AudioRepository = AudioRepository.getInstance(application)
    private val audioPlayer: AudioPlayer = AudioPlayer()

    private val _audioState = MutableStateFlow<AudioState>(AudioState.Idle)
    val audioState: StateFlow<AudioState> = _audioState

    val isListening = audioPlayer.isPlaying

    fun startListening(deviceId: String) {
        viewModelScope.launch {
            _audioState.value = AudioState.Loading
            try {
                // First, tell server to start ESP32 recording
                when (val result = repository.startListening(deviceId)) {
                    is Resource.Success -> {
                        // Then initialize WebSocket connection
                        audioPlayer.initialize(deviceId)
                        audioPlayer.startPlayback()
                        _audioState.value = AudioState.Listening
                    }
                    is Resource.Error -> {
                        _audioState.value = AudioState.Error(result.message)
                    }
                    is Resource.Loading -> {
                        _audioState.value = AudioState.Loading
                    }
                }
            } catch (e: Exception) {
                _audioState.value = AudioState.Error("Failed to start listening: ${e.message}")
            }
        }
    }

    fun stopListening(deviceId: String) {
        viewModelScope.launch {
            try {
                // First stop WebSocket connection
                audioPlayer.stopPlayback()
                
                // Then tell server to stop ESP32 recording
                when (val result = repository.stopListening(deviceId)) {
                    is Resource.Success -> {
                        _audioState.value = AudioState.Idle
                    }
                    is Resource.Error -> {
                        _audioState.value = AudioState.Error(result.message)
                    }
                    is Resource.Loading -> {
                        _audioState.value = AudioState.Loading
                    }
                }
            } catch (e: Exception) {
                _audioState.value = AudioState.Error("Failed to stop listening: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}

sealed class AudioState {
    data object Idle : AudioState()
    data object Loading : AudioState()
    data object Listening : AudioState()
    data class Error(val message: String) : AudioState()
} 