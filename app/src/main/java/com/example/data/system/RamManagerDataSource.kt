package com.example.data.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.example.data.model.ManagedAppSummary
import com.example.data.model.MetricValue
import com.example.data.model.SystemMemoryDetails
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RamManagerDataSource(private val context: Context) {

  suspend fun readSystemMemoryDetails(): SystemMemoryDetails = withContext(Dispatchers.IO) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)

    val total = memInfo.totalMem.coerceAtLeast(0L)
    val avail = memInfo.availMem.coerceAtLeast(0L)
    val used = (total - avail).coerceAtLeast(0L)
    val pct = if (total > 0L) ((used.toFloat() / total.toFloat()) * 100f).coerceIn(0f, 100f) else 0f

    val pssKb: MetricValue<Int> = runCatching {
      val procMem = am?.getProcessMemoryInfo(intArrayOf(Process.myPid()))
      val totalPss = procMem?.firstOrNull()?.totalPss ?: -1
      if (totalPss > 0) MetricValue.Available(totalPss) else MetricValue.Unavailable("Unavailable")
    }.getOrElse { MetricValue.Unavailable("Restricted") }

    val procMemMap = readProcMemInfoKb()
    fun kbMetric(key: String): MetricValue<Long> {
      val kb = procMemMap[key]
      return if (kb != null && kb >= 0L) MetricValue.Available(kb * 1024L)
      else MetricValue.Unavailable("Restricted by OS")
    }

    val visibleProcCount = runCatching { am?.runningAppProcesses?.size ?: 0 }.getOrDefault(0)

    SystemMemoryDetails(
      totalRamBytes = total,
      availableRamBytes = avail,
      usedRamBytes = used,
      usagePercent = pct,
      lowMemoryThresholdBytes = memInfo.threshold.coerceAtLeast(0L),
      isLowMemory = memInfo.lowMemory,
      nexusAppPssKb = pssKb,
      cachedMemoryBytes = kbMetric("Cached"),
      buffersBytes = kbMetric("Buffers"),
      swapTotalBytes = kbMetric("SwapTotal"),
      swapFreeBytes = kbMetric("SwapFree"),
      visibleProcessCount = visibleProcCount,
    )
  }

  @Suppress("DEPRECATION")
  suspend fun loadManageableApps(): List<ManagedAppSummary> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
      pm.queryIntentActivities(launcherIntent, 0)
    }

    val seen = mutableSetOf<String>()
    val result = mutableListOf<ManagedAppSummary>()
    for (ri in resolveInfos) {
      val pkg = ri.activityInfo?.packageName ?: continue
      if (!seen.add(pkg)) continue
      val pkgInfo = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
        } else {
          pm.getPackageInfo(pkg, 0)
        }
      }.getOrNull() ?: continue

      val appInfo = pkgInfo.applicationInfo
      val label = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
      val isSystem = appInfo != null &&
        ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
          (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
      val size = runCatching {
        val src = appInfo?.sourceDir
        if (!src.isNullOrBlank()) {
          val len = File(src).length()
          if (len > 0L) MetricValue.Available(len) else MetricValue.Unavailable("N/A")
        } else MetricValue.Unavailable("N/A")
      }.getOrElse { MetricValue.Unavailable("N/A") }

      val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.longVersionCode
      } else {
        pkgInfo.versionCode.toLong()
      }

      result.add(
        ManagedAppSummary(
          appName = label,
          packageName = pkg,
          versionName = pkgInfo.versionName ?: "N/A",
          versionCode = versionCode,
          isSystemApp = isSystem,
          isLaunchable = true,
          apkSizeBytes = size,
          installedAtMs = pkgInfo.firstInstallTime,
          updatedAtMs = pkgInfo.lastUpdateTime,
          targetSdk = appInfo?.targetSdkVersion ?: 0,
          requestedPermissions = emptyList(),
          foregroundUsageMs = null,
          lastUsedMs = null,
          icon = runCatching { appInfo?.loadIcon(pm) }.getOrNull(),
        )
      )
    }
    result.sortedBy { it.appName.lowercase() }
  }

  suspend fun clearOwnAppCache(): Long = withContext(Dispatchers.IO) {
    var freed = 0L
    freed += deleteDirContents(context.cacheDir)
    context.externalCacheDir?.let { freed += deleteDirContents(it) }
    System.gc()
    freed
  }

  private fun deleteDirContents(dir: File?): Long {
    if (dir == null || !dir.exists() || !dir.isDirectory) return 0L
    var freed = 0L
    dir.listFiles()?.forEach { child ->
      if (child.isDirectory) {
        freed += deleteDirContents(child)
        child.delete()
      } else {
        val len = child.length()
        if (child.delete()) freed += len
      }
    }
    return freed
  }

  private fun readProcMemInfoKb(): Map<String, Long> {
    return runCatching {
      val file = File("/proc/meminfo")
      if (!file.canRead()) return@runCatching emptyMap()
      val map = mutableMapOf<String, Long>()
      file.forEachLine { line ->
        val parts = line.split(":")
        if (parts.size == 2) {
          val key = parts[0].trim()
          val num = parts[1].trim().substringBefore(" ").toLongOrNull()
          if (num != null) map[key] = num
        }
      }
      map
    }.getOrDefault(emptyMap())
  }
}
