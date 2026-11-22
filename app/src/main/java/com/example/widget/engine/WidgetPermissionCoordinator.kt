package com.example.widget.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.data.system.PermissionManager

/**
 * Centralized capability-based permission coordinator for all current and future widgets.
 * Single source of truth wrapping PermissionManager without duplicate permission logic.
 */
class WidgetPermissionCoordinator(
  private val context: Context,
  private val permissionManager: PermissionManager,
) {

  fun statusFor(capability: WidgetCapability): CapabilityStatus {
    return when (capability) {
      WidgetCapability.TIME,
      WidgetCapability.BATTERY,
      WidgetCapability.CHARGING,
      WidgetCapability.MEMORY,
      WidgetCapability.STORAGE,
      WidgetCapability.DEVICE,
      WidgetCapability.PHOTOS,
      WidgetCapability.CUSTOM_DATA -> CapabilityStatus.GRANTED

      WidgetCapability.NETWORK -> {
        if (permissionManager.hasPermission(Manifest.permission.ACCESS_NETWORK_STATE)) {
          CapabilityStatus.GRANTED
        } else {
          CapabilityStatus.UNAVAILABLE
        }
      }

      WidgetCapability.MEDIA_SESSION,
      WidgetCapability.NOTIFICATIONS -> {
        if (permissionManager.isNotificationAccessEnabled()) {
          CapabilityStatus.GRANTED
        } else {
          CapabilityStatus.MISSING_SPECIAL_SETTINGS
        }
      }

      WidgetCapability.CALENDAR -> {
        if (permissionManager.isCalendarGranted()) {
          CapabilityStatus.GRANTED
        } else {
          CapabilityStatus.MISSING_RUNTIME
        }
      }

      WidgetCapability.LOCATION_WEATHER -> {
        if (permissionManager.isCoarseLocationGranted()) {
          CapabilityStatus.GRANTED
        } else {
          CapabilityStatus.MISSING_RUNTIME
        }
      }
    }
  }

  fun isGranted(capability: WidgetCapability): Boolean = statusFor(capability) == CapabilityStatus.GRANTED

  fun requiredRuntimePermissions(capability: WidgetCapability): List<String> {
    return when (capability) {
      WidgetCapability.CALENDAR -> listOf(Manifest.permission.READ_CALENDAR)
      WidgetCapability.LOCATION_WEATHER -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
      WidgetCapability.NOTIFICATIONS -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          emptyList()
        }
      }
      else -> emptyList()
    }
  }

  fun buildActionableIntent(capability: WidgetCapability): Intent? {
    return when (capability) {
      WidgetCapability.MEDIA_SESSION,
      WidgetCapability.NOTIFICATIONS -> permissionManager.buildNotificationAccessIntent()

      WidgetCapability.CALENDAR,
      WidgetCapability.LOCATION_WEATHER -> permissionManager.buildAppDetailsIntent()

      WidgetCapability.TIME -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        } else null
      }

      else -> null
    }
  }

  fun missingCapabilitiesReport(capabilities: Collection<WidgetCapability>): Map<String, Any> {
    val map = LinkedHashMap<String, Any>()
    capabilities.forEach { cap ->
      val st = statusFor(cap)
      map[cap.id] = mapOf(
        "granted" to (st == CapabilityStatus.GRANTED),
        "status" to st.name,
        "explanation" to cap.explanation,
      )
    }
    return map
  }
}
