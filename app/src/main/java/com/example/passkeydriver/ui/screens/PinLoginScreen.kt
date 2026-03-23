package com.example.passkeydriver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.passkeydriver.viewmodel.PinLoginViewModel
import kotlinx.coroutines.delay

@Composable
fun PinLoginScreen(
    viewModel: PinLoginViewModel,
    driverName: String,
    onDriverFound: (driverId: String, name: String) -> Unit,
    onBack: () -> Unit
) {
    val digits          by viewModel.digits.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val error           by viewModel.error.collectAsState()
    val lockedSeconds   by viewModel.tabletLockedSeconds.collectAsState()

    LaunchedEffect(viewModel.driverFound) {
        viewModel.driverFound.collect { result ->
            onDriverFound(result.driverId, result.name)
        }
    }

    // Tick down the lockout countdown every second
    LaunchedEffect(lockedSeconds) {
        if (lockedSeconds > 0) {
            delay(1000L)
            viewModel.refreshLockout()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1624))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A2535))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text("Enter PIN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.weight(1f))

        // Greeting
        Text(
            "Welcome, $driverName",
            color = Color(0xFF00C9D7),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Enter your 6-digit PIN",
            color = Color(0xFF8A96AA),
            fontSize = 13.sp
        )

        Spacer(Modifier.height(40.dp))

        // Tablet lockout banner
        if (lockedSeconds > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF3D0000),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    "Tablet locked — ${lockedSeconds / 60}m ${lockedSeconds % 60}s",
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // 6 dot indicators
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { i ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < digits.size) Color(0xFF00C9D7) else Color(0xFF252F42)
                        )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Error
        if (error != null) {
            Text(
                error!!,
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(32.dp))

        // Loading indicator or numpad
        if (isLoading) {
            CircularProgressIndicator(color = Color(0xFF00C9D7), modifier = Modifier.size(40.dp))
        } else {
            NumPad(
                enabled = lockedSeconds == 0L,
                onDigit = viewModel::onDigit,
                onDelete = viewModel::onDelete
            )
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumPad(enabled: Boolean, onDigit: (Int) -> Unit, onDelete: () -> Unit) {
    val keys = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9),
        listOf(-1, 0, -2)  // -1 = empty, -2 = delete
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    when (key) {
                        -1 -> Spacer(Modifier.size(72.dp))
                        -2 -> NumKey(label = "⌫", enabled = enabled, onClick = onDelete)
                        else -> NumKey(label = key.toString(), enabled = enabled, onClick = { onDigit(key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NumKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = if (enabled) Color(0xFF1A2535) else Color(0xFF131B28),
        onClick = onClick,
        enabled = enabled
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (enabled) Color.White else Color(0xFF4A5568),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
