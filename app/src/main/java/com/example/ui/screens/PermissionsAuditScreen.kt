package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.di.NexusContainer
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras

@Composable
fun PermissionsAuditScreen(
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val pm = remember(context) { NexusContainer.get(context).permissionManager }
  var entries by remember { mutableStateOf(pm.loadAuditSnapshot()) }
  val lifecycleOwner = LocalLifecycleOwner.current

  val runtimeLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    entries = pm.loadAuditSnapshot()
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        entries = pm.loadAuditSnapshot()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val grantedCount = entries.count { it.granted }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Permissions Audit",
      subtitle = "$grantedCount of ${entries.size} optional suite permissions granted",
      onBack = onBack,
      trailingContent = {
        IconButton(
          onClick = { entries = pm.loadAuditSnapshot() },
          modifier = Modifier.size(48.dp),
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh permissions", tint = colors.textPrimary)
        }
      },
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 4.dp,
        bottom = contentPadding.calculateBottomPadding() + 24.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        NexusGlassCard(accentHighlight = true) {
          SectionHeader(title = "Privacy & Least-Privilege Policy", icon = Icons.Default.VerifiedUser)
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Nexus System works completely offline with zero tracking or internet access. Sensitive permissions are strictly optional and requested only when you use their specific feature.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
        }
      }

      items(entries, key = { it.id }) { entry ->
        NexusGlassCard(testTag = "perm_card_${entry.id}") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = null,
              tint = if (entry.granted) colors.nominal else colors.accent,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(entry.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Text(entry.moduleName, style = MaterialTheme.typography.labelSmall, color = colors.accentSecondary)
            }
            StatusPill(
              text = if (entry.granted) "Granted" else "Not Granted",
              color = if (entry.granted) colors.nominal else colors.warning,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(entry.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)

          if (!entry.granted) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = {
                when (entry.id) {
                  "notification_access" -> runCatching { context.startActivity(pm.buildNotificationAccessIntent()) }
                  "write_settings" -> runCatching { context.startActivity(pm.buildWriteSettingsIntent()) }
                  "usage_stats" -> runCatching { context.startActivity(pm.buildUsageStatsIntent()) }
                  "overlay" -> runCatching { context.startActivity(pm.buildOverlayIntent()) }
                  "accessibility" -> runCatching { context.startActivity(pm.buildAccessibilitySettingsIntent()) }
                  "audio_media" -> runtimeLauncher.launch(pm.audioMediaPermissionName())
                  "video_media" -> runtimeLauncher.launch(pm.videoMediaPermissionName())
                  "record_audio" -> runtimeLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  "post_notifications" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runtimeLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Grant / Configure")
            }
          }
        }
      }
    }
  }
}
