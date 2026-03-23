package com.example.passkeydriver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.passkeydriver.navigation.AppNavigation
import com.example.passkeydriver.ui.theme.PasskeyDriverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PasskeyDriverTheme {
                AppNavigation()
            }
        }
    }
}
