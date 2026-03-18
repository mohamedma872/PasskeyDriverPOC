package com.example.passkeydriver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passkeydriver.data.Driver
import com.example.passkeydriver.data.DriverApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterDriverViewModel(private val api: DriverApi) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _createdDriver = MutableStateFlow<Driver?>(null)
    val createdDriver: StateFlow<Driver?> = _createdDriver.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun createDriver(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _createdDriver.value = api.createDriver(name)
            } catch (e: Exception) {
                _error.value = "Failed to create driver: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reset() {
        _createdDriver.value = null
        _error.value = null
    }

    fun clearError() { _error.value = null }
}
