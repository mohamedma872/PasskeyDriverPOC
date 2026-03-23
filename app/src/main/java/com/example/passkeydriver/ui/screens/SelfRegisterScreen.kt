package com.example.passkeydriver.ui.screens

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.passkeydriver.face.FaceFrame
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.passkeydriver.face.FaceAuthManager
import com.example.passkeydriver.face.FaceAuthStatus
import com.example.passkeydriver.face.LivenessChecker
import com.example.passkeydriver.viewmodel.RegisterViewModel
import java.util.concurrent.Executors

private val CAPTURE_PROMPTS = listOf(
    "Face scan 1 of 5 — look at the camera",
    "Face scan 2 of 5",
    "Face scan 3 of 5",
    "Face scan 4 of 5",
    "Face scan 5 of 5 — almost done"
)

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun SelfRegisterScreen(
    viewModel: RegisterViewModel,
    onRegistered: (driverId: String) -> Unit,
    onBack: () -> Unit
) {
    val step by viewModel.step.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.reset()
    }

    AnimatedContent(targetState = step, label = "register_step") { currentStep ->
        when (currentStep) {
            RegisterViewModel.Step.NAME_AND_PIN -> NameAndPinStep(viewModel, onBack)
            RegisterViewModel.Step.ENROLL_FACE  -> FaceEnrollStep(viewModel, onRegistered, onBack)
        }
    }
}

// ── Step 1: Name + PIN entry ──────────────────────────────────────────────────

@Composable
private fun NameAndPinStep(viewModel: RegisterViewModel, onBack: () -> Unit) {
    val name          by viewModel.driverName.collectAsState()
    val pinDigits     by viewModel.pinDigits.collectAsState()
    val confirmDigits by viewModel.confirmDigits.collectAsState()
    val isConfirming  by viewModel.isConfirming.collectAsState()
    val pinError      by viewModel.pinValidationError.collectAsState()

    val pinValid = name.isNotBlank() &&
                   pinDigits.size == 6 &&
                   confirmDigits.size == 6 &&
                   pinError == null

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1624))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A2535))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Column {
                Text(
                    "STEP 1 OF 2",
                    color = Color(0xFF00C9D7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    "Create your profile",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Name section ──────────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.setName(it) },
                label = { Text("Full name") },
                singleLine = true,
                trailingIcon = {
                    AnimatedVisibility(visible = name.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                        Surface(shape = CircleShape, color = Color(0xFF0D3340)) {
                            Text("✓", color = Color(0xFF00C9D7), fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF00C9D7),
                    unfocusedLabelColor = Color(0xFF8A96AA),
                    focusedBorderColor = Color(0xFF00C9D7),
                    unfocusedBorderColor = Color(0xFF2D3748)
                )
            )
        }

        HorizontalDivider(color = Color(0xFF1E2D42), thickness = 1.dp)

        // ── PIN section ───────────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // Phase label
            AnimatedContent(targetState = isConfirming, label = "pin_phase") { confirming ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (confirming) "CONFIRM PIN" else "CHOOSE A PIN",
                        color = Color(0xFF8A96AA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (confirming) "Re-enter your PIN to confirm" else "Choose a 6-digit PIN you'll remember",
                        color = Color(0xFF4A5568),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Dot indicators
            if (isConfirming) {
                // Row 1 — original PIN (locked, all teal)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(6) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00C9D7))
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Row 2 — confirm entry
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(6) { i ->
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < confirmDigits.size) Color(0xFF00C9D7)
                                    else Color(0xFF1E2D42)
                                )
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(6) { i ->
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < pinDigits.size) Color(0xFF00C9D7)
                                    else Color(0xFF1E2D42)
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Error or hint
            AnimatedContent(targetState = pinError, label = "pin_error") { error ->
                if (error != null) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF3D0000)) {
                        Text(
                            error,
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Text(
                        "No repeated (111111) or sequential (123456) digits",
                        color = Color(0xFF4A5568),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Numpad
            RegisterNumPad(
                onDigit = viewModel::onPinDigit,
                onDelete = viewModel::onPinDelete
            )

            Spacer(Modifier.weight(1f))
        }

        // ── Continue button ───────────────────────────────────────────────────
        Button(
            onClick = { viewModel.confirmNameAndPin {} },
            enabled = pinValid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00C9D7),
                disabledContainerColor = Color(0xFF1E2D42)
            )
        ) {
            Text(
                "Continue — Capture Face",
                color = if (pinValid) Color(0xFF0F1624) else Color(0xFF4A5568),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun RegisterNumPad(onDigit: (Int) -> Unit, onDelete: () -> Unit) {
    val keys = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    when (key) {
                        -1 -> Spacer(Modifier.size(72.dp))
                        -2 -> Surface(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = Color(0xFF1A2535),
                            onClick = onDelete
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("⌫", color = Color(0xFF8A96AA), fontSize = 22.sp)
                            }
                        }
                        else -> Surface(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = Color(0xFF1A2535),
                            onClick = { onDigit(key) }
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    key.toString(),
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Step 2: 3-angle face enrollment ──────────────────────────────────────────

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun FaceEnrollStep(
    viewModel: RegisterViewModel,
    onRegistered: (String) -> Unit,
    onBack: () -> Unit
) {
    val embeddings        by viewModel.embeddings.collectAsState()
    val isUploading       by viewModel.isUploading.collectAsState()
    val uploadError       by viewModel.uploadError.collectAsState()
    val duplicateDriver   by viewModel.duplicateDriver.collectAsState()
    val isCheckingDup     by viewModel.isCheckingDuplicate.collectAsState()
    val step = embeddings.size   // 0..4 → done at 5
    val lifecycleOwner = LocalLifecycleOwner.current

    var captureStatus by remember { mutableStateOf<FaceAuthStatus>(FaceAuthStatus.NoFace) }
    var faceFrame by remember { mutableStateOf<FaceFrame?>(null) }
    var capturing by remember { mutableStateOf(false) }

    // Already registered as another driver
    if (duplicateDriver != null) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0F1624)).windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("👤", fontSize = 48.sp)
                Text("Already registered", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "This face is already registered as \"$duplicateDriver\".\nLog in with that account instead.",
                    color = Color(0xFF8A96AA), fontSize = 14.sp, textAlign = TextAlign.Center
                )
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C9D7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Go back", color = Color(0xFF0F1624), fontWeight = FontWeight.Bold) }
            }
        }
        return
    }

    // Upload failed
    if (uploadError) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0F1624)).windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("⚠", fontSize = 48.sp)
                Text("Upload failed", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Could not save your profile. Check your connection and try again.",
                    color = Color(0xFF8A96AA), fontSize = 14.sp, textAlign = TextAlign.Center
                )
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C9D7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Go back", color = Color(0xFF0F1624), fontWeight = FontWeight.Bold) }
            }
        }
        return
    }

    if (step >= 5) {
        LaunchedEffect(Unit) {
            viewModel.finalizeEnrollment { driverId -> onRegistered(driverId) }
        }
        Box(Modifier.fillMaxSize().background(Color(0xFF0F1624)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF00C9D7))
                Spacer(Modifier.height(16.dp))
                Text(
                    if (isUploading) "Uploading to server…" else "Saving your face data…",
                    color = Color(0xFF8A96AA)
                )
            }
        }
        return
    }

    var managerHolder: FaceAuthManager? = null
    val manager = remember {
        FaceAuthManager(
            faceNetModel = viewModel.faceNetModel,
            onStatusUpdate = { captureStatus = it },
            onFaceFrame = { faceFrame = it },
            onEmbeddingReady = { embedding ->
                if (!capturing) {
                    capturing = true
                    viewModel.addEmbedding(embedding) { accepted ->
                        if (accepted) managerHolder?.resetLiveness()
                        capturing = false
                    }
                }
            }
        ).also { managerHolder = it }
    }

    val executor = remember { Executors.newSingleThreadExecutor() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1624))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A2535))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text("Step 2 of 2 — Face Setup", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // Progress bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(5) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                i < step  -> Color(0xFF00C9D7)
                                i == step -> Color(0xFF00838F)
                                else      -> Color(0xFF2D3748)
                            }
                        )
                )
            }
        }

        Text(
            CAPTURE_PROMPTS[step],
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Capture ${step + 1} of 5  ·  Follow the prompt below",
            color = Color(0xFF8A96AA),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
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
                            .setTargetResolution(Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also {
                                it.setAnalyzer(executor) { proxy -> manager.processFrame(proxy) }
                            }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview, analysis
                            )
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Live face bounding box overlay
            val boxColor = when (captureStatus) {
                is FaceAuthStatus.MultipleFaces   -> Color(0xFFFF6B6B)
                is FaceAuthStatus.Matching        -> Color(0xFF00C9D7)
                is FaceAuthStatus.ChallengePrompt -> Color(0xFFFFD54F)
                else -> Color.White
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val frame = faceFrame ?: return@Canvas
                val scale = maxOf(size.width / frame.imgW, size.height / frame.imgH)
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
                    size         = ComposeSize(right - left, bottom - top),
                    cornerRadius = CornerRadius(24f),
                    style        = Stroke(width = 6f)
                )
            }

            if (isCheckingDup) {
                // Transient overlay while the duplicate check network call runs
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xCC1A2535)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00C9D7),
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Checking face…", color = Color.White, fontSize = 13.sp)
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xCC1A2535)
                ) {
                    Text(
                        when (val s = captureStatus) {
                            is FaceAuthStatus.NoFace         -> "Look at the camera"
                            is FaceAuthStatus.MultipleFaces  -> "One face only"
                            is FaceAuthStatus.FaceDetected   -> "Hold still…"
                            is FaceAuthStatus.ChallengePrompt -> when (s.challenge) {
                                LivenessChecker.ChallengeType.BLINK      -> "Blink to capture"
                                LivenessChecker.ChallengeType.SMILE      -> "Smile to capture"
                                LivenessChecker.ChallengeType.LOOK_LEFT  -> "Look left"
                                LivenessChecker.ChallengeType.LOOK_RIGHT -> "Look right"
                            }
                            is FaceAuthStatus.Matching       -> "Captured!"
                        },
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
