package com.example.childtrackingappiot.ui.audio

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.childtrackingappiot.audio.AudioPlayer
import com.example.childtrackingappiot.repository.AudioRepository
import com.example.childtrackingappiot.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "AudioViewModel"
    private val repository: AudioRepository = AudioRepository.getInstance(application)
    private val audioPlayer: AudioPlayer = AudioPlayer()

    private val _audioState = MutableStateFlow<AudioState>(AudioState.Idle)
    val audioState: StateFlow<AudioState> = _audioState

    val isListening = audioPlayer.isPlaying

    init {
        try {
            audioPlayer.initialize(deviceId = "ESP32_001",sampleRate = 16000, channelCount = 1)
        } catch (e: Exception) {
            _audioState.value = AudioState.Error("Failed to initialize audio: ${e.message}")
        }
    }

    fun startListening(deviceId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Starting listening process for device: $deviceId")
            _audioState.value = AudioState.Loading
            try {
                when (val result = repository.startListening(deviceId)) {
                    is Resource.Success -> {
                        Log.d(TAG, "Server accepted start listening request")
                        audioPlayer.startPlayback()  // Chỉ start AudioTrack
                        _audioState.value = AudioState.Listening
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Failed to start listening: ${result.message}")
                        _audioState.value = AudioState.Error(result.message)
                    }
                    is Resource.Loading -> {
                        _audioState.value = AudioState.Loading
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in startListening", e)
                _audioState.value = AudioState.Error("Failed to start listening: ${e.message}")
            }
        }
    }

    fun stopListening(deviceId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Stopping listening process for device: $deviceId")
            try {
                audioPlayer.stopPlayback()
                
                when (val result = repository.stopListening(deviceId)) {
                    is Resource.Success -> {
                        Log.d(TAG, "Successfully stopped listening")
                        _audioState.value = AudioState.Idle
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Error stopping listening: ${result.message}")
                        _audioState.value = AudioState.Error(result.message)
                    }
                    is Resource.Loading -> {
                        _audioState.value = AudioState.Loading
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in stopListening", e)
                _audioState.value = AudioState.Error("Failed to stop listening: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, releasing resources")
        audioPlayer.release()
    }
}

sealed class AudioState {
    data object Idle : AudioState()
    data object Loading : AudioState()
    data object Listening : AudioState()
    data class Error(val message: String) : AudioState()
} 