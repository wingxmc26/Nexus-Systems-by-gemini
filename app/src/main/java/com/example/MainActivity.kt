package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.NexusRootShell
import com.example.ui.theme.NexusTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.widget.NexusWidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private var pendingRoute by mutableStateOf<String?>(null)
  private var pendingWidgetId by mutableStateOf<Int?>(null)
  private var pendingProjectId by mutableStateOf<Long?>(null)
  private var isInPipMode by mutableStateOf(false)
  private var isActiveVideoForPip by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pendingRoute = intent?.getStringExtra("route")
    pendingWidgetId = intent?.getIntExtra("widget_id", -1)?.takeIf { it > 0 }
    pendingProjectId = intent?.getLongExtra("project_id", -1L)?.takeIf { it > 0L }
    isInPipMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) isInPictureInPictureMode else false

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
        NexusRootShell(
          mainViewModel = mainViewModel,
          pendingRoute = pendingRoute,
          onPendingRouteConsumed = { pendingRoute = null },
          pendingWidgetId = pendingWidgetId,
          pendingProjectId = pendingProjectId,
          onPendingWidgetTargetConsumed = {
            pendingWidgetId = null
            pendingProjectId = null
          },
          isInPipMode = isInPipMode,
          onActiveVideoForPipChanged = { active ->
            isActiveVideoForPip = active
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              runCatching { setPictureInPictureParams(buildPipParams(active)) }
            }
          },
          onEnterPip = { enterPipIfSupported() },
        )
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
      .setAspectRatio(Rational(16, 9))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      builder.setAutoEnterEnabled(autoEnter)
    }
    return builder.build()
  }

  fun enterPipIfSupported() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
      runCatching { enterPictureInPictureMode(buildPipParams(isActiveVideoForPip)) }
    }
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (isActiveVideoForPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
      runCatching { enterPictureInPictureMode(buildPipParams(true)) }
    }
  }

  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    isInPipMode = isInPictureInPictureMode
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val route = intent.getStringExtra("route")
    if (!route.isNullOrBlank()) {
      pendingRoute = route
    }
    val wId = intent.getIntExtra("widget_id", -1)
    if (wId > 0) pendingWidgetId = wId
    val pId = intent.getLongExtra("project_id", -1L)
    if (pId > 0L) pendingProjectId = pId
  }

  override fun onResume() {
    super.onResume()
    isInPipMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) isInPictureInPictureMode else false
    lifecycleScope.launch {
      NexusWidgetUpdater.refreshAll(applicationContext)
    }
  }
}
