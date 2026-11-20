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
import androidx.compose.material.icons.filled.Memory
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ActivityLauncherScreen
import com.example.ui.screens.AppManagerScreen
import com.example.ui.screens.BatteryScreen
import com.example.ui.screens.CustomizationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DepthClockScreen
import com.example.ui.screens.DisplayTesterScreen
import com.example.ui.screens.HardwareScreen
import com.example.ui.screens.LiveWallpaperStudioScreen
import com.example.ui.screens.MusicPlayerScreen
import com.example.ui.screens.NotificationStudioScreen
import com.example.ui.screens.PermissionsAuditScreen
import com.example.ui.screens.RamManagerScreen
import com.example.ui.screens.ScreenRecorderScreen
import com.example.ui.screens.SensorLabScreen
import com.example.ui.screens.ShortcutsScreen
import com.example.ui.screens.SoundBoosterScreen
import com.example.ui.screens.StorageScreen
import com.example.ui.screens.UtilitiesHubScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.WidgetStudioScreen
import com.example.ui.screens.WidgetsGuideScreen
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.ActivityLauncherViewModel
import com.example.ui.viewmodel.AppManagerViewModel
import com.example.ui.viewmodel.BatteryViewModel
import com.example.ui.viewmodel.CustomizationViewModel
import com.example.ui.viewmodel.DepthClockViewModel
import com.example.ui.viewmodel.DisplayTesterViewModel
import com.example.ui.viewmodel.HardwareViewModel
import com.example.ui.viewmodel.LiveWallpaperViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MusicPlayerViewModel
import com.example.ui.viewmodel.NotificationStudioViewModel
import com.example.ui.viewmodel.RamManagerViewModel
import com.example.ui.viewmodel.ScreenRecorderViewModel
import com.example.ui.viewmodel.SensorLabViewModel
import com.example.ui.viewmodel.ShortcutsViewModel
import com.example.ui.viewmodel.SoundBoosterViewModel
import com.example.ui.viewmodel.StorageViewModel
import com.example.ui.viewmodel.VideoPlayerViewModel
import com.example.ui.viewmodel.WidgetStudioViewModel

object NexusRoutes {
  const val DASHBOARD = "dashboard"
  const val BATTERY = "battery"
  const val RAM = "ram"
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
  const val STORAGE = "storage"
  const val APP_MANAGER = "app_manager"
  const val NOTIFICATIONS = "notifications"
  const val RECORDER = "recorder"
  const val HARDWARE = "hardware"
  const val SENSORS = "sensors"
  const val DISPLAY = "display"
  const val PERMISSIONS = "permissions"
  const val CUSTOMIZE = "customize"
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
  TopLevelNavItem(NexusRoutes.RAM, "RAM", Icons.Default.Memory, "nav_ram"),
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
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: NexusRoutes.DASHBOARD
  val isTopLevel = topLevelDestinations.any { it.route == currentRoute }

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

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
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
      val vm: BatteryViewModel = viewModel()
      BatteryScreen(
        viewModel = vm,
        onBack = null,
        contentPadding = contentPadding,
      )
    }
    composable(NexusRoutes.RAM) {
      val vm: RamManagerViewModel = viewModel()
      RamManagerScreen(
        viewModel = vm,
        onBack = null,
        contentPadding = contentPadding,
      )
    }
    composable(NexusRoutes.UTILITIES) {
      UtilitiesHubScreen(
        onNavigate = onNavigate,
        contentPadding = contentPadding,
      )
    }
    composable(NexusRoutes.WALLPAPER) {
      val vm: LiveWallpaperViewModel = viewModel()
      LiveWallpaperStudioScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.LAUNCHER) {
      val vm: ActivityLauncherViewModel = viewModel()
      ActivityLauncherScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.WIDGETS) {
      WidgetsGuideScreen(
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.WIDGET_STUDIO) {
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
    composable(NexusRoutes.DEPTH_CLOCK) {
      val vm: DepthClockViewModel = viewModel()
      DepthClockScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.MUSIC) {
      val vm: MusicPlayerViewModel = viewModel()
      MusicPlayerScreen(
        viewModel = vm,
        onOpenSoundBooster = { onNavigate(NexusRoutes.SOUND_BOOSTER) },
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.VIDEO) {
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
    composable(NexusRoutes.SOUND_BOOSTER) {
      val vm: SoundBoosterViewModel = viewModel()
      SoundBoosterScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.SHORTCUTS) {
      val vm: ShortcutsViewModel = viewModel()
      ShortcutsScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.STORAGE) {
      val vm: StorageViewModel = viewModel()
      StorageScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.APP_MANAGER) {
      val vm: AppManagerViewModel = viewModel()
      AppManagerScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.NOTIFICATIONS) {
      val vm: NotificationStudioViewModel = viewModel()
      NotificationStudioScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.RECORDER) {
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
    composable(NexusRoutes.HARDWARE) {
      val vm: HardwareViewModel = viewModel()
      HardwareScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.SENSORS) {
      val vm: SensorLabViewModel = viewModel()
      SensorLabScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.DISPLAY) {
      val vm: DisplayTesterViewModel = viewModel()
      DisplayTesterScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
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
  }
}
