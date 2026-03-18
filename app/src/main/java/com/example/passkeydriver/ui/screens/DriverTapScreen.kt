package com.example.passkeydriver.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.passkeydriver.data.DriverApi
import com.example.passkeydriver.viewmodel.NfcState
import com.example.passkeydriver.viewmodel.NfcViewModel

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

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale"
    )

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = "NFC",
                modifier = Modifier.size(120.dp).scale(scale),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Tap your driver card",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Hold your NFC card to the back of the device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            val errorText = lookupError ?: (nfcState as? NfcState.Error)?.message
            if (errorText != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextButton(onClick = onPasswordLogin) {
                Text("Use password instead")
            }
        }

        TextButton(
            onClick = onAdmin,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Text("Admin", style = MaterialTheme.typography.labelSmall)
        }
    }
}
