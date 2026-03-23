package com.example.passkeydriver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.passkeydriver.network.DriverInfo
import com.example.passkeydriver.viewmodel.DriverListViewModel

@Composable
fun DriverListScreen(
    viewModel: DriverListViewModel,
    onDriverSelected: (driver: DriverInfo) -> Unit,
    onRegister: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1624))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A2535))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "WASTEHERO",
                    color = Color(0xFF00C9D7),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    "Who are you?",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (state is DriverListViewModel.UiState.Ready || state is DriverListViewModel.UiState.Error) {
                IconButton(onClick = viewModel::load) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF8A96AA))
                }
            }
        }

        when (val s = state) {
            is DriverListViewModel.UiState.Loading -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00C9D7))
                }
            }

            is DriverListViewModel.UiState.Error -> {
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Could not load drivers", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Check your connection and try again.", color = Color(0xFF8A96AA), fontSize = 13.sp, textAlign = TextAlign.Center)
                        OutlinedButton(
                            onClick = viewModel::load,
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Retry", color = Color(0xFF00C9D7)) }
                    }
                }
            }

            is DriverListViewModel.UiState.Ready -> {
                if (s.drivers.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No drivers registered yet.\nTap below to add yourself.",
                            color = Color(0xFF8A96AA),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        "Select your name to log in",
                        color = Color(0xFF8A96AA),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.drivers) { driver ->
                            DriverRow(driver = driver, onClick = { onDriverSelected(driver) })
                        }
                    }
                }
            }
        }

        // Register button
        Button(
            onClick = onRegister,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1A2535)
            )
        ) {
            Text(
                "Register as new driver",
                color = Color(0xFF00C9D7),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DriverRow(driver: DriverInfo, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = driver.isEnrolled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (driver.isEnrolled) Color(0xFF1A2535) else Color(0xFF131B28)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (driver.isEnrolled) Color(0xFF0D3340) else Color(0xFF1C2030)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    driver.name.firstOrNull()?.uppercase() ?: "?",
                    color = if (driver.isEnrolled) Color(0xFF00C9D7) else Color(0xFF4A5568),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    driver.name,
                    color = if (driver.isEnrolled) Color.White else Color(0xFF4A5568),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (driver.isEnrolled) "Face enrolled" else "Not enrolled — cannot log in",
                    color = if (driver.isEnrolled) Color(0xFF00C9D7) else Color(0xFF4A5568),
                    fontSize = 12.sp
                )
            }

            if (driver.isEnrolled) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF0D3340)) {
                    Text(
                        "PIN + Face",
                        color = Color(0xFF00C9D7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
