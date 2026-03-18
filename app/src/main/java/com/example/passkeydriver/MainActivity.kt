package com.example.passkeydriver

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.passkeydriver.data.DriverApi
import com.example.passkeydriver.navigation.AppNavigation
import com.example.passkeydriver.ui.theme.PasskeyDriverTheme
import com.example.passkeydriver.viewmodel.AdminViewModel
import com.example.passkeydriver.viewmodel.DriverAuthViewModel
import com.example.passkeydriver.viewmodel.NfcMode
import com.example.passkeydriver.viewmodel.NfcViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val api = DriverApi()
    private val nfcViewModel = NfcViewModel()
    private val adminViewModel by lazy { AdminViewModel(api) }
    private val driverAuthViewModel by lazy { DriverAuthViewModel(api) }

    private var nfcAdapter: NfcAdapter? = null
    private val nfcReaderCallback = NfcAdapter.ReaderCallback { tag: Tag ->
        nfcViewModel.onTagDiscovered(tag)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                nfcViewModel.nfcMode.collect { mode ->
                    if (mode != NfcMode.NONE) {
                        nfcAdapter?.enableReaderMode(
                            this@MainActivity, nfcReaderCallback,
                            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                            null
                        )
                    } else {
                        nfcAdapter?.disableReaderMode(this@MainActivity)
                    }
                }
            }
        }

        setContent {
            PasskeyDriverTheme {
                AppNavigation(
                    nfcViewModel = nfcViewModel,
                    adminViewModel = adminViewModel,
                    driverAuthViewModel = driverAuthViewModel,
                    api = api
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }
}
