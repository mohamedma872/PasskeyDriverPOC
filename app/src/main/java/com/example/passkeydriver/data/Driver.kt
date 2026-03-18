package com.example.passkeydriver.data

data class Driver(
    val id: String,
    val name: String,
    val username: String,
    val createdAt: String? = null,
    val password: String? = null,
    val pin: String? = null,
    val cardIssuedAt: String? = null
)
