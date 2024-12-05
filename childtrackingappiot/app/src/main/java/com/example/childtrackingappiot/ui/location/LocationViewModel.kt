package com.example.childtrackingappiot.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.childtrackingappiot.data.model.LocationState
import com.example.childtrackingappiot.repository.LocationRepository
import com.example.childtrackingappiot.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(private val repository: LocationRepository):ViewModel(){
    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    fun getCurrentLocation(deviceId: String) {
        viewModelScope.launch {
            _locationState.value = LocationState.Loading
            when (val result = repository.getCurrentLocation(deviceId)) {
                is Resource.Success -> _locationState.value = LocationState.Success(result.data)
                is Resource.Error -> _locationState.value = LocationState.Error(result.message)
                is Resource.Loading -> _locationState.value = LocationState.Loading
            }
        }
    }

    fun getLatestLocation(deviceId: String) {
        viewModelScope.launch {
            _locationState.value = LocationState.Loading
            when (val result = repository.getLatestLocation(deviceId)) {
                is Resource.Success -> _locationState.value = LocationState.Success(result.data)
                is Resource.Error -> _locationState.value = LocationState.Error(result.message)
                is Resource.Loading -> _locationState.value = LocationState.Loading
            }
        }
    }

    fun getLocationHistory(deviceId: String) {
        viewModelScope.launch {
            _locationState.value = LocationState.Loading
            when (val result = repository.getLocationHistory(deviceId)) {
                is Resource.Success -> _locationState.value = LocationState.HistorySuccess(result.data)
                is Resource.Error -> _locationState.value = LocationState.Error(result.message)
                is Resource.Loading -> _locationState.value = LocationState.Loading
            }
        }
    }
}