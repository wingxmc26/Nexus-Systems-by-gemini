package com.example.widget.engine

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.widget.NexusBatteryWidgetProvider
import com.example.widget.NexusCustomWidgetProvider
import com.example.widget.NexusDepthClockWidgetProvider
import com.example.widget.NexusDeviceInfoWidgetProvider
import com.example.widget.NexusFavoritesWidgetProvider
import com.example.widget.NexusMusicWidgetProvider
import com.example.widget.NexusSystemStatusWidgetProvider
import com.example.widget.WidgetSizePreset

/**
 * Standardized registry for all built-in, custom, and extensible future widget types.
 * Each widget declares its capability dependencies, render mode, and recommended slot size so
 * permission gating, diagnostics, and background sync apply universally without per-widget hacks.
 */
object WidgetEngineRegistry {

  private val builtInAndSystemDescriptors: List<WidgetDescriptor> = listOf(
    WidgetDescriptor(
      id = "custom_studio",
      title = "Custom Studio Widget",
      category = "Custom Builder",
      providerClass = NexusCustomWidgetProvider::class.java,
      requiredCapabilities = setOf(WidgetCapability.TIME, WidgetCapability.CUSTOM_DATA),
      renderMode = WidgetRenderMode.HTML_HEADLESS,
      recommendedSizePreset = WidgetSizePreset.MEDIUM,
      supportsStyleCustomization = false,
    ),
    WidgetDescriptor(
      id = "depth_clock",
      title = "Depth Clock Widget",
      category = "Time & Clock",
      providerClass = NexusDepthClockWidgetProvider::class.java,
      requiredCapabilities = setOf(WidgetCapability.TIME),
      renderMode = WidgetRenderMode.VISUAL_CANVAS_WITH_LIVE_CLOCK,
      recommendedSizePreset = WidgetSizePreset.IOS_LARGE_CLOCK,
      supportsStyleCustomization = false,
    ),
    WidgetDescriptor(
      id = "music",
      title = "Music Playback Widget",
      category = "Media",
      providerClass = NexusMusicWidgetProvider::class.java,
      requiredCapabilities = setOf(WidgetCapability.MEDIA_SESSION),
      renderMode = WidgetRenderMode.REMOTE_VIEWS_NATIVE,
      recommendedSizePreset = WidgetSizePreset.WIDE,
      supportsStyleCustomization = true,
    ),
    WidgetDescriptor(
      id = "battery",
      title = "Battery & Charging",
      category = "Power & Telemetry",
      providerClass = NexusBatteryWidgetProvider::class.java,
      requiredCapabilities = setOf(WidgetCapability.BATTERY, WidgetCapability.CHARGING),
      renderMode = WidgetRenderMode.REMOTE_VIEWS_NATIVE,
      recommendedSizePreset = WidgetSizePreset.MEDIUM,
      supportsStyleCustomization = true,
    ),
    WidgetDescriptor(
      id = "system_status",
      title = "System Status",
      category = "Power & Telemetry",
      providerClass = NexusSystemStatusWidgetProvider::class.java,
      requiredCapabilities = setOf(WidgetCapability.MEMORY, WidgetCapability.STORAGE, WidgetCapability.BATTERY),
      renderMode = WidgetRenderMode.REMOTE_VIEWS_NATIVE,
      recommendedSizePreset = WidgetSizePreset.MEDIUM,
      supportsStyleCustomization = true,
    ),
    WidgetDescriptor(
      id = "device_info",
      title = "Device Info",
      category = "System",
      providerClass = NexusDeviceInfoWidgetProvider::class.java,
      requiredCapabilities = setOf(WidgetCapability.DEVICE),
      renderMode = WidgetRenderMode.REMOTE_VIEWS_NATIVE,
      recommendedSizePreset = WidgetSizePreset.MEDIUM,
      supportsStyleCustomization = true,
    ),
    WidgetDescriptor(
      id = "favorites",
      title = "Favorite Shortcuts",
      category = "Launcher Shortcuts",
      providerClass = NexusFavoritesWidgetProvider::class.java,
      requiredCapabilities = emptySet(),
      renderMode = WidgetRenderMode.REMOTE_VIEWS_NATIVE,
      recommendedSizePreset = WidgetSizePreset.MEDIUM,
      supportsStyleCustomization = true,
    ),
  )

  private val customDescriptors = LinkedHashMap<String, WidgetDescriptor>()

  fun registerDescriptor(descriptor: WidgetDescriptor) {
    synchronized(customDescriptors) {
      customDescriptors[descriptor.id] = descriptor
    }
  }

  fun getAllDescriptors(): List<WidgetDescriptor> = synchronized(customDescriptors) {
    builtInAndSystemDescriptors + customDescriptors.values.toList()
  }

  fun findById(id: String): WidgetDescriptor? = getAllDescriptors().firstOrNull { it.id == id }

  fun activeWidgetCount(context: Context): Int {
    val mgr = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return 0
    return getAllDescriptors()
      .map { it.providerClass }
      .distinct()
      .sumOf { cls ->
        runCatching { mgr.getAppWidgetIds(ComponentName(context, cls)).size }.getOrDefault(0)
      }
  }

  fun missingCapabilitiesFor(
    descriptor: WidgetDescriptor,
    coordinator: WidgetPermissionCoordinator,
  ): List<Pair<WidgetCapability, CapabilityStatus>> {
    return descriptor.requiredCapabilities.mapNotNull { cap ->
      val st = coordinator.statusFor(cap)
      if (st == CapabilityStatus.GRANTED) null else (cap to st)
    }
  }
}
