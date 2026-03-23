package com.example.passkeydriver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passkeydriver.network.FleetApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CredentialLoginViewModel : ViewModel() {

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setUsername(v: String) { _username.value = v; _error.value = null }
    fun setPassword(v: String) { _password.value = v; _error.value = null }

    fun login(onSuccess: (driverId: String, name: String) -> Unit) {
        val u = _username.value.trim()
        val p = _password.value
        if (u.isBlank() || p.isBlank()) {
            _error.value = "Enter your username and password"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = FleetApiClient.loginWithCredentials(u.lowercase(), p)
            _isLoading.value = false
            if (result != null) {
                onSuccess(result.driverId, result.name)
            } else {
                _error.value = "Invalid username or password"
            }
        }
    }
}
