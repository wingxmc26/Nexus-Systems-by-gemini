package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Formatters {
  fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
      value /= 1024.0
      unitIndex++
    }
    return if (unitIndex == 0) {
      "$bytes B"
    } else {
      String.format(Locale.US, "%.2f %s", value, units[unitIndex])
    }
  }

  fun formatDuration(ms: Long): String {
    if (ms < 0L) return "0s"
    val totalSeconds = ms / 1000L
    val days = totalSeconds / 86400L
    val hours = (totalSeconds % 86400L) / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return buildString {
      if (days > 0) append("${days}d ")
      if (hours > 0 || days > 0) append("${hours}h ")
      append("${minutes}m ${seconds}s")
    }.trim()
  }

  fun formatTimer(ms: Long): String {
    if (ms <= 0L) return "00:00:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
  }

  fun formatFrequencyKhz(khz: Long): String {
    return if (khz >= 1_000_000L) {
      String.format(Locale.US, "%.2f GHz", khz / 1_000_000.0)
    } else {
      "${khz / 1000L} MHz"
    }
  }

  fun formatTimestamp(ms: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(ms))
  }

  fun formatDateTime(ms: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
  }

  fun formatDurationWords(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    if (totalSeconds < 60L) {
      return if (totalSeconds == 1L) "1 second" else "$totalSeconds seconds"
    }
    val totalMinutes = totalSeconds / 60L
    val hours = totalMinutes / 60L
    val mins = totalMinutes % 60L
    val parts = mutableListOf<String>()
    if (hours > 0L) {
      parts += if (hours == 1L) "1 hour" else "$hours hours"
    }
    if (mins > 0L || hours == 0L) {
      parts += if (mins == 1L) "1 minute" else "$mins minutes"
    }
    return parts.joinToString(" ")
  }

  fun formatClock12h(ms: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.US)
    return sdf.format(Date(ms))
  }

  fun formatChargingSessionLog(
    startPct: Int,
    endPct: Int,
    durationMs: Long,
    timestampMs: Long,
    isActive: Boolean,
  ): String {
    val durWords = formatDurationWords(durationMs)
    val timeStr = formatClock12h(timestampMs)
    return if (isActive) {
      "Charging $startPct-$endPct% • $durWords elapsed (started at $timeStr)"
    } else {
      "Charging $startPct-$endPct% in $durWords at $timeStr"
    }
  }

  fun formatCurrentMa(microAmps: Int, isCharging: Boolean): String {
    // BatteryManager.BATTERY_PROPERTY_CURRENT_NOW is documented in microamperes (µA).
    val ma = microAmps / 1000.0
    val absMa = abs(ma)
    val prefix = when {
      ma > 0 -> "+"
      ma < 0 -> "-"
      isCharging -> "+"
      else -> ""
    }
    return String.format(Locale.US, "%s%.0f mA", prefix, absMa)
  }
}
