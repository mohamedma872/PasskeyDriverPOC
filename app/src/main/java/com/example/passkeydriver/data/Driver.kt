package com.example.passkeydriver.data

data class Driver(
    val id: String,
    val name: String,
    val username: String,
    val createdAt: String? = null,
    // Only present immediately after creation — shown once to admin
    val password: String? = null,
    val pin: String? = null
)
