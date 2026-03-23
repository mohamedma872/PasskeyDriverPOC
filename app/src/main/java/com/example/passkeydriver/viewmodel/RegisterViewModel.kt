package com.example.passkeydriver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.passkeydriver.face.FaceNetModel
import com.example.passkeydriver.network.DuplicateCheckResult
import com.example.passkeydriver.network.FleetApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class RegisterViewModel(app: Application) : AndroidViewModel(app) {

    val faceNetModel = FaceNetModel(app)

    enum class Step { NAME_AND_PIN, ENROLL_FACE }

    private val _step = MutableStateFlow(Step.NAME_AND_PIN)
    val step: StateFlow<Step> = _step

    // ── Name ──────────────────────────────────────────────────────────────────
    private val _driverName = MutableStateFlow("")
    val driverName: StateFlow<String> = _driverName

    // ── PIN entry ─────────────────────────────────────────────────────────────
    private val _pinDigits = MutableStateFlow<List<Int>>(emptyList())
    val pinDigits: StateFlow<List<Int>> = _pinDigits

    private val _confirmDigits = MutableStateFlow<List<Int>>(emptyList())
    val confirmDigits: StateFlow<List<Int>> = _confirmDigits

    /** True once the user has typed all 6 PIN digits and is now on the confirm pad. */
    private val _isConfirming = MutableStateFlow(false)
    val isConfirming: StateFlow<Boolean> = _isConfirming

    private val _pinValidationError = MutableStateFlow<String?>(null)
    val pinValidationError: StateFlow<String?> = _pinValidationError

    val isPinValid: Boolean
        get() {
            val pin     = _pinDigits.value.joinToString("")
            val confirm = _confirmDigits.value.joinToString("")
            return pin.length == 6 && confirm.length == 6 &&
                   pin == confirm && _pinValidationError.value == null
        }

    // ── Face enrollment ───────────────────────────────────────────────────────
    private val _embeddings = MutableStateFlow<List<FloatArray>>(emptyList())
    val embeddings: StateFlow<List<FloatArray>> = _embeddings

    private val _uploadError = MutableStateFlow(false)
    val uploadError: StateFlow<Boolean> = _uploadError

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    /** Non-null when the face matches an already-enrolled driver. */
    private val _duplicateDriver = MutableStateFlow<String?>(null)
    val duplicateDriver: StateFlow<String?> = _duplicateDriver

    private val _isCheckingDuplicate = MutableStateFlow(false)
    val isCheckingDuplicate: StateFlow<Boolean> = _isCheckingDuplicate

    private var pendingId:  String = UUID.randomUUID().toString()
    private var pendingPin: String = ""

    // ── Name ──────────────────────────────────────────────────────────────────
    fun setName(n: String) { _driverName.value = n }

    // ── PIN numpad ────────────────────────────────────────────────────────────
    fun onPinDigit(d: Int) {
        _pinValidationError.value = null
        if (_isConfirming.value) {
            if (_confirmDigits.value.size >= 6) return
            val next = _confirmDigits.value + d
            _confirmDigits.value = next
            if (next.size == 6) validatePins()
        } else {
            if (_pinDigits.value.size >= 6) return
            val next = _pinDigits.value + d
            _pinDigits.value = next
            if (next.size == 6) _isConfirming.value = true
        }
    }

    fun onPinDelete() {
        _pinValidationError.value = null
        if (_isConfirming.value) {
            if (_confirmDigits.value.isEmpty()) {
                // backspace past confirm → go back to editing PIN
                _isConfirming.value = false
                _pinDigits.value = _pinDigits.value.dropLast(1)
            } else {
                _confirmDigits.value = _confirmDigits.value.dropLast(1)
            }
        } else {
            _pinDigits.value = _pinDigits.value.dropLast(1)
        }
    }

    private fun validatePins() {
        val pin     = _pinDigits.value.joinToString("")
        val confirm = _confirmDigits.value.joinToString("")
        val error = when {
            pin != confirm          -> "PINs do not match — try again"
            isAllSame(pin)          -> "PIN cannot be all the same digit (e.g. 111111)"
            isSequential(pin)       -> "PIN cannot be a simple sequence (e.g. 123456)"
            isRepeatingPattern(pin) -> "PIN pattern is too predictable (e.g. 123123)"
            else                    -> null
        }
        _pinValidationError.value = error
        if (error != null) {
            // Reset only the confirm digits; driver retypes confirmation
            _confirmDigits.value = emptyList()
        }
    }

    private fun isAllSame(pin: String) = pin.all { it == pin[0] }

    private fun isSequential(pin: String): Boolean {
        val digits = pin.map { it.digitToInt() }
        val diffs  = digits.zipWithNext { a, b -> b - a }
        return diffs.all { it == 1 } || diffs.all { it == -1 }
    }

    private fun isRepeatingPattern(pin: String): Boolean {
        // 123123 — first half equals second half
        if (pin.substring(0, 3) == pin.substring(3, 6)) return true
        // 121212 — 2-digit chunk repeats
        val chunk2 = pin.substring(0, 2)
        return pin.chunked(2).all { it == chunk2 }
    }

    // ── Confirm step 1 ────────────────────────────────────────────────────────
    fun confirmNameAndPin(onDone: () -> Unit) {
        if (!isPinValid || _driverName.value.isBlank()) return
        pendingId  = UUID.randomUUID().toString()
        pendingPin = _pinDigits.value.joinToString("")
        _step.value = Step.ENROLL_FACE
        onDone()
    }

    // ── Face enrollment ───────────────────────────────────────────────────────

    /**
     * Called for each captured face embedding.
     * On the FIRST capture only, runs a server-side duplicate check.
     * [onReady] is called (with true) only if the face is not a duplicate.
     */
    fun addEmbedding(embedding: FloatArray, onReady: (accepted: Boolean) -> Unit) {
        if (_embeddings.value.isNotEmpty()) {
            // Scans 2-5: skip the check, add directly
            _embeddings.value = _embeddings.value + embedding
            onReady(true)
            return
        }
        // First scan: check for duplicate before accepting
        viewModelScope.launch {
            _isCheckingDuplicate.value = true
            val result = FleetApiClient.checkDuplicateFace(embedding)
            _isCheckingDuplicate.value = false
            if (result == null) {
                // Network error — still allow enrollment (fail-open for UX)
                _embeddings.value = _embeddings.value + embedding
                onReady(true)
            } else if (result.isDuplicate) {
                _duplicateDriver.value = result.name ?: "another driver"
                onReady(false)
            } else {
                _embeddings.value = _embeddings.value + embedding
                onReady(true)
            }
        }
    }

    val enrolledCount: Int get() = _embeddings.value.size

    fun finalizeEnrollment(onDone: (driverId: String) -> Unit) {
        val name = _driverName.value.trim()
        val pin  = pendingPin
        val id   = pendingId
        val embs = _embeddings.value
        viewModelScope.launch {
            _isUploading.value = true
            val ok = FleetApiClient.registerDriver(
                id         = id,
                name       = name,
                pin        = pin,
                embeddings = embs
            )
            _isUploading.value = false
            if (ok) onDone(id) else _uploadError.value = true
        }
    }

    fun reset() {
        _step.value             = Step.NAME_AND_PIN
        _driverName.value       = ""
        _pinDigits.value        = emptyList()
        _confirmDigits.value    = emptyList()
        _isConfirming.value     = false
        _pinValidationError.value = null
        _embeddings.value       = emptyList()
        _uploadError.value      = false
        _duplicateDriver.value  = null
        pendingId  = UUID.randomUUID().toString()
        pendingPin = ""
    }

    override fun onCleared() {
        super.onCleared()
        faceNetModel.close()
    }
}
