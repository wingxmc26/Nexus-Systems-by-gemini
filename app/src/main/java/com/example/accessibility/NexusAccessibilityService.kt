package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.example.data.local.NexusGuardPrefs
import com.example.di.NexusContainer
import com.example.overlay.NexusEdgePanelService
import com.example.security.AppLockMonitorCoordinator
import com.example.security.NexusAppLockMonitorService
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Strictly optional AccessibilityService enabled only by explicit user consent in Android Settings.
 * Used exclusively for:
 *  - Optional global navigation actions from Gesture Studio (Back, Home, Recents, Notification Shade)
 *  - Instant foreground package change events & watchdog auto-healing for App Lock + Edge Bar
 *
 * Reads zero screen content (`canRetrieveWindowContent = false`).
 */
class NexusAccessibilityService : AccessibilityService() {

  companion object {
    @Volatile
    private var instanceRef: WeakReference<NexusAccessibilityService>? = null

    @Volatile
    var lastForegroundPackage: String = ""
      private set

    fun currentInstance(): NexusAccessibilityService? = instanceRef?.get()
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    instanceRef = WeakReference(this)
    healServicesIfEnabled()
    CoroutineScope(Dispatchers.IO).launch {
      AppLockMonitorCoordinator.syncProfileCache(applicationContext)
      val container = NexusContainer.get(applicationContext)
      val sec = container.advancedPreferences.getSecuritySnapshot()
      if (sec.appLockEnabled && sec.externalMonitorRunning && !NexusAppLockMonitorService.isRunning()) {
        runCatching { NexusAppLockMonitorService.start(applicationContext) }
      }
      val ep = container.advancedPreferences.getEdgePanelSnapshot()
      if (ep.enabled && Settings.canDrawOverlays(applicationContext) && !NexusEdgePanelService.isRunning()) {
        runCatching { NexusEdgePanelService.start(applicationContext) }
      }
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val type = event?.eventType ?: return
    if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      healServicesIfEnabled()
      val pkg = event.packageName?.toString()?.trim().orEmpty()
      if (pkg.isNotBlank() && pkg != lastForegroundPackage) {
        lastForegroundPackage = pkg
        AppLockMonitorCoordinator.onForegroundPackageObserved(applicationContext, pkg)
      }
    }
  }

  private fun healServicesIfEnabled() {
    if (NexusGuardPrefs.shouldRunAppLockGuard(applicationContext) && !NexusAppLockMonitorService.isRunning()) {
      runCatching { NexusAppLockMonitorService.start(applicationContext) }
    }
    if (NexusGuardPrefs.shouldRunEdgeBar(applicationContext) &&
      Settings.canDrawOverlays(applicationContext) &&
      !NexusEdgePanelService.isRunning()
    ) {
      runCatching { NexusEdgePanelService.start(applicationContext) }
    }
  }

  override fun onInterrupt() {
    // Nothing held to interrupt
  }

  override fun onDestroy() {
    instanceRef = null
    super.onDestroy()
  }

  fun performGlobalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
  fun performGlobalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
  fun performGlobalRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
  fun performGlobalNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
}
