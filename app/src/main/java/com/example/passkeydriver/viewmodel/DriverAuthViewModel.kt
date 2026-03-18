package com.example.passkeydriver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passkeydriver.data.Driver
import com.example.passkeydriver.data.DriverApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DriverAuthViewModel(private val api: DriverApi) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _authenticatedDriver = MutableStateFlow<Driver?>(null)
    val authenticatedDriver: StateFlow<Driver?> = _authenticatedDriver.asStateFlow()

    // PIN lockout
    private val _wrongAttempts = MutableStateFlow(0)
    val wrongAttempts: StateFlow<Int> = _wrongAttempts.asStateFlow()

    private val _lockedUntilMs = MutableStateFlow(0L)
    val lockedUntilMs: StateFlow<Long> = _lockedUntilMs.asStateFlow()

    private var lockoutJob: Job? = null

    fun verifyPin(driverId: String, pin: String) {
        if (System.currentTimeMillis() < _lockedUntilMs.value) {
            _error.value = "Too many attempts. Please wait."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val valid = api.verifyPin(driverId, pin)
                if (valid) {
                    _wrongAttempts.value = 0
                    val driver = api.getDriverById(driverId)
                    _authenticatedDriver.value = driver
                } else {
                    val attempts = _wrongAttempts.value + 1
                    _wrongAttempts.value = attempts
                    if (attempts >= 3) {
                        _lockedUntilMs.value = System.currentTimeMillis() + 30_000L
                        _error.value = "Too many wrong PINs. Locked for 30 seconds."
                        _wrongAttempts.value = 0
                        lockoutJob?.cancel()
                        lockoutJob = launch {
                            delay(30_000L)
                            _lockedUntilMs.value = 0L
                            _error.value = null
                        }
                    } else {
                        _error.value = "Wrong PIN. ${3 - attempts} attempt(s) remaining."
                    }
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun verifyPassword(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val driver = api.verifyPassword(username, password)
                if (driver != null) {
                    _authenticatedDriver.value = driver
                } else {
                    _error.value = "Invalid username or password."
                }
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearAuthenticated() { _authenticatedDriver.value = null }
    fun clearError() { _error.value = null }
    fun resetAttempts() {
        _wrongAttempts.value = 0
        _lockedUntilMs.value = 0L
        lockoutJob?.cancel()
    }
}
