package com.example.passkeydriver.face

import android.util.Log
import com.google.mlkit.vision.face.Face

/**
 * Layer 2 — Liveness via randomized 2-step challenge.
 *
 * On each construction/reset, two random challenges are picked from:
 *   BLINK, SMILE, LOOK_LEFT, LOOK_RIGHT
 *
 * Each challenge follows a neutral-then-action pattern:
 *   1. Wait for a neutral baseline (eyes open, face forward, no smile…)
 *   2. Detect the requested action
 *
 * Picking two different challenges each session makes video-replay attacks
 * impractical — the attacker would need a pre-recorded clip of the exact
 * random sequence shown on screen.
 *
 * All data comes from the same ML Kit CLASSIFICATION_MODE_ALL pass already
 * running in FaceAuthManager — no extra model or library required.
 */
class LivenessChecker {

    private val TAG = "LivenessChecker"

    enum class ChallengeType { BLINK, SMILE, LOOK_LEFT, LOOK_RIGHT }

    private enum class StepState { WAITING_NEUTRAL, ACTION_READY }

    var challenges: List<ChallengeType> = pickTwo()
        private set

    private var stepState = StepState.WAITING_NEUTRAL

    var currentStep: Int = 0
        private set

    /** True once every challenge in the sequence has been completed. */
    val isConfirmed: Boolean get() = currentStep >= challenges.size

    /** The challenge the driver must perform right now, or null if all done. */
    val currentChallenge: ChallengeType? get() = challenges.getOrNull(currentStep)

    /**
     * True when the neutral baseline has been established for the current
     * challenge and the driver should now perform the action.
     */
    val isReadyForAction: Boolean get() = stepState == StepState.ACTION_READY

    fun reset() {
        challenges = pickTwo()
        stepState = StepState.WAITING_NEUTRAL
        currentStep = 0
        Log.d(TAG, "Reset — new challenges: $challenges")
    }

    /** Feed every detected Face here. Returns true when all challenges pass. */
    fun process(face: Face): Boolean {
        if (isConfirmed) return true
        val challenge = challenges[currentStep]

        when (stepState) {
            StepState.WAITING_NEUTRAL -> {
                if (isNeutral(face, challenge)) {
                    Log.d(TAG, "Step $currentStep neutral OK — awaiting $challenge")
                    stepState = StepState.ACTION_READY
                }
            }
            StepState.ACTION_READY -> {
                if (isActionPerformed(face, challenge)) {
                    Log.i(TAG, "Step $currentStep ✓ $challenge")
                    currentStep++
                    stepState = StepState.WAITING_NEUTRAL
                    if (isConfirmed) Log.i(TAG, "✓ All challenges passed — liveness confirmed")
                }
            }
        }

        return isConfirmed
    }

    // ── Neutral baseline ─────────────────────────────────────────────────────

    private fun isNeutral(face: Face, challenge: ChallengeType): Boolean = when (challenge) {
        ChallengeType.BLINK ->
            (face.leftEyeOpenProbability  ?: 0f) > 0.70f &&
            (face.rightEyeOpenProbability ?: 0f) > 0.70f
        ChallengeType.SMILE ->
            (face.smilingProbability ?: 1f) < 0.30f
        ChallengeType.LOOK_LEFT, ChallengeType.LOOK_RIGHT ->
            kotlin.math.abs(face.headEulerAngleY) < 15f
    }

    // ── Action detection ─────────────────────────────────────────────────────

    private fun isActionPerformed(face: Face, challenge: ChallengeType): Boolean = when (challenge) {
        ChallengeType.BLINK ->
            (face.leftEyeOpenProbability  ?: 1f) < 0.20f &&
            (face.rightEyeOpenProbability ?: 1f) < 0.20f
        ChallengeType.SMILE ->
            (face.smilingProbability ?: 0f) > 0.75f
        // Driver's left  = face rotates right from camera's view = +Y euler angle
        ChallengeType.LOOK_LEFT  -> face.headEulerAngleY > 22f
        // Driver's right = face rotates left  from camera's view = -Y euler angle
        ChallengeType.LOOK_RIGHT -> face.headEulerAngleY < -22f
    }

    // ── Challenge selection ───────────────────────────────────────────────────

    private fun pickTwo(): List<ChallengeType> {
        val pool = ChallengeType.values().toMutableList()
        val first = pool.random()
        pool.remove(first)
        // Don't pair LOOK_LEFT + LOOK_RIGHT — near-identical instructions are confusing
        if (first == ChallengeType.LOOK_LEFT)  pool.remove(ChallengeType.LOOK_RIGHT)
        if (first == ChallengeType.LOOK_RIGHT) pool.remove(ChallengeType.LOOK_LEFT)
        val second = pool.random()
        return listOf(first, second)
    }
}
