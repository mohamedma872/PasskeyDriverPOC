package com.example.passkeydriver.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.passkeydriver.data.Driver
import com.example.passkeydriver.data.DriverApi
import com.example.passkeydriver.viewmodel.NfcState
import com.example.passkeydriver.viewmodel.NfcViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcReadScreen(
    nfcViewModel: NfcViewModel,
    api: DriverApi,
    expectedDriverId: String = "",
    expectedDriverName: String = "",
    onBack: () -> Unit
) {
    val nfcState by nfcViewModel.nfcState.collectAsState()
    var foundDriver by remember { mutableStateOf<Driver?>(null) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var verifyResult by remember { mutableStateOf<Boolean?>(null) } // null = not yet, true = match, false = mismatch
    val scope = rememberCoroutineScope()
    val isVerifyMode = expectedDriverId.isNotEmpty()

    DisposableEffect(Unit) {
        nfcViewModel.startRead()
        onDispose { nfcViewModel.stopNfc() }
    }

    LaunchedEffect(nfcState) {
        if (nfcState is NfcState.ReadSuccess) {
            val driverId = (nfcState as NfcState.ReadSuccess).driverId
            scope.launch {
                try {
                    val driver = api.getDriverById(driverId)
                    foundDriver = driver
                    lookupError = if (driver == null) "Unknown card — not registered in system" else null
                    if (isVerifyMode && driver != null) {
                        verifyResult = driver.id == expectedDriverId
                    }
                } catch (e: Exception) {
                    lookupError = "Lookup failed: ${e.message}"
                }
            }
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isVerifyMode) "Verify Card" else "Read NFC Card") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                // Verify mode: show match/mismatch result
                isVerifyMode && foundDriver != null -> {
                    val matched = verifyResult == true
                    Icon(
                        if (matched) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        if (matched) "Card Matched!" else "Card Mismatch!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    if (matched) {
                        Text("This card belongs to $expectedDriverName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    } else {
                        Text("Expected: $expectedDriverName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        Text("Found: ${foundDriver!!.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(onClick = {
                        foundDriver = null
                        verifyResult = null
                        lookupError = null
                        nfcViewModel.startRead()
                    }) { Text("Scan Again") }
                }

                // Non-verify mode: show driver info
                !isVerifyMode && foundDriver != null -> {
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(foundDriver!!.name.first().uppercaseChar().toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(foundDriver!!.name, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("@${foundDriver!!.username}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("ID: ${foundDriver!!.id}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(onClick = {
                        foundDriver = null
                        lookupError = null
                        nfcViewModel.startRead()
                    }) { Text("Tap another card") }
                }

                // Error state
                lookupError != null -> {
                    Icon(Icons.Default.Nfc, contentDescription = null,
                        modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(lookupError!!, color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        lookupError = null
                        nfcViewModel.startRead()
                    }) { Text("Try Again") }
                }

                // Scanning state
                else -> {
                    Icon(Icons.Default.Nfc, contentDescription = null,
                        modifier = Modifier.size(96.dp).scale(scale),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        if (isVerifyMode) "Tap ${expectedDriverName}'s card" else "Tap any driver card",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isVerifyMode) "Hold the card assigned to this driver" else "Hold an NFC card to verify its contents",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (nfcState is NfcState.Error) {
                        Spacer(Modifier.height(16.dp))
                        Text((nfcState as NfcState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
