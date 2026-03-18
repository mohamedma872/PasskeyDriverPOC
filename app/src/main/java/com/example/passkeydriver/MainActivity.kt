package com.example.passkeydriver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.passkeydriver.auth.PasskeyManager
import com.example.passkeydriver.data.DriverRepository
import com.example.passkeydriver.navigation.AppNavigation
import com.example.passkeydriver.ui.theme.PasskeyDriverTheme
import com.example.passkeydriver.viewmodel.DriverListViewModel
import com.example.passkeydriver.viewmodel.RegisterViewModel

class MainActivity : ComponentActivity() {

    private val passkeyManager by lazy { PasskeyManager(applicationContext) }
    private val driverListViewModel by lazy { DriverListViewModel(passkeyManager) }
    private val registerViewModel by lazy { RegisterViewModel(passkeyManager) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize persistent credential storage — loads previously registered drivers
        DriverRepository.init(passkeyManager.webAuthnServer.getCredentialStore())

        setContent {
            PasskeyDriverTheme {
                AppNavigation(
                    driverListViewModel = driverListViewModel,
                    registerViewModel = registerViewModel
                )
            }
        }
    }
}
