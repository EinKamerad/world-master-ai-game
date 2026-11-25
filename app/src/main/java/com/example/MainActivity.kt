package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.RpgMasterApp
import com.example.ui.RpgMasterViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge layout and render Jetpack Compose UI
        enableEdgeToEdge()
        setContent {
            val rpgViewModel: RpgMasterViewModel = viewModel(
                factory = RpgMasterViewModel.provideFactory(applicationContext)
            )
            Surface(modifier = Modifier.fillMaxSize()) {
                RpgMasterApp(viewModel = rpgViewModel)
            }
        }
    }
}
