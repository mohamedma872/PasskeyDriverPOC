package com.example.passkeydriver.data

data class Driver(
    val id: String,
    val username: String,
    val displayName: String,
    val hasPasskey: Boolean = false,
    val credentialId: String? = null
)
