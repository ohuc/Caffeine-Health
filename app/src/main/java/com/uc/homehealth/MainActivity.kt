package com.uc.homehealth

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.ui.theme.HomeHealthTheme
import com.uc.homehealth.ui.viewmodel.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Without ACCESS_LOCAL_NETWORK, Android 17 silently blocks TCP connects to LAN IPs —
    // OkHttp surfaces it as a generic 10s connect timeout, not a permission error.
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — WS reconnect loop handles the retry */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 36) {
            val accessLocalNetwork = "android.permission.ACCESS_LOCAL_NETWORK"
            if (checkSelfPermission(accessLocalNetwork) != PackageManager.PERMISSION_GRANTED) {
                requestPermission.launch(accessLocalNetwork)
            }
        }

        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            HomeHealthTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeHealthNavHost()
                }
            }
        }
    }
}
