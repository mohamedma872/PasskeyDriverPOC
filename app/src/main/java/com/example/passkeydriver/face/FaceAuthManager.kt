package com.example.passkeydriver.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream

/** Normalized face bounding box (0..1) in display-upright space + image dimensions for coordinate mapping. */
data class FaceFrame(val left: Float, val top: Float, val right: Float, val bottom: Float, val imgW: Int, val imgH: Int)

/**
 * 3-layer pipeline per camera frame:
 *
 *   Layer 1 — ML Kit Face Detection   : count faces, get bounding box
 *   Layer 2 — LivenessChecker         : blink via leftEyeOpenProbability (same ML Kit pass)
 *   Layer 3 — FaceNet / LiteRT        : 512-dim embedding → cosine similarity match
 */
class FaceAuthManager(
    private val faceNetModel: FaceNetModel,
    private val onStatusUpdate: (FaceAuthStatus) -> Unit,
    private val onFaceFrame: (FaceFrame?) -> Unit = {},
    private val onEmbeddingReady: (FloatArray) -> Unit
) {

    private val TAG = "FaceAuthManager"

    private val liveness = LivenessChecker()
    private var embeddingFired = false

    // CLASSIFICATION_MODE_ALL enables leftEyeOpenProbability / rightEyeOpenProbability
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.20f)
            .build()
    )

    fun resetLiveness() {
        Log.d(TAG, "resetLiveness()")
        liveness.reset()
        embeddingFired = false
    }

    @androidx.camera.core.ExperimentalGetImage
    fun processFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) { imageProxy.close(); return }

        val rotation = imageProxy.imageInfo.rotationDegrees

        // ML Kit gets the original MediaImage with rotation metadata — most reliable path.
        // Do NOT read imageProxy.planes before this call: ByteBuffer.get() advances the
        // buffer position, so ML Kit would see empty buffers and detect zero faces.
        val input = InputImage.fromMediaImage(mediaImage, rotation)

        // imgW/imgH in display-upright space, matching the ML Kit bounding box coordinate space
        val imgW = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val imgH = if (rotation == 90 || rotation == 270) imageProxy.width  else imageProxy.height

        detector.process(input)
            // imageProxy is still open here (closed in addOnCompleteListener below)
            .addOnSuccessListener { faces -> handleFaces(faces, imageProxy, rotation, imgW, imgH) }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit detection failed", e)
                onStatusUpdate(FaceAuthStatus.NoFace)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun handleFaces(faces: List<Face>, imageProxy: ImageProxy, rotation: Int, imgW: Int, imgH: Int) {
        when {
            faces.isEmpty() -> {
                liveness.reset(); embeddingFired = false
                onFaceFrame(null)
                onStatusUpdate(FaceAuthStatus.NoFace)
            }
            faces.size > 1 -> {
                Log.d(TAG, "${faces.size} faces detected — rejecting")
                liveness.reset(); embeddingFired = false
                onFaceFrame(null)
                onStatusUpdate(FaceAuthStatus.MultipleFaces)
            }
            else -> {
                val face = faces[0]
                val box = face.boundingBox
                onFaceFrame(FaceFrame(
                    left   = box.left.toFloat()   / imgW,
                    top    = box.top.toFloat()    / imgH,
                    right  = box.right.toFloat()  / imgW,
                    bottom = box.bottom.toFloat() / imgH,
                    imgW   = imgW,
                    imgH   = imgH
                ))
                Log.v(TAG, "Face: box=${box.width()}×${box.height()}")

                if (embeddingFired) { onStatusUpdate(FaceAuthStatus.Matching); return }

                if (liveness.isConfirmed) {
                    // Layer 3 — FaceNet: ML Kit is done, safe to read YUV buffers now
                    onStatusUpdate(FaceAuthStatus.Matching)
                    val t0 = System.currentTimeMillis()
                    val rawBitmap = imageProxyToBitmap(imageProxy)
                    val embedding = faceNetModel.getEmbedding(cropFace(rotateBitmap(rawBitmap, rotation), box))
                    if (embedding != null && !embeddingFired) {
                        embeddingFired = true
                        Log.i(TAG, "Embedding ready — ${System.currentTimeMillis() - t0} ms")
                        onEmbeddingReady(embedding)
                    }
                } else {
                    // Layer 2 — Liveness (randomized 2-step challenge)
                    val confirmed = liveness.process(face)
                    onStatusUpdate(livenessToStatus())
                    if (confirmed) {
                        val rawBitmap = imageProxyToBitmap(imageProxy)
                        val embedding = faceNetModel.getEmbedding(cropFace(rotateBitmap(rawBitmap, rotation), box))
                        if (embedding != null && !embeddingFired) {
                            embeddingFired = true
                            Log.i(TAG, "Embedding ready (on challenge frame)")
                            onEmbeddingReady(embedding)
                        }
                    }
                }
            }
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        // Rewind in case ML Kit advanced the buffer positions during detection
        val yBuffer = imageProxy.planes[0].buffer.apply { rewind() }
        val uBuffer = imageProxy.planes[1].buffer.apply { rewind() }
        val vBuffer = imageProxy.planes[2].buffer.apply { rewind() }
        val ySize = yBuffer.remaining(); val uSize = uBuffer.remaining(); val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize); vBuffer.get(nv21, ySize, vSize); uBuffer.get(nv21, ySize + vSize, uSize)
        val out = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            .compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropFace(bitmap: Bitmap, box: Rect): Bitmap {
        val pad = (box.width() * 0.25f).toInt()
        val l = (box.left - pad).coerceAtLeast(0); val t = (box.top - pad).coerceAtLeast(0)
        val r = (box.right + pad).coerceAtMost(bitmap.width); val b = (box.bottom + pad).coerceAtMost(bitmap.height)
        return Bitmap.createBitmap(bitmap, l, t, (r - l).coerceAtLeast(1), (b - t).coerceAtLeast(1))
    }

    private fun livenessToStatus(): FaceAuthStatus {
        val challenge = liveness.currentChallenge ?: return FaceAuthStatus.Matching
        return if (liveness.isReadyForAction)
            FaceAuthStatus.ChallengePrompt(challenge, liveness.currentStep, liveness.challenges.size)
        else
            FaceAuthStatus.FaceDetected
    }

    fun close() = detector.close()
}

sealed class FaceAuthStatus {
    object NoFace        : FaceAuthStatus()
    object MultipleFaces : FaceAuthStatus()
    object FaceDetected  : FaceAuthStatus()
    /** Driver must now perform [challenge] (step [step]+1 of [total]). */
    data class ChallengePrompt(
        val challenge: LivenessChecker.ChallengeType,
        val step: Int,
        val total: Int
    ) : FaceAuthStatus()
    object Matching      : FaceAuthStatus()
}
