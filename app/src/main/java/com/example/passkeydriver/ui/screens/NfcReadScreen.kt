package com.example.passkeydriver.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
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
    onBack: () -> Unit
) {
    val nfcState by nfcViewModel.nfcState.collectAsState()
    var foundDriver by remember { mutableStateOf<Driver?>(null) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        nfcViewModel.startRead()
        onDispose { nfcViewModel.stopNfc() }
    }

    LaunchedEffect(nfcState) {
        if (nfcState is NfcState.ReadSuccess) {
            val driverId = (nfcState as NfcState.ReadSuccess).driverId
            scope.launch {
                try {
                    foundDriver = api.getDriverById(driverId)
                    lookupError = if (foundDriver == null) "No driver found for ID: $driverId" else null
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
                title = { Text("Read NFC Card") },
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
            if (foundDriver != null) {
                // Show driver info
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
                }) {
                    Text("Tap another card")
                }
            } else if (lookupError != null) {
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
            } else {
                Icon(Icons.Default.Nfc, contentDescription = null,
                    modifier = Modifier.size(96.dp).scale(scale),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text("Tap any driver card", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Hold an NFC card to verify its contents",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
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
