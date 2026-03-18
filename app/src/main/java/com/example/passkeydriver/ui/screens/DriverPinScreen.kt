package com.example.passkeydriver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.passkeydriver.viewmodel.DriverAuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverPinScreen(
    driverId: String,
    driverName: String,
    viewModel: DriverAuthViewModel,
    onAuthenticated: (driverId: String) -> Unit,
    onForgotPin: () -> Unit,
    onBack: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val authenticatedDriver by viewModel.authenticatedDriver.collectAsState()
    val lockedUntilMs by viewModel.lockedUntilMs.collectAsState()

    var pin by remember { mutableStateOf("") }
    val isLocked = System.currentTimeMillis() < lockedUntilMs

    LaunchedEffect(authenticatedDriver) {
        authenticatedDriver?.let {
            onAuthenticated(it.id)
            viewModel.clearAuthenticated()
        }
    }

    // Auto-submit when 4 digits entered
    LaunchedEffect(pin) {
        if (pin.length == 4 && !isLocked) {
            viewModel.verifyPin(driverId, pin)
            pin = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter PIN") },
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
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                // Avatar
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = driverName.first().uppercaseChar().toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(driverName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Enter your 4-digit PIN", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(24.dp))

                // PIN dots
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier.size(20.dp).clip(CircleShape).background(
                                if (i < pin.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Numpad
                val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(Modifier.size(80.dp))
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        if (key == "⌫") {
                                            if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        } else if (pin.length < 4 && !isLocked) {
                                            pin += key
                                        }
                                    },
                                    modifier = Modifier.size(80.dp),
                                    shape = CircleShape,
                                    enabled = !isLoading && !isLocked
                                ) {
                                    if (key == "⌫") {
                                        Icon(Icons.Default.Backspace, contentDescription = "Delete",
                                            modifier = Modifier.size(20.dp))
                                    } else {
                                        Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onForgotPin) {
                    Text("Forgot PIN? Use password instead")
                }
            }
        }
    }
}
