package com.example.passkeydriver.viewmodel

import android.nfc.Tag
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passkeydriver.nfc.NfcManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NfcState {
    object Idle : NfcState()
    object Scanning : NfcState()
    data class ReadSuccess(val driverId: String) : NfcState()
    object WriteSuccess : NfcState()
    data class Error(val message: String) : NfcState()
}

enum class NfcMode { NONE, READ, WRITE }

class NfcViewModel : ViewModel() {

    companion object {
        private const val TAG = "NfcViewModel"
    }

    private val nfcManager = NfcManager()

    private val _nfcState = MutableStateFlow<NfcState>(NfcState.Idle)
    val nfcState: StateFlow<NfcState> = _nfcState.asStateFlow()

    private val _nfcMode = MutableStateFlow(NfcMode.NONE)
    val nfcMode: StateFlow<NfcMode> = _nfcMode.asStateFlow()

    private var pendingWriteDriverId: String? = null

    fun startRead() {
        _nfcMode.value = NfcMode.READ
        _nfcState.value = NfcState.Scanning
        Log.d(TAG, "NFC read mode started")
    }

    fun startWrite(driverId: String) {
        pendingWriteDriverId = driverId
        _nfcMode.value = NfcMode.WRITE
        _nfcState.value = NfcState.Scanning
        Log.d(TAG, "NFC write mode started for driverId=$driverId")
    }

    fun stopNfc() {
        _nfcMode.value = NfcMode.NONE
        _nfcState.value = NfcState.Idle
        pendingWriteDriverId = null
        Log.d(TAG, "NFC stopped")
    }

    fun resetState() {
        _nfcState.value = NfcState.Scanning
    }

    fun onTagDiscovered(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            when (_nfcMode.value) {
                NfcMode.READ -> {
                    val result = nfcManager.readDriverId(tag)
                    _nfcState.value = result.fold(
                        onSuccess = { NfcState.ReadSuccess(it) },
                        onFailure = { NfcState.Error(it.message ?: "Read failed") }
                    )
                }
                NfcMode.WRITE -> {
                    val driverId = pendingWriteDriverId
                    if (driverId == null) {
                        _nfcState.value = NfcState.Error("No driver selected for writing")
                        return@launch
                    }
                    val result = nfcManager.writeDriverId(tag, driverId)
                    _nfcState.value = result.fold(
                        onSuccess = { NfcState.WriteSuccess },
                        onFailure = { NfcState.Error(it.message ?: "Write failed") }
                    )
                }
                NfcMode.NONE -> {
                    Log.w(TAG, "Tag discovered but NFC mode is NONE")
                }
            }
        }
    }
}
