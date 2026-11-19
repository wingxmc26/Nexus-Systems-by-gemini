package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.NexusRootShell
import com.example.ui.theme.NexusTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )
    setContent {
      val mainViewModel: MainViewModel = viewModel()
      val prefs by mainViewModel.preferences.collectAsStateWithLifecycle()

      NexusTheme(
        accentPalette = prefs.accentPalette,
        amoledMode = prefs.amoledMode,
      ) {
        NexusRootShell(mainViewModel = mainViewModel)
      }
    }
  }
}
