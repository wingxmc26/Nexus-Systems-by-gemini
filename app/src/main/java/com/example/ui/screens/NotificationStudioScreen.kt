package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NotificationHistoryEntity
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.NotificationFilter
import com.example.ui.viewmodel.NotificationStudioViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationStudioScreen(
  viewModel: NotificationStudioViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var selectedItem by remember { mutableStateOf<NotificationHistoryEntity?>(null) }
  var showClearConfirm by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAccess()
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      containerColor = colors.cardSurface,
      title = { Text("Clear Notification History?", color = colors.textPrimary) },
      text = { Text("Removes all saved local notification logs from the on-device database.", color = colors.textSecondary) },
      confirmButton = {
        Button(
          onClick = {
            viewModel.clearAllHistory()
            showClearConfirm = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = colors.critical),
        ) { Text("Clear All") }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
      },
    )
  }

  selectedItem?.let { n ->
    AlertDialog(
      onDismissRequest = { selectedItem = null },
      containerColor = colors.cardSurface,
      title = { Text(n.appName, color = colors.textPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(n.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
          if (n.text.isNotBlank()) Text(n.text, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
          if (n.subText.isNotBlank()) Text(n.subText, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
          Text("Package: ${n.packageName}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
          Text("Received: ${SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(n.postedAtMs))}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
      },
      confirmButton = {
        if (n.isClearable) {
          TextButton(onClick = {
            viewModel.dismissActive(n)
            selectedItem = null
          }) { Text("Dismiss Active", color = colors.accent) }
        }
      },
      dismissButton = {
        TextButton(onClick = { selectedItem = null }) { Text("Close") }
      },
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Notification Studio",
      subtitle = "Local History, Filters & Pinned Alerts",
      onBack = onBack,
      trailingContent = {
        IconButton(onClick = { showClearConfirm = true }, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.ClearAll, contentDescription = "Clear history", tint = colors.textSecondary)
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
        NexusGlassCard(accentHighlight = !state.hasNotificationAccess, testTag = "notif_access_card") {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Notification Listener", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            StatusPill(
              text = when {
                state.isServiceConnected -> "ACTIVE"
                state.hasNotificationAccess -> "ENABLED"
                else -> "DISABLED"
              },
              color = if (state.hasNotificationAccess) colors.nominal else colors.warning,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = if (state.hasNotificationAccess) {
              "Recording new notifications locally on this device while enabled."
            } else {
              "Grant Notification Access in Android Settings to capture incoming notifications locally. Android never exposes past notifications retroactively."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
          if (!state.hasNotificationAccess) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = { runCatching { context.startActivity(viewModel.buildNotificationAccessIntent()) } },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier.fillMaxWidth().testTag("grant_notif_access_button"),
            ) {
              Text("Open Notification Access Settings")
            }
          }
        }
      }

      state.statusMessage?.let { msg ->
        item {
          NexusGlassCard(accentHighlight = true) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
              Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
              TextButton(onClick = { viewModel.clearStatus() }) { Text("OK", color = colors.accent) }
            }
          }
        }
      }

      item {
        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = viewModel::setSearchQuery,
          placeholder = { Text("Search notifications or apps...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          NotificationFilter.entries.forEach { f ->
            FilterChip(
              selected = state.filter == f,
              onClick = { viewModel.setFilter(f) },
              label = { Text(f.label) },
            )
          }
        }
        if (state.appCounts.isNotEmpty()) {
          Spacer(modifier = Modifier.height(6.dp))
          Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.appCounts.forEach { (app, count) ->
              FilterChip(
                selected = state.selectedAppFilter == app,
                onClick = { viewModel.selectAppFilter(app) },
                label = { Text("$app ($count)") },
              )
            }
          }
        }
      }

      if (state.items.isEmpty()) {
        item {
          EmptyOrErrorState(
            icon = if (state.hasNotificationAccess) Icons.Default.Notifications else Icons.Default.NotificationsOff,
            title = "No Logged Notifications",
            description = if (state.hasNotificationAccess) "Incoming notifications from other apps will appear here automatically." else "Enable Notification Access above to begin recording.",
          )
        }
      } else {
        item {
          SectionHeader(title = "Saved History", trailingText = "${state.items.size} entries")
        }
        items(state.items, key = { it.id }) { entry ->
          NexusGlassCard(onClick = { selectedItem = entry }, accentHighlight = entry.isPinned || entry.isImportant) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(entry.appName, style = MaterialTheme.typography.labelMedium, color = colors.accentSecondary)
                  Text(SimpleDateFormat("HH:mm", Locale.US).format(Date(entry.postedAtMs)), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                  if (entry.isOngoing) StatusPill(text = "ONGOING", color = colors.info)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(entry.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.text.isNotBlank()) {
                  Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
              }
              IconButton(onClick = { viewModel.togglePinned(entry) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = if (entry.isPinned) colors.accent else colors.textMuted, modifier = Modifier.size(18.dp))
              }
              IconButton(onClick = { viewModel.toggleImportant(entry) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.PriorityHigh, contentDescription = "Important", tint = if (entry.isImportant) colors.warning else colors.textMuted, modifier = Modifier.size(18.dp))
              }
              if (entry.isClearable) {
                IconButton(onClick = { viewModel.dismissActive(entry) }, modifier = Modifier.size(40.dp)) {
                  Icon(Icons.Default.Close, contentDescription = "Dismiss active", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
              }
              IconButton(onClick = { viewModel.deleteEntry(entry) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete entry", tint = colors.critical, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }
    }
  }
}
