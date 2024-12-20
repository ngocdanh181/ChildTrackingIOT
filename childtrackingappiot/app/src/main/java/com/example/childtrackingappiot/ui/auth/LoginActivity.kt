package com.example.childtrackingappiot.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.childtrackingappiot.MainActivity
import com.example.childtrackingappiot.data.model.AuthState
import com.example.childtrackingappiot.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels { 
        AuthViewModel.Factory(application) 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        observeAuthState()
    }

    private fun setupViews() {
        binding.loginButton.setOnClickListener {
            if (validateInput()) {
                val email = binding.emailInput.text.toString()
                val password = binding.passwordInput.text.toString()
                viewModel.login(email, password)
            }
        }

        binding.registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> {
                        binding.loginButton.isEnabled = false
                        binding.progressBar.isVisible = true
                    }
                    is AuthState.Success -> {
                        // Navigate to MainActivity on successful login
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish() // Close LoginActivity
                    }
                    is AuthState.Error -> {
                        binding.loginButton.isEnabled = true
                        binding.progressBar.isVisible = false
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    is AuthState.Idle -> {
                        binding.loginButton.isEnabled = true
                        binding.progressBar.isVisible = false
                    }
                }
            }
        }
    }

    private fun validateInput(): Boolean {
        val email = binding.emailInput.text.toString()
        val password = binding.passwordInput.text.toString()
        
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInput.error = "Valid email is required"
            return false
        }
        
        if (password.isEmpty()) {
            binding.passwordInput.error = "Password is required"
            return false
        }
        
        return true
    }
}