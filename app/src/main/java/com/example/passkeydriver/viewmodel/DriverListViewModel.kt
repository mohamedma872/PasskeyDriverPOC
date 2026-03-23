package com.example.passkeydriver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passkeydriver.network.DriverInfo
import com.example.passkeydriver.network.FleetApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DriverListViewModel : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Ready(val drivers: List<DriverInfo>) : UiState()
        object Error : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val drivers = FleetApiClient.listDrivers()
            _state.value = if (drivers != null) UiState.Ready(drivers) else UiState.Error
        }
    }
}
