package com.example.passkeydriver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passkeydriver.data.Driver
import com.example.passkeydriver.data.DriverApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminViewModel(private val api: DriverApi) : ViewModel() {

    companion object {
        private const val ADMIN_PIN = "1234"
    }

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    private val _drivers = MutableStateFlow<List<Driver>>(emptyList())
    val drivers: StateFlow<List<Driver>> = _drivers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun validatePin(pin: String) {
        if (pin == ADMIN_PIN) {
            _isLoggedIn.value = true
            _pinError.value = false
            loadDrivers()
        } else {
            _pinError.value = true
        }
    }

    fun clearPinError() {
        _pinError.value = false
    }

    fun logout() {
        _isLoggedIn.value = false
        _drivers.value = emptyList()
    }

    fun loadDrivers() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _drivers.value = api.getDrivers()
            } catch (e: Exception) {
                _error.value = "Failed to load drivers: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}
