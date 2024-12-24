package com.example.childtrackingappiot.ui.config

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.childtrackingappiot.MainActivity
import com.example.childtrackingappiot.data.api.RetrofitClient
import com.example.childtrackingappiot.data.model.AuthState
import com.example.childtrackingappiot.databinding.ActivityLoginBinding
import com.example.childtrackingappiot.databinding.ActivityServerConfigBinding
import com.example.childtrackingappiot.ui.auth.LoginActivity
import com.example.childtrackingappiot.utils.PrefsManager
import kotlinx.coroutines.launch

class ServerConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityServerConfigBinding
    private lateinit var prefsManager: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager.getInstance(this)

        // Load existing config
        binding.etHttpServer.setText(prefsManager.getHttpServer())
        binding.etWebSocketServer.setText(prefsManager.getWsServer())

        binding.btnSave.setOnClickListener {
            val httpServer = binding.etHttpServer.text.toString().trim()
            val wsServer = binding.etWebSocketServer.text.toString().trim()

            if (validateInputs(httpServer, wsServer)) {
                prefsManager.saveServerConfig(httpServer, wsServer)
                RetrofitClient.updateBaseUrl(httpServer)
                
                // Navigate to Login/Main Activity
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun validateInputs(httpServer: String, wsServer: String): Boolean {
        if (httpServer.isEmpty() || wsServer.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return false
        }

        // Add more validation if needed
        return true
    }
} 