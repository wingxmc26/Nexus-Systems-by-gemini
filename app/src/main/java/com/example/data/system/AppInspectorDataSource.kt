package com.example.data.system

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.model.AppPermissionItem
import com.example.data.model.InstalledAppEntry
import com.example.data.model.MetricValue
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppInspectorDataSource(private val context: Context) {

  @Suppress("DEPRECATION")
  suspend fun loadLaunchableApps(): List<InstalledAppEntry> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
      pm.queryIntentActivities(launcherIntent, 0)
    }

    val seenPackages = mutableSetOf<String>()
    val entries = mutableListOf<InstalledAppEntry>()

    for (ri in resolveInfos) {
      val pkg = ri.activityInfo?.packageName ?: continue
      if (!seenPackages.add(pkg)) continue

      val pkgInfo = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
          pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        }
      }.getOrNull() ?: continue

      val appInfo = pkgInfo.applicationInfo
      val label = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
      val isSystem = appInfo != null &&
        ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
          (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)

      val apkSize: MetricValue<Long> = runCatching {
        val src = appInfo?.sourceDir
        if (!src.isNullOrBlank()) {
          val len = File(src).length()
          if (len > 0L) MetricValue.Available(len) else MetricValue.Unavailable("Size unavailable")
        } else {
          MetricValue.Unavailable("Path restricted")
        }
      }.getOrElse { MetricValue.Unavailable("Restricted") }

      val perms = extractPermissions(pkgInfo)
      val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.longVersionCode
      } else {
        pkgInfo.versionCode.toLong()
      }

      val icon = runCatching { appInfo?.loadIcon(pm) }.getOrNull()
      val canLaunch = pm.getLaunchIntentForPackage(pkg) != null

      entries.add(
        InstalledAppEntry(
          appName = label,
          packageName = pkg,
          versionName = pkgInfo.versionName ?: "N/A",
          versionCode = verCode,
          targetSdk = appInfo?.targetSdkVersion ?: 0,
          isSystemApp = isSystem,
          apkSizeBytes = apkSize,
          permissions = perms,
          canLaunch = canLaunch,
          icon = icon,
        )
      )
    }

    entries.sortedBy { it.appName.lowercase() }
  }

  private fun extractPermissions(pkgInfo: PackageInfo): List<AppPermissionItem> {
    val req = pkgInfo.requestedPermissions ?: return emptyList()
    val flags = pkgInfo.requestedPermissionsFlags
    return req.mapIndexed { idx, permName ->
      val granted = flags != null && idx < flags.size &&
        (flags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
      AppPermissionItem(
        name = permName.removePrefix("android.permission."),
        isGranted = granted,
      )
    }
  }
}
