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

  fun formatCurrentMa(microAmps: Int): String {
    val ma = microAmps / 1000.0
    val sign = if (ma > 0) "+" else if (ma < 0) "-" else ""
    return String.format(Locale.US, "%s%.0f mA", sign, abs(ma))
  }
}
