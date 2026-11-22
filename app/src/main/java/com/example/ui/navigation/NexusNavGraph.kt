package com.example.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.SecuritySettingsConfig
import com.example.di.NexusContainer
import com.example.gesture.nexusGesturePad
import com.example.security.AppLockMonitorCoordinator
import com.example.security.NexusSecurityManager
import com.example.ui.components.SecurityLockGatePane
import com.example.ui.screens.ActivityLauncherScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AppManagerScreen
import com.example.ui.screens.BatteryScreen
import com.example.ui.screens.CustomizationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DepthClockScreen
import com.example.ui.screens.EdgePanelScreen
import com.example.ui.screens.FloatingToolsScreen
import com.example.ui.screens.GestureStudioScreen
import com.example.ui.screens.HardwareScreen
import com.example.ui.screens.LiveWallpaperStudioScreen
import com.example.ui.screens.MusicPlayerScreen
import com.example.ui.screens.NotificationStudioScreen
import com.example.ui.screens.PermissionsAuditScreen
import com.example.ui.screens.ScreenRecorderScreen
import com.example.ui.screens.SecureVaultScreen
import com.example.ui.screens.ShortcutsScreen
import com.example.ui.screens.SoundBoosterScreen
import com.example.ui.screens.UtilitiesHubScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.WidgetStudioScreen
import com.example.ui.screens.WidgetsGuideScreen
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.ActivityLauncherViewModel
import com.example.ui.viewmodel.AppLockViewModel
import com.example.ui.viewmodel.AppManagerViewModel
import com.example.ui.viewmodel.BatteryViewModel
import com.example.ui.viewmodel.CustomizationViewModel
import com.example.ui.viewmodel.DepthClockViewModel
import com.example.ui.viewmodel.GestureStudioViewModel
import com.example.ui.viewmodel.HardwareViewModel
import com.example.ui.viewmodel.LiveWallpaperViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MusicPlayerViewModel
import com.example.ui.viewmodel.NotificationStudioViewModel
import com.example.ui.viewmodel.OverlayHubViewModel
import com.example.ui.viewmodel.ScreenRecorderViewModel
import com.example.ui.viewmodel.SecureVaultViewModel
import com.example.ui.viewmodel.ShortcutsViewModel
import com.example.ui.viewmodel.SoundBoosterViewModel
import com.example.ui.viewmodel.VideoPlayerViewModel
import com.example.ui.viewmodel.WidgetStudioViewModel
import kotlinx.coroutines.launch

object NexusRoutes {
  const val DASHBOARD = "dashboard"
  const val BATTERY = "battery"
  const val UTILITIES = "utilities"
  const val WALLPAPER = "wallpaper"
  const val LAUNCHER = "launcher"
  const val WIDGETS = "widgets"
  const val WIDGET_STUDIO = "widget_studio"
  const val DEPTH_CLOCK = "depth_clock"
  const val MUSIC = "music"
  const val VIDEO = "video"
  const val SOUND_BOOSTER = "sound_booster"
  const val SHORTCUTS = "shortcuts"
  const val APP_MANAGER = "app_manager"
  const val NOTIFICATIONS = "notifications"
  const val RECORDER = "recorder"
  const val HARDWARE = "hardware"
  const val PERMISSIONS = "permissions"
  const val CUSTOMIZE = "customize"
  const val GESTURE_STUDIO = "gesture_studio"
  const val FLOATING_TOOLS = "floating_tools"
  const val EDGE_PANEL = "edge_panel"
  const val SECURE_VAULT = "secure_vault"
  const val APP_LOCK = "app_lock"
}

private data class TopLevelNavItem(
  val route: String,
  val label: String,
  val icon: ImageVector,
  val testTag: String,
)

private val topLevelDestinations = listOf(
  TopLevelNavItem(NexusRoutes.DASHBOARD, "Dashboard", Icons.Default.Dashboard, "nav_dashboard"),
  TopLevelNavItem(NexusRoutes.BATTERY, "Battery", Icons.Default.BatteryChargingFull, "nav_battery"),
  TopLevelNavItem(NexusRoutes.APP_LOCK, "App Lock", Icons.Default.Lock, "nav_applock"),
  TopLevelNavItem(NexusRoutes.UTILITIES, "Tools", Icons.Default.Build, "nav_utilities"),
)

@Composable
fun NexusRootShell(
  mainViewModel: MainViewModel,
  pendingRoute: String? = null,
  onPendingRouteConsumed: () -> Unit = {},
  pendingWidgetId: Int? = null,
  pendingProjectId: Long? = null,
  onPendingWidgetTargetConsumed: () -> Unit = {},
  isInPipMode: Boolean = false,
  onActiveVideoForPipChanged: (Boolean) -> Unit = {},
  onEnterPip: () -> Unit = {},
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val density = LocalDensity.current.density
  val container = remember(context) { NexusContainer.get(context) }
  val scope = rememberCoroutineScope()

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: NexusRoutes.DASHBOARD
  val isTopLevel = topLevelDestinations.any { it.route == currentRoute }

  val gestureProfiles by container.database.gestureStudioDao().observeProfiles()
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val activeGestureProfile = gestureProfiles.firstOrNull { it.isActive }

  fun navigateToTopLevel(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  fun navigateToModule(route: String) {
    if (topLevelDestinations.any { it.route == route }) {
      navigateToTopLevel(route)
    } else {
      navController.navigate(route) {
        launchSingleTop = true
      }
    }
  }

  LaunchedEffect(pendingRoute) {
    val target = pendingRoute
    if (!target.isNullOrBlank()) {
      navigateToModule(target)
      onPendingRouteConsumed()
    }
  }

  LaunchedEffect(container) {
    container.actionDispatcher.uiEvents.collect { event ->
      val route = event.routeTarget
      if (!route.isNullOrBlank()) {
        navigateToModule(route)
      }
    }
  }

  val gestureModifier = if (!isInPipMode &&
    currentRoute != NexusRoutes.GESTURE_STUDIO &&
    activeGestureProfile?.inAppEnabled == true
  ) {
    Modifier.nexusGesturePad(
      sensitivity = activeGestureProfile.sensitivity,
      density = density,
      pass = PointerEventPass.Main,
    ) { type, sx, sy, w, h ->
      scope.launch {
        container.gestureRepository.dispatchDetectedGesture(context, type, sx, sy, w, h)
      }
    }
  } else {
    Modifier
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background)
      .then(gestureModifier),
  ) {
    val useNavRail = maxWidth >= 600.dp

    if (useNavRail) {
      Row(modifier = Modifier.fillMaxSize()) {
        if (isTopLevel && !isInPipMode) {
          NavigationRail(
            containerColor = colors.cardSurface,
            contentColor = colors.textPrimary,
            modifier = Modifier
              .fillMaxHeight()
              .statusBarsPadding(),
          ) {
            topLevelDestinations.forEach { dest ->
              val selected = currentRoute == dest.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTopLevel(dest.route) },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                  selectedIconColor = colors.accent,
                  selectedTextColor = colors.accentSecondary,
                  indicatorColor = colors.accent.copy(alpha = 0.16f),
                  unselectedIconColor = colors.textSecondary,
                  unselectedTextColor = colors.textSecondary,
                ),
                modifier = Modifier.testTag(dest.testTag),
              )
            }
          }
        }
        Box(modifier = Modifier.weight(1f)) {
          NexusNavHost(
            navController = navController,
            mainViewModel = mainViewModel,
            onNavigate = ::navigateToModule,
            contentPadding = PaddingValues(bottom = if (isInPipMode) 0.dp else 16.dp),
            pendingWidgetId = pendingWidgetId,
            pendingProjectId = pendingProjectId,
            onPendingWidgetTargetConsumed = onPendingWidgetTargetConsumed,
            isInPipMode = isInPipMode,
            onActiveVideoForPipChanged = onActiveVideoForPipChanged,
            onEnterPip = onEnterPip,
          )
        }
      }
    } else {
      Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
          if (isTopLevel && !isInPipMode) {
            NavigationBar(
              containerColor = colors.cardSurface.copy(alpha = 0.96f),
              contentColor = colors.textPrimary,
              windowInsets = WindowInsets.navigationBars,
            ) {
              topLevelDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationBarItem(
                  selected = selected,
                  onClick = { navigateToTopLevel(dest.route) },
                  icon = { Icon(dest.icon, contentDescription = dest.label) },
                  label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                  colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accentSecondary,
                    indicatorColor = colors.accent.copy(alpha = 0.16f),
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                  ),
                  modifier = Modifier.testTag(dest.testTag),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        NexusNavHost(
          navController = navController,
          mainViewModel = mainViewModel,
          onNavigate = ::navigateToModule,
          contentPadding = innerPadding,
          pendingWidgetId = pendingWidgetId,
          pendingProjectId = pendingProjectId,
          onPendingWidgetTargetConsumed = onPendingWidgetTargetConsumed,
          isInPipMode = isInPipMode,
          onActiveVideoForPipChanged = onActiveVideoForPipChanged,
          onEnterPip = onEnterPip,
        )
      }
    }
  }
}

@Composable
private fun ProtectedRouteGate(
  route: String,
  moduleLabel: String,
  onBack: (() -> Unit)?,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val container = remember(context) { NexusContainer.get(context) }
  val secConfig by container.advancedPreferences.securityConfig
    .collectAsStateWithLifecycle(initialValue = SecuritySettingsConfig())
  val profiles by container.database.appLockDao().observeProfiles()
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val unlockedRoutes by NexusSecurityManager.unlockedNexusRoutes.collectAsStateWithLifecycle()

  val activeProfile = profiles.firstOrNull { it.isActive }
  val protectedRoutes = remember(activeProfile) {
    activeProfile?.let { AppLockMonitorCoordinator.parseStringList(it.protectedNexusRoutesJson) }.orEmpty()
  }
  val isRouteGuarded = secConfig.appLockEnabled &&
    secConfig.hasPinConfigured &&
    route in protectedRoutes &&
    route != NexusRoutes.SECURE_VAULT // SecureVaultScreen enforces its own AES hardware gate

  val isCurrentlyUnlocked = if (!isRouteGuarded) {
    true
  } else {
    val ts = unlockedRoutes[route]
    val delayMs = activeProfile?.autoLockDelayMs ?: 0L
    ts != null && (delayMs <= 0L || (System.currentTimeMillis() - ts) <= delayMs)
  }

  if (isCurrentlyUnlocked) {
    content()
  } else {
    val scope = rememberCoroutineScope()
    var pinError by remember(route) { mutableStateOf<String?>(null) }
    SecurityLockGatePane(
      title = "$moduleLabel Locked",
      subtitle = "Protected by NEXUS Internal App Lock",
      hasPinConfigured = secConfig.hasPinConfigured,
      activeLockType = secConfig.activeLockType,
      biometricEnabled = secConfig.biometricEnabled,
      failedAttempts = secConfig.failedAttempts,
      lockoutUntilMs = secConfig.lockoutUntilMs,
      externalErrorMessage = pinError,
      onSetupPin = { pin ->
        scope.launch {
          val res = NexusSecurityManager.configurePin(pin, container.advancedPreferences, container.database.appLockDao())
          if (res.isSuccess) {
            NexusSecurityManager.markNexusRouteUnlocked(route)
            pinError = null
          } else {
            pinError = res.exceptionOrNull()?.message
          }
        }
      },
      onSetupCredential = { type, secret ->
        scope.launch {
          val res = NexusSecurityManager.configureCredential(type, secret, container.advancedPreferences, container.database.appLockDao())
          if (res.isSuccess) {
            NexusSecurityManager.markNexusRouteUnlocked(route)
            pinError = null
          } else {
            pinError = res.exceptionOrNull()?.message
          }
        }
      },
      onSubmitPin = { pin ->
        scope.launch {
          val res = NexusSecurityManager.verifyCredential(
            secret = pin,
            targetType = "APP_LOCK_INTERNAL",
            targetName = moduleLabel,
            prefs = container.advancedPreferences,
            appLockDao = container.database.appLockDao(),
            nexusRouteToUnlock = route,
          )
          pinError = if (res.success) null else res.message
        }
      },
      onRequestBiometric = { activity ->
        NexusSecurityManager.showBiometricPrompt(
          activity = activity,
          title = "Unlock $moduleLabel",
          subtitle = "Confirm identity to access locked NEXUS module",
          onSuccess = {
            scope.launch {
              NexusSecurityManager.recordBiometricOutcome(
                success = true,
                targetType = "APP_LOCK_INTERNAL",
                targetName = moduleLabel,
                reason = "",
                appLockDao = container.database.appLockDao(),
                nexusRouteToUnlock = route,
              )
            }
          },
          onFailure = { msg ->
            pinError = msg
            scope.launch {
              NexusSecurityManager.recordBiometricOutcome(
                success = false,
                targetType = "APP_LOCK_INTERNAL",
                targetName = moduleLabel,
                reason = msg,
                appLockDao = container.database.appLockDao(),
              )
            }
          },
        )
      },
      onBack = onBack,
      testTagPrefix = "applock_internal_gate",
    )
  }
}

@Composable
private fun NexusNavHost(
  navController: androidx.navigation.NavHostController,
  mainViewModel: MainViewModel,
  onNavigate: (String) -> Unit,
  contentPadding: PaddingValues,
  pendingWidgetId: Int? = null,
  pendingProjectId: Long? = null,
  onPendingWidgetTargetConsumed: () -> Unit = {},
  isInPipMode: Boolean = false,
  onActiveVideoForPipChanged: (Boolean) -> Unit = {},
  onEnterPip: () -> Unit = {},
) {
  var pendingVideoToOpen by remember { mutableStateOf<Pair<Uri, String>?>(null) }

  NavHost(
    navController = navController,
    startDestination = NexusRoutes.DASHBOARD,
    modifier = Modifier.fillMaxSize(),
  ) {
    composable(NexusRoutes.DASHBOARD) {
      DashboardScreen(
        viewModel = mainViewModel,
        onNavigate = onNavigate,
        contentPadding = contentPadding,
      )
    }
    composable(NexusRoutes.BATTERY) {
      ProtectedRouteGate(NexusRoutes.BATTERY, "Battery & Charging", null) {
        val vm: BatteryViewModel = viewModel()
        BatteryScreen(
          viewModel = vm,
          onBack = null,
          contentPadding = contentPadding,
        )
      }
    }
    composable(NexusRoutes.UTILITIES) {
      UtilitiesHubScreen(
        onNavigate = onNavigate,
        contentPadding = contentPadding,
      )
    }
    composable(NexusRoutes.WALLPAPER) {
      ProtectedRouteGate(NexusRoutes.WALLPAPER, "Live Wallpaper Studio", { navController.popBackStack() }) {
        val vm: LiveWallpaperViewModel = viewModel()
        LiveWallpaperStudioScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.LAUNCHER) {
      ProtectedRouteGate(NexusRoutes.LAUNCHER, "Shortcut Studio", { navController.popBackStack() }) {
        val vm: ActivityLauncherViewModel = viewModel()
        ActivityLauncherScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.WIDGETS) {
      ProtectedRouteGate(NexusRoutes.WIDGETS, "Home Widgets", { navController.popBackStack() }) {
        WidgetsGuideScreen(
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.WIDGET_STUDIO) {
      ProtectedRouteGate(NexusRoutes.WIDGET_STUDIO, "Widget Studio", { navController.popBackStack() }) {
        val vm: WidgetStudioViewModel = viewModel()
        WidgetStudioScreen(
          viewModel = vm,
          pendingWidgetId = pendingWidgetId,
          pendingProjectId = pendingProjectId,
          onPendingTargetConsumed = onPendingWidgetTargetConsumed,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.DEPTH_CLOCK) {
      ProtectedRouteGate(NexusRoutes.DEPTH_CLOCK, "Depth Clock Studio", { navController.popBackStack() }) {
        val vm: DepthClockViewModel = viewModel()
        DepthClockScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.MUSIC) {
      ProtectedRouteGate(NexusRoutes.MUSIC, "Music Player", { navController.popBackStack() }) {
        val vm: MusicPlayerViewModel = viewModel()
        MusicPlayerScreen(
          viewModel = vm,
          onOpenSoundBooster = { onNavigate(NexusRoutes.SOUND_BOOSTER) },
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.VIDEO) {
      ProtectedRouteGate(NexusRoutes.VIDEO, "Video Player", { navController.popBackStack() }) {
        val vm: VideoPlayerViewModel = viewModel()
        LaunchedEffect(pendingVideoToOpen) {
          val target = pendingVideoToOpen
          if (target != null) {
            vm.openVideoUri(target.first, target.second)
            pendingVideoToOpen = null
          }
        }
        VideoPlayerScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
          isInPipMode = isInPipMode,
          onActiveVideoForPipChanged = onActiveVideoForPipChanged,
          onEnterPipRequest = onEnterPip,
        )
      }
    }
    composable(NexusRoutes.SOUND_BOOSTER) {
      ProtectedRouteGate(NexusRoutes.SOUND_BOOSTER, "Sound Booster", { navController.popBackStack() }) {
        val vm: SoundBoosterViewModel = viewModel()
        SoundBoosterScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.SHORTCUTS) {
      ProtectedRouteGate(NexusRoutes.SHORTCUTS, "Quick Controls", { navController.popBackStack() }) {
        val vm: ShortcutsViewModel = viewModel()
        ShortcutsScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.APP_MANAGER) {
      ProtectedRouteGate(NexusRoutes.APP_MANAGER, "App Manager", { navController.popBackStack() }) {
        val vm: AppManagerViewModel = viewModel()
        AppManagerScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.NOTIFICATIONS) {
      ProtectedRouteGate(NexusRoutes.NOTIFICATIONS, "Notification Studio", { navController.popBackStack() }) {
        val vm: NotificationStudioViewModel = viewModel()
        NotificationStudioScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.RECORDER) {
      ProtectedRouteGate(NexusRoutes.RECORDER, "Screen Recorder", { navController.popBackStack() }) {
        val vm: ScreenRecorderViewModel = viewModel()
        ScreenRecorderScreen(
          viewModel = vm,
          onPlayVideo = { uri, title ->
            pendingVideoToOpen = uri to title
            onNavigate(NexusRoutes.VIDEO)
          },
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.HARDWARE) {
      ProtectedRouteGate(NexusRoutes.HARDWARE, "Device Info", { navController.popBackStack() }) {
        val vm: HardwareViewModel = viewModel()
        HardwareScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.PERMISSIONS) {
      PermissionsAuditScreen(
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.CUSTOMIZE) {
      val vm: CustomizationViewModel = viewModel()
      CustomizationScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.GESTURE_STUDIO) {
      ProtectedRouteGate(NexusRoutes.GESTURE_STUDIO, "Gesture Studio", { navController.popBackStack() }) {
        val vm: GestureStudioViewModel = viewModel()
        GestureStudioScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.FLOATING_TOOLS) {
      ProtectedRouteGate(NexusRoutes.FLOATING_TOOLS, "Floating Tools", { navController.popBackStack() }) {
        val vm: OverlayHubViewModel = viewModel()
        FloatingToolsScreen(
          viewModel = vm,
          onNavigateModule = onNavigate,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.EDGE_PANEL) {
      ProtectedRouteGate(NexusRoutes.EDGE_PANEL, "Edge Panel", { navController.popBackStack() }) {
        val vm: OverlayHubViewModel = viewModel()
        EdgePanelScreen(
          viewModel = vm,
          onBack = { navController.popBackStack() },
          contentPadding = PaddingValues(),
        )
      }
    }
    composable(NexusRoutes.SECURE_VAULT) {
      val vm: SecureVaultViewModel = viewModel()
      SecureVaultScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.APP_LOCK) {
      val vm: AppLockViewModel = viewModel()
      AppLockScreen(
        viewModel = vm,
        onBack = null,
        contentPadding = contentPadding,
      )
    }
  }
}
