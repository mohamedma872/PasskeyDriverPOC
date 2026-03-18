package com.example.passkeydriver.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.passkeydriver.data.DriverApi
import com.example.passkeydriver.viewmodel.NfcState
import com.example.passkeydriver.viewmodel.NfcViewModel
import kotlinx.coroutines.launch

@Composable
fun DriverTapScreen(
    nfcViewModel: NfcViewModel,
    api: DriverApi,
    onDriverIdentified: (driverId: String, driverName: String) -> Unit,
    onPasswordLogin: () -> Unit,
    onAdmin: () -> Unit
) {
    val nfcState by nfcViewModel.nfcState.collectAsState()
    var lookupError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        nfcViewModel.startRead()
        onDispose { nfcViewModel.stopNfc() }
    }

    LaunchedEffect(nfcState) {
        if (nfcState is NfcState.ReadSuccess) {
            val driverId = (nfcState as NfcState.ReadSuccess).driverId
            nfcViewModel.resetState()
            scope.launch {
                try {
                    val driver = api.getDriverById(driverId)
                    if (driver != null) {
                        lookupError = null
                        onDriverIdentified(driver.id, driver.name)
                    } else {
                        lookupError = "Unknown card — driver not found"
                    }
                } catch (e: Exception) {
                    lookupError = "Lookup failed: ${e.message}"
                }
            }
        }
    }

    // Expanding ripple ring animations
    val transition = rememberInfiniteTransition(label = "rings")
    val ring1Scale by transition.animateFloat(0.35f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), "rs1")
    val ring1Alpha by transition.animateFloat(0.4f, 0f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), "ra1")
    val ring2Scale by transition.animateFloat(0.35f, 1f,
        infiniteRepeatable(tween(2200, delayMillis = 730, easing = LinearEasing), RepeatMode.Restart), "rs2")
    val ring2Alpha by transition.animateFloat(0.4f, 0f,
        infiniteRepeatable(tween(2200, delayMillis = 730, easing = LinearEasing), RepeatMode.Restart), "ra2")
    val ring3Scale by transition.animateFloat(0.35f, 1f,
        infiniteRepeatable(tween(2200, delayMillis = 1460, easing = LinearEasing), RepeatMode.Restart), "rs3")
    val ring3Alpha by transition.animateFloat(0.4f, 0f,
        infiniteRepeatable(tween(2200, delayMillis = 1460, easing = LinearEasing), RepeatMode.Restart), "ra3")

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(primaryColor)
            .safeDrawingPadding()
    ) {
        // Center: ripple rings + NFC icon
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                // Ripple ring 1
                Box(
                    Modifier.fillMaxSize()
                        .scale(ring1Scale)
                        .alpha(ring1Alpha)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                // Ripple ring 2
                Box(
                    Modifier.fillMaxSize()
                        .scale(ring2Scale)
                        .alpha(ring2Alpha)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                // Ripple ring 3
                Box(
                    Modifier.fillMaxSize()
                        .scale(ring3Scale)
                        .alpha(ring3Alpha)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Tap your driver card",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Hold your NFC card to the back of the device",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )

            // Error banner
            val errorText = lookupError ?: (nfcState as? NfcState.Error)?.message
            if (errorText != null) {
                Spacer(Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = errorText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Bottom buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = onPasswordLogin,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Use password instead", fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onAdmin) {
                Text(
                    "Admin access",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
