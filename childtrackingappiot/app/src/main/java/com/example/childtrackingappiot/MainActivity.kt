package com.example.childtrackingappiot

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.childtrackingappiot.data.model.LocationState
import com.example.childtrackingappiot.databinding.ActivityMainBinding
import com.example.childtrackingappiot.ui.location.LocationViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding
    private val locationViewModel: LocationViewModel by viewModels()
    
    // Hardcoded device ID for testing
    private val deviceId = "ESP32_001"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Set device ID
        binding.tvDeviceId.text = "Device ID: $deviceId"

        setupViews()
        observeViewModel()
    }
    private fun setupViews() {
        binding.btnGetLocation.setOnClickListener {
            locationViewModel.getCurrentLocation(deviceId)
        }
    }
    private fun observeViewModel() {
        lifecycleScope.launch {
            locationViewModel.locationState.collect { state ->
                when (state) {
                    is LocationState.Loading -> {
                        binding.btnGetLocation.isEnabled = false
                        binding.progressBar.isVisible = true
                    }
                    is LocationState.Success -> {
                        binding.btnGetLocation.isEnabled = true
                        binding.progressBar.isVisible = false
                        binding.tvLocation.text = "Location: Lat ${state.location.latitude}, Lng ${state.location.longitude}"
                    }
                    is LocationState.Error -> {
                        binding.btnGetLocation.isEnabled = true
                        binding.progressBar.isVisible = false
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    is LocationState.Idle -> {
                        binding.btnGetLocation.isEnabled = true
                        binding.progressBar.isVisible = false
                    }
                    is LocationState.HistorySuccess -> {
                        // Handle history display if needed
                    }
                }
            }
        }
    }

}