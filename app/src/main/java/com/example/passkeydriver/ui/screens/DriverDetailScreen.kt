package com.example.passkeydriver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.passkeydriver.data.Driver
import com.example.passkeydriver.data.DriverApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDetailScreen(
    driverId: String,
    api: DriverApi,
    onWriteCard: (Driver) -> Unit,
    onVerifyCard: (driverId: String, driverName: String) -> Unit,
    onBack: () -> Unit
) {
    var driver by remember { mutableStateOf<Driver?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPin by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(driverId) {
        try {
            driver = api.getDriverById(driverId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(driver?.name ?: "Driver Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Text(error!!, modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error)
                driver != null -> {
                    val d = driver!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier.size(80.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(d.name.first().uppercaseChar().toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(d.name, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold)

                        // Card status
                        val hasCard = d.cardIssuedAt != null
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (hasCard) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (hasCard) Icons.Default.Nfc else Icons.Default.CreditCardOff,
                                    contentDescription = null, modifier = Modifier.size(16.dp),
                                    tint = if (hasCard) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    if (hasCard) "Card issued" else "No card issued",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (hasCard) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Credentials card
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Credentials", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider()
                                CredentialField("Username", d.username)
                                CredentialField("Password", d.password ?: "—",
                                    hidden = !showPassword,
                                    onToggleVisibility = { showPassword = !showPassword })
                                CredentialField("PIN", d.pin ?: "—",
                                    hidden = !showPin,
                                    onToggleVisibility = { showPin = !showPin })
                                CredentialField("Driver ID", d.id)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Issue / Rewrite card button
                        Button(
                            onClick = { onWriteCard(d) },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.Nfc, contentDescription = null,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (hasCard) "Rewrite Card" else "Issue New Card",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Verify card button (only if card was issued)
                        if (hasCard) {
                            OutlinedButton(
                                onClick = { onVerifyCard(d.id, d.name) },
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null,
                                    modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Verify Card", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialField(
    label: String,
    value: String,
    hidden: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (hidden) "••••" else value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        if (onToggleVisibility != null) {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null, modifier = Modifier.size(18.dp)
                )
            }
        }
        IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy",
                modifier = Modifier.size(18.dp))
        }
    }
}
