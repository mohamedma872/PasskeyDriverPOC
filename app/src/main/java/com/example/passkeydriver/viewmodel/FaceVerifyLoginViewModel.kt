package com.example.passkeydriver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.passkeydriver.face.FaceAuthManager
import com.example.passkeydriver.face.FaceAuthStatus
import com.example.passkeydriver.face.FaceFrame
import com.example.passkeydriver.face.FaceNetModel
import com.example.passkeydriver.network.FleetApiClient
import com.example.passkeydriver.network.VerifyResult
import com.example.passkeydriver.security.DriverLockoutManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class FaceVerifyLoginViewModel(
    app: Application,
    val driverId: String,
    val driverName: String
) : AndroidViewModel(app) {

    private val driverLockout = DriverLockoutManager(app)
    val faceNetModel = FaceNetModel(app)

    private val _status = MutableStateFlow<FaceAuthStatus>(FaceAuthStatus.NoFace)
    val status: StateFlow<FaceAuthStatus> = _status

    private val _faceFrame = MutableStateFlow<FaceFrame?>(null)
    val faceFrame: StateFlow<FaceFrame?> = _faceFrame

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _cloudResult = MutableStateFlow<VerifyResult?>(null)
    val cloudResult: StateFlow<VerifyResult?> = _cloudResult

    private val _driverLockedSeconds = MutableStateFlow(
        if (driverLockout.isLocked(driverId)) driverLockout.remainingSeconds(driverId) else 0L
    )
    val driverLockedSeconds: StateFlow<Long> = _driverLockedSeconds

    private val _loginSuccess = MutableSharedFlow<String>()
    val loginSuccess = _loginSuccess.asSharedFlow()

    var faceAuthManager: FaceAuthManager? = null
        private set

    init {
        if (!driverLockout.isLocked(driverId)) {
            var holder: FaceAuthManager? = null
            holder = FaceAuthManager(
                faceNetModel = faceNetModel,
                onStatusUpdate = { _status.value = it },
                onFaceFrame = { _faceFrame.value = it },
                onEmbeddingReady = { embedding ->
                    viewModelScope.launch {
                        _isLoading.value = true
                        val result = FleetApiClient.verifyFace(driverId, embedding)
                        _isLoading.value = false
                        if (result != null) {
                            _cloudResult.value = result
                            if (result.matched) {
                                driverLockout.reset(driverId)
                                _loginSuccess.emit(driverId)
                            } else {
                                driverLockout.recordFailedFace(driverId)
                                _driverLockedSeconds.value =
                                    if (driverLockout.isLocked(driverId)) driverLockout.remainingSeconds(driverId) else 0L
                                holder?.resetLiveness()
                            }
                        } else {
                            // Network error — reset and let driver try again
                            holder?.resetLiveness()
                        }
                    }
                }
            )
            faceAuthManager = holder
        }
    }

    fun retryAfterResult() {
        _cloudResult.value = null
        faceAuthManager?.resetLiveness()
    }

    override fun onCleared() {
        super.onCleared()
        faceNetModel.close()
    }

    class Factory(
        private val driverId: String,
        private val driverName: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
            @Suppress("UNCHECKED_CAST")
            return FaceVerifyLoginViewModel(app, driverId, driverName) as T
        }
    }
}
