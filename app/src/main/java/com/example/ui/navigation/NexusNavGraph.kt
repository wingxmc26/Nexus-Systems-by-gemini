package com.example.ui.navigation

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AppInspectorScreen
import com.example.ui.screens.AudioDiagnosticsScreen
import com.example.ui.screens.BatteryScreen
import com.example.ui.screens.CustomizationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DisplayTesterScreen
import com.example.ui.screens.HardwareScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.SensorLabScreen
import com.example.ui.screens.ShortcutsScreen
import com.example.ui.screens.StorageScreen
import com.example.ui.screens.UtilitiesHubScreen
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.AppInspectorViewModel
import com.example.ui.viewmodel.AudioDiagnosticsViewModel
import com.example.ui.viewmodel.BatteryViewModel
import com.example.ui.viewmodel.CustomizationViewModel
import com.example.ui.viewmodel.DisplayTesterViewModel
import com.example.ui.viewmodel.HardwareViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.NetworkViewModel
import com.example.ui.viewmodel.SensorLabViewModel
import com.example.ui.viewmodel.ShortcutsViewModel
import com.example.ui.viewmodel.StorageViewModel

object NexusRoutes {
  const val DASHBOARD = "dashboard"
  const val BATTERY = "battery"
  const val HARDWARE = "hardware"
  const val UTILITIES = "utilities"
  const val STORAGE = "storage"
  const val NETWORK = "network"
  const val SENSORS = "sensors"
  const val DISPLAY = "display"
  const val AUDIO = "audio"
  const val APPS = "apps"
  const val SHORTCUTS = "shortcuts"
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
  TopLevelNavItem(NexusRoutes.HARDWARE, "Hardware", Icons.Default.Memory, "nav_hardware"),
  TopLevelNavItem(NexusRoutes.UTILITIES, "Lab & Tools", Icons.Default.Build, "nav_utilities"),
)

@Composable
fun NexusRootShell(mainViewModel: MainViewModel) {
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

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    val useNavRail = maxWidth >= 600.dp

    if (useNavRail) {
      Row(modifier = Modifier.fillMaxSize()) {
        if (isTopLevel) {
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
            contentPadding = PaddingValues(bottom = 16.dp),
          )
        }
      }
    } else {
      Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
          if (isTopLevel) {
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
) {
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
    composable(NexusRoutes.HARDWARE) {
      val vm: HardwareViewModel = viewModel()
      HardwareScreen(
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
    composable(NexusRoutes.STORAGE) {
      val vm: StorageViewModel = viewModel()
      StorageScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.NETWORK) {
      val vm: NetworkViewModel = viewModel()
      NetworkScreen(
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
    composable(NexusRoutes.AUDIO) {
      val vm: AudioDiagnosticsViewModel = viewModel()
      AudioDiagnosticsScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
        contentPadding = PaddingValues(),
      )
    }
    composable(NexusRoutes.APPS) {
      val vm: AppInspectorViewModel = viewModel()
      AppInspectorScreen(
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
