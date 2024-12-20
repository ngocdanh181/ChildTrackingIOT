package com.example.childtrackingappiot

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.childtrackingappiot.data.model.LocationState
import com.example.childtrackingappiot.databinding.ActivityMainBinding
import com.example.childtrackingappiot.ui.location.LocationViewModel
import com.example.childtrackingappiot.ui.audio.AudioViewModel
import com.example.childtrackingappiot.ui.audio.AudioState
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val locationViewModel: LocationViewModel by viewModels()
    private val audioViewModel: AudioViewModel by viewModels()
    
    private val deviceId = "ESP32_001"
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        //setupMQTT()
        observeLocationUpdates()
        observeAudioState()
    }

    @SuppressLint("SetTextI18n")
    private fun setupViews() {
        binding.tvDeviceId.text = "Device ID: $deviceId"
        
        // Location tracking button
        binding.btnTrackLocation.setOnClickListener {
            if (locationViewModel.isTracking) {
                locationViewModel.stopTracking(deviceId)
                binding.btnTrackLocation.text = "Start Tracking"
            } else {
                locationViewModel.startTracking(deviceId)
                binding.btnTrackLocation.text = "Stop Tracking"
            }
        }

        // Audio listening button
        binding.btnListen.setOnClickListener {
            if (audioViewModel.isListening.value) {
                audioViewModel.stopListening(deviceId)
                binding.btnListen.text = "Start Listening"
            } else {
                audioViewModel.startListening(deviceId)
                binding.btnListen.text = "Stop Listening"
            }
        }
    }

    /*private fun setupMQTT() {
        try {
            val serverUri = "tcp://192.168.1.5:1883"
            val clientId = "AndroidApp_${System.currentTimeMillis()}"
            mqttClient = MqttClient(serverUri, clientId, null)

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 30
                keepAliveInterval = 60
                isAutomaticReconnect = true
            }

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    binding.audioError.text = "Connection lost : ${cause?.message}"
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.let {
                        val payload = String(it.payload)
                        try {
                            val json = JSONObject(payload)
                            val audioData = json.getString("data")
                            runOnUiThread {  // Quan trọng: UI updates phải chạy trên main thread
                                binding.tvLog.text = "Gia tri cua audioData la : $audioData"
                            }
                            //audioViewModel.processAudioChunk(audioData)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    //println("Delivery complete")
                    binding.audioError.text = "Delivery complete"
                    //Toast.makeText(this, "Delivery complete", Toast.LENGTH_SHORT).show()
                }
            })

            mqttClient?.connect(options)

            if (mqttClient?.isConnected == true) {
                mqttClient?.subscribe("device/$deviceId/audio/stream")
                binding.audioStatus.text = "MQTT Connected and subscribed"  // Thêm log này
            } else {
                binding.audioStatus.text = "MQTT Connection failed"  // Thêm log này
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "MQTT Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }*/

    @SuppressLint("SetTextI18n")
    private fun observeLocationUpdates() {
        lifecycleScope.launch {
            locationViewModel.locationState.collect { state ->
                when (state) {
                    is LocationState.Loading -> {
                        binding.progressBar.isVisible = true
                        binding.btnTrackLocation.isEnabled = false
                    }
                    is LocationState.Success -> {
                        binding.progressBar.isVisible = false
                        binding.btnTrackLocation.isEnabled = true
                        binding.tvLocation.text = 
                            "Location: Lat ${state.latitude}, Lng ${state.longitude}\nAddress: ${state.address}"
                    }
                    is LocationState.Error -> {
                        binding.progressBar.isVisible = false
                        binding.btnTrackLocation.isEnabled = true
                        binding.btnTrackLocation.text = "Start Tracking"
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        binding.progressBar.isVisible = false
                        binding.btnTrackLocation.isEnabled = true
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun observeAudioState() {
        lifecycleScope.launch {
            audioViewModel.audioState.collect { state ->
                when (state) {
                    is AudioState.Loading -> {
                        binding.btnListen.isEnabled = false
                        binding.audioProgressBar.isVisible = true
                    }

                    is AudioState.Listening -> {
                        binding.btnListen.isEnabled = true
                        binding.audioProgressBar.isVisible = false
                        binding.audioStatus.text = "Listening..."
                        binding.btnListen.text = "Stop Listening"
                    }

                    is AudioState.Error -> {
                        binding.btnListen.isEnabled = true
                        binding.audioProgressBar.isVisible = false
                        binding.btnListen.text = "Start Listening"
                        binding.audioStatus.text = "Error: ${state.message}"
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_SHORT).show()
                    }

                    is AudioState.Idle -> {
                        binding.btnListen.isEnabled = true
                        binding.audioProgressBar.isVisible = false
                        binding.audioStatus.text = "Not listening"
                        binding.btnListen.text = "Start Listening"
                    }
                }
            }
        }
        // Observe WebSocket connection state
        lifecycleScope.launch {
            audioViewModel.isListening.collect { isListening ->
                binding.audioStatus.text = if (isListening) "Connected to audio stream" else "Disconnected"
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        //mqttClient?.disconnect()
        //mqttClient?.close()
    }
}