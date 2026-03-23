package com.example.passkeydriver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun DashboardScreen(driverId: String, driverName: String, onLogout: () -> Unit) {
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
                Text("WasteHero", color = Color(0xFF00C9D7), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Driver Dashboard", color = Color.White, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, "Logout", tint = Color(0xFF8A96AA))
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Avatar
            Box(
                Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A2535)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    driverName.firstOrNull()?.uppercase() ?: "?",
                    color = Color(0xFF00C9D7),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(20.dp))

            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = Color(0xFF00C9D7),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))

            Text("Welcome, $driverName", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Authenticated via Face + PIN", color = Color(0xFF8A96AA), fontSize = 14.sp)

            Spacer(Modifier.height(40.dp))

            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A2535)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Session Info", color = Color(0xFF8A96AA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    InfoLine("Driver", driverName)
                    InfoLine("Auth method", "PIN + Cloud Face Verify")
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF8A96AA), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
