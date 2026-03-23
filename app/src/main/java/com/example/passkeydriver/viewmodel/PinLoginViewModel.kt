package com.example.passkeydriver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.passkeydriver.network.FleetApiClient
import com.example.passkeydriver.network.PinCheckResult
import com.example.passkeydriver.security.TabletLockoutManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class PinLoginViewModel(
    app: Application,
    private val driverId: String
) : AndroidViewModel(app) {

    class Factory(
        private val app: Application,
        private val driverId: String
    ) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PinLoginViewModel(app, driverId) as T
        }
    }

    private val lockout = TabletLockoutManager(app)

    private val _digits = MutableStateFlow<List<Int>>(emptyList())
    val digits: StateFlow<List<Int>> = _digits

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _tabletLockedSeconds = MutableStateFlow(0L)
    val tabletLockedSeconds: StateFlow<Long> = _tabletLockedSeconds

    private val _driverFound = MutableSharedFlow<PinCheckResult>()
    val driverFound = _driverFound.asSharedFlow()

    init {
        refreshLockout()
    }

    fun refreshLockout() {
        _tabletLockedSeconds.value = if (lockout.isLocked()) lockout.remainingSeconds() else 0L
    }

    fun onDigit(d: Int) {
        if (lockout.isLocked() || _isLoading.value) return
        if (_digits.value.size >= 6) return
        val next = _digits.value + d
        _digits.value = next
        _error.value = null
        if (next.size == 6) submit(next.joinToString(""))
    }

    fun onDelete() {
        if (_isLoading.value) return
        _digits.value = _digits.value.dropLast(1)
        _error.value = null
    }

    private fun submit(pin: String) {
        if (lockout.isLocked()) {
            _tabletLockedSeconds.value = lockout.remainingSeconds()
            _digits.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val result = FleetApiClient.checkPin(driverId, pin)
            _isLoading.value = false
            if (result != null) {
                lockout.reset()
                _driverFound.emit(result)
            } else {
                lockout.recordFailedPin()
                _tabletLockedSeconds.value = if (lockout.isLocked()) lockout.remainingSeconds() else 0L
                _error.value = if (lockout.isLocked()) "Too many attempts. Tablet locked." else "Incorrect PIN"
                _digits.value = emptyList()
            }
        }
    }
}
