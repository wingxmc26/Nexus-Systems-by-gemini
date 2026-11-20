package com.example.data.system

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.data.model.ManagedAppSummary
import com.example.data.model.MetricValue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppManagerDataSource(
  private val context: Context,
  private val permissionManager: PermissionManager,
) {

  @Suppress("DEPRECATION")
  suspend fun loadInstalledApps(): List<ManagedAppSummary> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val flags = PackageManager.GET_PERMISSIONS
    val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
      pm.getInstalledPackages(flags)
    }

    val usageMap = if (permissionManager.isUsageStatsGranted()) {
      readRecentUsageMap()
    } else {
      emptyMap()
    }

    installed.map { pkgInfo ->
      val appInfo = pkgInfo.applicationInfo
      val label = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkgInfo.packageName
      val isSystem = appInfo != null &&
        ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
          (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
      val apkSize = runCatching {
        val src = appInfo?.sourceDir
        if (!src.isNullOrBlank()) {
          val bytes = File(src).length()
          if (bytes > 0L) MetricValue.Available(bytes) else MetricValue.Unavailable("Unavailable")
        } else MetricValue.Unavailable("Unavailable")
      }.getOrElse { MetricValue.Unavailable("Unavailable") }

      val usage = usageMap[pkgInfo.packageName]
      ManagedAppSummary(
        appName = label,
        packageName = pkgInfo.packageName,
        versionName = pkgInfo.versionName ?: "N/A",
        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode else pkgInfo.versionCode.toLong(),
        isSystemApp = isSystem,
        isLaunchable = pm.getLaunchIntentForPackage(pkgInfo.packageName) != null,
        apkSizeBytes = apkSize,
        installedAtMs = pkgInfo.firstInstallTime,
        updatedAtMs = pkgInfo.lastUpdateTime,
        targetSdk = appInfo?.targetSdkVersion ?: 0,
        requestedPermissions = pkgInfo.requestedPermissions?.toList().orEmpty(),
        foregroundUsageMs = usage?.first,
        lastUsedMs = usage?.second,
        icon = runCatching { appInfo?.loadIcon(pm) }.getOrNull(),
      )
    }.sortedBy { it.appName.lowercase() }
  }

  private fun readRecentUsageMap(): Map<String, Pair<Long, Long>> {
    return runCatching {
      val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
      val end = System.currentTimeMillis()
      val start = end - (7L * 24L * 3600L * 1000L)
      val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return emptyMap()
      val map = mutableMapOf<String, Pair<Long, Long>>()
      for (s in stats) {
        val prev = map[s.packageName]
        val fg = (prev?.first ?: 0L) + s.totalTimeInForeground
        val last = maxOf(prev?.second ?: 0L, s.lastTimeUsed)
        map[s.packageName] = fg to last
      }
      map
    }.getOrDefault(emptyMap())
  }

  fun launchApp(packageName: String): Boolean {
    return runCatching {
      val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      } ?: return false
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  fun openAppInfo(packageName: String): Boolean {
    return runCatching {
      val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  fun requestSystemUninstall(packageName: String): Boolean {
    return runCatching {
      val intent = Intent(Intent.ACTION_DELETE).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  fun openDefaultAppsSettings(): Boolean {
    return runCatching {
      val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  fun shareAppDetails(app: ManagedAppSummary) {
    val summary = buildString {
      appendLine("App: ${app.appName}")
      appendLine("Package: ${app.packageName}")
      appendLine("Version: ${app.versionName} (${app.versionCode})")
      appendLine("Target SDK: ${app.targetSdk}")
      appendLine("Permissions (${app.requestedPermissions.size}):")
      app.requestedPermissions.take(25).forEach { appendLine("- $it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, "${app.appName} (${app.packageName})")
      putExtra(Intent.EXTRA_TEXT, summary)
    }
    runCatching {
      context.startActivity(
        Intent.createChooser(intent, "Share App Info").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      )
    }
  }
}
