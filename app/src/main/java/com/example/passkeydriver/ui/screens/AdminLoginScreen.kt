package com.example.passkeydriver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.passkeydriver.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(
    viewModel: AdminViewModel,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val pinError by viewModel.pinError.collectAsState()

    var pin by remember { mutableStateOf("") }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) onSuccess()
    }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            viewModel.validatePin(pin)
            if (!isLoggedIn) pin = ""
        }
    }

    LaunchedEffect(pinError) {
        if (pinError) {
            pin = ""
            viewModel.clearPinError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Access") },
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
                Spacer(Modifier.height(24.dp))
                Icon(Icons.Default.Lock, contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Admin PIN", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Enter the 4-digit admin PIN to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier.size(20.dp).clip(CircleShape).background(
                                if (i < pin.length) MaterialTheme.colorScheme.primary
                                else if (pinError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }

                if (pinError) {
                    Spacer(Modifier.height(12.dp))
                    Text("Incorrect PIN", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                        } else if (pin.length < 4) {
                                            pin += key
                                        }
                                    },
                                    modifier = Modifier.size(80.dp),
                                    shape = CircleShape
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
            }
        }
    }
}
