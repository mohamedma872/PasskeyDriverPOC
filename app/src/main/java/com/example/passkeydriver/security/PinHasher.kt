package com.example.passkeydriver.security

object PinHasher {
    /** Generate a random 6-digit PIN for new driver enrollment. */
    fun generatePin(): String = (100000..999999).random().toString()
}
