package com.example.childtrackingappiot.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.childtrackingappiot.MainActivity
import com.example.childtrackingappiot.databinding.ActivityRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> {
                        binding.registerButton.isEnabled = false
                        binding.progressBar.isVisible = true
                    }
                    is AuthState.Success -> {
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finishAffinity()
                    }
                    is AuthState.Error -> {
                        binding.registerButton.isEnabled = true
                        binding.progressBar.isVisible = false
                        Toast.makeText(this@RegisterActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    is AuthState.Idle -> {
                        binding.registerButton.isEnabled = true
                        binding.progressBar.isVisible = false
                    }
                }
            }
        }
    }

    private fun setupViews(){
        binding.registerButton.setOnClickListener {
            if (validateInputs()) {
                val name = binding.nameInput.text.toString()
                val email = binding.emailInput.text.toString()
                val password = binding.passwordInput.text.toString()
                viewModel.register(name, email, password)
            }
        }
        binding.loginLink.setOnClickListener {
            finish() // Quay lại màn Login
        }
    }

    private fun validateInputs(): Boolean {
        val name = binding.nameInput.text.toString()
        val email = binding.emailInput.text.toString()
        val password = binding.passwordInput.text.toString()
        val confirmPassword = binding.confirmPasswordInput.text.toString()

        if (name.isEmpty()) {
            binding.nameInput.error = "Name is required"
            return false
        }

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInput.error = "Valid email is required"
            return false
        }

        if (password.length < 6) {
            binding.passwordInput.error = "Password must be at least 6 characters"
            return false
        }

        if (password != confirmPassword) {
            binding.confirmPasswordInput.error = "Passwords do not match"
            return false
        }

        return true
    }
}




