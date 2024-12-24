package com.example.childtrackingappiot.ui.location

import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.childtrackingappiot.data.model.LocationState
import com.example.childtrackingappiot.repository.LocationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.example.childtrackingappiot.utils.Resource
import java.util.Locale


class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: LocationRepository = LocationRepository.getInstance(application)
    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState
    
    private var trackingJob: Job? = null
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: Boolean
        get() = _isTracking.value

    fun startTracking(deviceId: String) {
        if (_isTracking.value) return
        
        viewModelScope.launch {
            _locationState.value = LocationState.Loading
            when (val result = repository.startTracking(deviceId)) {
                is Resource.Success -> {
                    _isTracking.value = true
                    startLocationUpdates(deviceId)
                }
                is Resource.Error -> {
                    _locationState.value = LocationState.Error(result.message)
                }
                is Resource.Loading -> {
                    _locationState.value = LocationState.Loading
                }
            }
        }
    }

    fun stopTracking(deviceId: String) {
        viewModelScope.launch {
            when (val result = repository.stopTracking(deviceId)) {
                is Resource.Success -> {
                    _isTracking.value = false
                    trackingJob?.cancel()
                    _locationState.value = LocationState.Idle
                }
                is Resource.Error -> {
                    _locationState.value = LocationState.Error(result.message)
                }
                is Resource.Loading -> {
                    _locationState.value = LocationState.Loading
                }
            }
        }
    }

    private fun startLocationUpdates(deviceId: String) {
    trackingJob?.cancel()
    trackingJob = viewModelScope.launch {
        repository.trackLocationUpdates(deviceId)
            .catch { e ->
                _locationState.value = LocationState.Error(e.message ?: "Error tracking location")
            }
            .collect { result ->
                when (result) {
                    is Resource.Success -> {
                        val location = result.data
                        val currentTime = System.currentTimeMillis()
                        val locationTime = location.updatedAt // Assuming location has a time property in milliseconds

                        if (locationTime != null) {
                            if ((currentTime - locationTime.toLong()) > 15 * 60 * 1000) { // 15 minutes in milliseconds
                                _locationState.value = LocationState.Error("Location data is older than 15 minutes")
                            } else {
                                val address = getAddressFromCoordinates(location.latitude, location.longitude)
                                _locationState.value = LocationState.Success(location.latitude, location.longitude, address)
                            }
                        }
                    }
                    is Resource.Error -> {
                        _locationState.value = LocationState.Error(result.message)
                    }
                    is Resource.Loading -> {
                        _locationState.value = LocationState.Loading
                    }
                }
            }
    }
}

    private fun getAddressFromCoordinates(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(getApplication(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                address.getAddressLine(0) ?: "Unknown address"
            } else {
                "Unknown address"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    override fun onCleared() {
        super.onCleared()
        trackingJob?.cancel()
    }
}