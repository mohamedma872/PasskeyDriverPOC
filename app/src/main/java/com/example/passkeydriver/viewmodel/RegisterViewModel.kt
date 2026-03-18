package com.example.passkeydriver.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passkeydriver.auth.PasskeyManager
import com.example.passkeydriver.auth.PasskeyResult
import com.example.passkeydriver.data.DriverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val passkeyManager: PasskeyManager
) : ViewModel() {

    companion object {
        private const val TAG = "RegisterViewModel"
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _registrationSuccess = MutableStateFlow(false)
    val registrationSuccess: StateFlow<Boolean> = _registrationSuccess.asStateFlow()

    private var activityRef: Activity? = null

    fun setActivity(activity: Activity) {
        activityRef = activity
    }

    fun register(username: String, password: String) {
        val activity = activityRef ?: run {
            Log.e(TAG, "Activity reference is null")
            _error.value = "Activity not available"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            Log.d(TAG, "=== REGISTRATION FLOW START === username=$username")

            // Step 1: Verify credentials with mock backend
            val driver = DriverRepository.verifyCredentials(username, password)
            if (driver == null) {
                Log.e(TAG, "Credential verification failed for username=$username")
                _error.value = "Invalid username or password"
                _isLoading.value = false
                return@launch
            }
            Log.d(TAG, "Credentials verified: driver=${driver.id}, hasPasskey=${driver.hasPasskey}")

            if (driver.hasPasskey) {
                Log.w(TAG, "Driver ${driver.id} already has a passkey")
                _error.value = "This driver already has a passkey registered"
                _isLoading.value = false
                return@launch
            }

            // Step 2: Create passkey
            Log.d(TAG, "Calling passkeyManager.registerPasskey...")
            val result = passkeyManager.registerPasskey(
                context = activity,
                userId = driver.id,
                username = driver.username,
                displayName = driver.displayName
            )

            when (result) {
                is PasskeyResult.Success -> {
                    Log.d(TAG, "=== REGISTRATION FLOW SUCCESS === credentialId=${result.data}")
                    DriverRepository.markPasskeyRegistered(driver.id, result.data)
                    _registrationSuccess.value = true
                }
                is PasskeyResult.Error -> {
                    Log.e(TAG, "=== REGISTRATION FLOW FAILED === error=${result.message}")
                    _error.value = result.message
                }
                is PasskeyResult.Cancelled -> {
                    Log.w(TAG, "=== REGISTRATION FLOW CANCELLED by user ===")
                }
            }

            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccess() {
        _registrationSuccess.value = false
    }
}
