package com.example.passkeydriver.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passkeydriver.auth.PasskeyManager
import com.example.passkeydriver.auth.PasskeyResult
import com.example.passkeydriver.data.Driver
import com.example.passkeydriver.data.DriverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DriverListViewModel(
    private val passkeyManager: PasskeyManager
) : ViewModel() {

    val passkeyDrivers: StateFlow<List<Driver>> = DriverRepository.drivers
        .map { it.filter { driver -> driver.hasPasskey } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _authenticatedDriverId = MutableStateFlow<String?>(null)
    val authenticatedDriverId: StateFlow<String?> = _authenticatedDriverId.asStateFlow()

    private var activityRef: Activity? = null

    fun setActivity(activity: Activity) {
        activityRef = activity
    }

    fun authenticateDriver(driver: Driver) {
        val activity = activityRef ?: run {
            _error.value = "Activity not available"
            return
        }

        viewModelScope.launch {
            _isAuthenticating.value = true
            _error.value = null

            val result = passkeyManager.authenticateWithPasskey(
                context = activity,
                credentialId = driver.credentialId
            )

            when (result) {
                is PasskeyResult.Success -> {
                    _authenticatedDriverId.value = result.data
                }
                is PasskeyResult.Error -> {
                    _error.value = result.message
                }
                is PasskeyResult.Cancelled -> {
                    // User cancelled - do nothing
                }
            }

            _isAuthenticating.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearAuthenticatedDriver() {
        _authenticatedDriverId.value = null
    }
}
