package com.example.data.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.data.model.ExportedActivityInfo
import com.example.data.model.LaunchablePackageInfo
import com.example.data.model.ShortcutShape
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PackageActivityDataSource(private val context: Context) {

  @Suppress("DEPRECATION")
  suspend fun loadPackagesWithActivities(): List<LaunchablePackageInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
      pm.queryIntentActivities(launcherIntent, 0)
    }

    val seen = mutableSetOf<String>()
    val result = mutableListOf<LaunchablePackageInfo>()

    for (ri in resolveInfos) {
      val pkg = ri.activityInfo?.packageName ?: continue
      if (!seen.add(pkg)) continue
      buildPackageInfo(pm, pkg)?.let { result.add(it) }
    }

    result.sortedBy { it.appName.lowercase() }
  }

  suspend fun inspectPackageByName(packageName: String): Result<LaunchablePackageInfo> = withContext(Dispatchers.IO) {
    runCatching {
      val clean = packageName.trim()
      require(clean.isNotEmpty()) { "Enter a valid package name (e.g., com.android.settings)" }
      buildPackageInfo(context.packageManager, clean)
        ?: error("Package '$clean' is not installed or hidden by Android package visibility rules")
    }
  }

  @Suppress("DEPRECATION")
  private fun buildPackageInfo(pm: PackageManager, pkg: String): LaunchablePackageInfo? {
    val pkgInfo: PackageInfo = runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
      } else {
        pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
      }
    }.getOrNull() ?: return null

    val appInfo = pkgInfo.applicationInfo
    val appName = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
    val isSystem = appInfo != null &&
      ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)

    val allActivities = pkgInfo.activities.orEmpty()
    val exported = allActivities
      .filter { it.exported && it.enabled }
      .map { act ->
        val label = runCatching { act.loadLabel(pm)?.toString() }.getOrNull()
          ?.takeIf { it.isNotBlank() } ?: act.name.substringAfterLast('.')
        ExportedActivityInfo(
          className = act.name,
          simpleName = act.name.substringAfterLast('.'),
          label = label,
          permissionRequired = act.permission,
        )
      }
      .sortedBy { it.simpleName.lowercase() }

    return LaunchablePackageInfo(
      appName = appName,
      packageName = pkg,
      versionName = pkgInfo.versionName ?: "N/A",
      targetSdk = appInfo?.targetSdkVersion ?: 0,
      isSystemApp = isSystem,
      hasLauncherIntent = pm.getLaunchIntentForPackage(pkg) != null,
      exportedActivities = exported,
      totalActivityCount = allActivities.size,
      icon = runCatching { appInfo?.loadIcon(pm) }.getOrNull(),
    )
  }

  fun launchTarget(packageName: String, activityClassName: String?): Result<Unit> {
    return runCatching {
      val intent = if (!activityClassName.isNullOrBlank()) {
        Intent().apply {
          component = ComponentName(packageName, activityClassName)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      } else {
        context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: error("No default launcher activity exposed for $packageName")
      }
      context.startActivity(intent)
    }
  }

  suspend fun saveUploadedIconLocally(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val dir = File(context.filesDir, "shortcut_icons")
      if (!dir.exists()) dir.mkdirs()
      val outFile = File(dir, "icon_${System.currentTimeMillis()}.png")
      val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
      } ?: error("Unable to decode selected image")
      val scaled = Bitmap.createScaledBitmap(raw, 256, 256, true)
      if (scaled !== raw) raw.recycle()
      FileOutputStream(outFile).use { fos ->
        scaled.compress(Bitmap.CompressFormat.PNG, 95, fos)
      }
      scaled.recycle()
      outFile.absolutePath
    }
  }

  fun isPinShortcutSupported(): Boolean {
    return runCatching { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }.getOrDefault(false)
  }

  fun pinHomeScreenShortcut(
    packageName: String,
    activityClassName: String?,
    customLabel: String,
    accentColorArgb: Long,
    iconKey: String,
    shape: ShortcutShape = ShortcutShape.ROUNDED,
    customIconPath: String? = null,
    iconZoom: Float = 1.0f,
  ): Result<Boolean> {
    return runCatching {
      if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        return@runCatching false
      }

      val targetIntent = if (!activityClassName.isNullOrBlank()) {
        Intent(Intent.ACTION_VIEW).apply {
          component = ComponentName(packageName, activityClassName)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      } else {
        context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: error("Cannot create shortcut: package has no launchable entry")
      }

      val iconBitmap = createShortcutIconBitmap(
        packageName = packageName,
        accentColorArgb = accentColorArgb,
        iconKey = iconKey,
        shape = shape,
        customIconPath = customIconPath,
        zoom = iconZoom,
      )
      val shortcutId = "nexus_${packageName}_${activityClassName ?: "main"}_${customLabel.hashCode()}_${System.currentTimeMillis()}"
      val info = ShortcutInfoCompat.Builder(context, shortcutId)
        .setShortLabel(customLabel.take(24).ifBlank { packageName })
        .setLongLabel(customLabel.ifBlank { packageName })
        .setIcon(IconCompat.createWithBitmap(iconBitmap))
        .setIntent(targetIntent)
        .build()

      ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }
  }

  fun createShortcutIconBitmap(
    packageName: String,
    accentColorArgb: Long,
    iconKey: String,
    shape: ShortcutShape,
    customIconPath: String?,
    zoom: Float,
  ): Bitmap {
    val size = 192
    val rawContent: Bitmap? = when {
      !customIconPath.isNullOrBlank() && File(customIconPath).exists() ->
        runCatching { BitmapFactory.decodeFile(customIconPath) }.getOrNull()
      iconKey == "app_icon" ->
        runCatching { drawableToBitmap(context.packageManager.getApplicationIcon(packageName)) }.getOrNull()
      else -> null
    }

    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val rect = RectF(4f, 4f, size - 4f, size - 4f)
    val corner = when (shape) {
      ShortcutShape.CIRCLE -> size / 2f
      ShortcutShape.SQUIRCLE -> 54f
      ShortcutShape.ROUNDED -> 34f
      ShortcutShape.SQUARE -> 8f
    }

    val maskPath = Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) }

    if (rawContent != null) {
      val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
      val lc = Canvas(layer)
      val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
      lc.drawPath(maskPath, shapePaint)

      val z = zoom.coerceIn(0.6f, 2.0f)
      val scaledW = size * z
      val scaledH = size * z
      val dx = (size - scaledW) / 2f
      val dy = (size - scaledH) / 2f
      val srcPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
      }
      lc.drawBitmap(rawContent, null, RectF(dx, dy, dx + scaledW, dy + scaledH), srcPaint)
      canvas.drawBitmap(layer, 0f, 0f, null)
      layer.recycle()
      if (rawContent !== bmp) rawContent.recycle()
    } else {
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0B0E.toInt() }
      val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColorArgb.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
      }
      canvas.drawPath(maskPath, bgPaint)
      canvas.drawPath(maskPath, borderPaint)

      val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColorArgb.toInt()
        textSize = 68f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
      }
      val badge = when (iconKey) {
        "bolt" -> "⚡"
        "terminal" -> ">"
        "settings" -> "⚙"
        "star" -> "★"
        else -> packageName.substringAfterLast('.').take(2).uppercase()
      }
      val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
      canvas.drawText(badge, canvas.width / 2f, yPos, textPaint)
    }
    return bmp
  }

  private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
  }
}
