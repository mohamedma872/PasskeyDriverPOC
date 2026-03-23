package com.example.passkeydriver.ui.screens

import android.util.Size as AndroidSize
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.passkeydriver.face.FaceAuthStatus
import com.example.passkeydriver.face.LivenessChecker
import com.example.passkeydriver.viewmodel.FaceVerifyLoginViewModel
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun FaceVerifyLoginScreen(
    viewModel: FaceVerifyLoginViewModel,
    onSuccess: (driverId: String) -> Unit,
    onBack: () -> Unit
) {
    val status          by viewModel.status.collectAsState()
    val faceFrame       by viewModel.faceFrame.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val cloudResult     by viewModel.cloudResult.collectAsState()
    val lockedSeconds   by viewModel.driverLockedSeconds.collectAsState()
    val lifecycleOwner  = LocalLifecycleOwner.current
    val executor        = remember { Executors.newSingleThreadExecutor() }

    // Navigate on success
    LaunchedEffect(viewModel.loginSuccess) {
        viewModel.loginSuccess.collect { driverId -> onSuccess(driverId) }
    }

    // Tick down driver lockout
    LaunchedEffect(lockedSeconds) {
        if (lockedSeconds > 0) { delay(1000L) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1624))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(AndroidSize(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().also {
                            it.setAnalyzer(executor) { proxy ->
                                viewModel.faceAuthManager?.processFrame(proxy)
                            }
                        }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Live face bounding box
        val boxColor = when (status) {
            is FaceAuthStatus.MultipleFaces  -> Color(0xFFFF6B6B)
            is FaceAuthStatus.Matching       -> Color(0xFF00C9D7)
            is FaceAuthStatus.ChallengePrompt -> Color(0xFFFFD54F)
            else -> Color.White
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frame = faceFrame ?: return@Canvas
            val scale  = maxOf(size.width / frame.imgW, size.height / frame.imgH)
            val scaledW = frame.imgW * scale
            val scaledH = frame.imgH * scale
            val offX = (size.width - scaledW) / 2f
            val offY = (size.height - scaledH) / 2f
            val left   = size.width - (frame.right * scaledW + offX)
            val right  = size.width - (frame.left  * scaledW + offX)
            val top    = frame.top    * scaledH + offY
            val bottom = frame.bottom * scaledH + offY
            drawRoundRect(
                color        = boxColor,
                topLeft      = Offset(left, top),
                size         = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(24f),
                style        = Stroke(width = 6f)
            )
        }

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Color(0xCC0F1624))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Column {
                Text(
                    "Verifying: ${viewModel.driverName}",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                Text("Complete the challenge to verify", color = Color(0xFF8A96AA), fontSize = 11.sp)
            }
        }

        // Driver lockout banner
        if (lockedSeconds > 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xCC3D0000)
            ) {
                Text(
                    "Account locked — ${lockedSeconds / 60}m ${lockedSeconds % 60}s",
                    color = Color(0xFFFF6B6B),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else if (!isLoading && cloudResult == null) {
            // Status hint
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xCC1A2535)
            ) {
                Text(
                    text = when (val s = status) {
                        is FaceAuthStatus.NoFace         -> "Look at the camera"
                        is FaceAuthStatus.MultipleFaces  -> "One face only"
                        is FaceAuthStatus.FaceDetected   -> "Hold still…"
                        is FaceAuthStatus.ChallengePrompt -> {
                            val action = when (s.challenge) {
                                LivenessChecker.ChallengeType.BLINK      -> "Blink"
                                LivenessChecker.ChallengeType.SMILE      -> "Smile"
                                LivenessChecker.ChallengeType.LOOK_LEFT  -> "Look left"
                                LivenessChecker.ChallengeType.LOOK_RIGHT -> "Look right"
                            }
                            "$action  (${s.step + 1}/${s.total})"
                        }
                        is FaceAuthStatus.Matching       -> "Processing…"
                    },
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    fontSize = 14.sp
                )
            }
        }

        // Cloud loading overlay
        if (isLoading) {
            Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00C9D7), modifier = Modifier.size(48.dp))
            }
        }

        // Result overlay
        cloudResult?.let { r ->
            val pct = (r.score * 100).toInt()
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF01A2535)
            ) {
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (r.matched) "✓ Match" else "✗ No match",
                        color = if (r.matched) Color(0xFF00C9D7) else Color(0xFFFF6B6B),
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$pct%",
                        color = if (r.matched) Color(0xFF00C9D7) else Color(0xFFFF6B6B),
                        fontSize = 72.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "similarity score\nthreshold: 75%",
                        color = Color(0xFF8A96AA), fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                    if (!r.matched) {
                        OutlinedButton(
                            onClick = { viewModel.retryAfterResult() },
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Try again", color = Color(0xFF8A96AA)) }
                    }
                }
            }
        }
    }
}
