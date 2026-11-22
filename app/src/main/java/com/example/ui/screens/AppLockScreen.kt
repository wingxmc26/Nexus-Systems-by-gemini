package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NexusLockType
import com.example.security.AppLockMonitorCoordinator
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.PatternLockGrid
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.AppLockViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppLockScreen(
  viewModel: AppLockViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val secConfig by viewModel.securityConfig.collectAsStateWithLifecycle()
  val profiles by viewModel.profiles.collectAsStateWithLifecycle()
  val apps by viewModel.installedApps.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

  val activeProfile = profiles.firstOrNull { it.isActive } ?: profiles.firstOrNull()
  var usageGranted by remember { mutableStateOf(viewModel.permissionManager.isUsageStatsGranted()) }
  var accessibilityEnabled by remember { mutableStateOf(viewModel.permissionManager.isAccessibilityServiceEnabled()) }

  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        usageGranted = viewModel.permissionManager.isUsageStatsGranted()
        accessibilityEnabled = viewModel.permissionManager.isAccessibilityServiceEnabled()
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }

  var showCredentialDialog by remember { mutableStateOf(false) }
  var appSearch by remember { mutableStateOf("") }

  val protectedPkgs = remember(activeProfile) {
    activeProfile?.let { AppLockMonitorCoordinator.parseStringList(it.protectedPackagesJson) }.orEmpty()
  }

  val shownApps = remember(apps, appSearch, protectedPkgs) {
    val filtered = apps.filter {
      appSearch.isBlank() ||
        it.appName.contains(appSearch, ignoreCase = true) ||
        it.packageName.contains(appSearch, ignoreCase = true)
    }
    filtered.sortedByDescending { it.packageName in protectedPkgs }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "App Lock",
      subtitle = "Background Guard, Pattern / PIN / Password & Installed Apps",
      onBack = onBack,
      trailingContent = {
        IconButton(
          onClick = viewModel::lockAllNow,
          modifier = Modifier.testTag("applock_lock_now_button"),
        ) {
          Icon(Icons.Default.Lock, contentDescription = "Lock Apps Now", tint = colors.accent)
        }
      },
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("app_lock_list"),
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 24.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!statusMessage.isNullOrBlank()) {
        item {
          NexusGlassCard(accentHighlight = true) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = statusMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = viewModel::clearStatus) { Text("OK") }
            }
          }
        }
      }

      // 1. Background Guard & Re-lock Timing
      item {
        NexusGlassCard(
          accentHighlight = secConfig.appLockEnabled,
          testTag = "applock_master_card",
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Default.Shield, contentDescription = null, tint = colors.accent)
              Spacer(Modifier.width(8.dp))
              Column {
                Text("App Lock Master", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                  text = if (secConfig.appLockEnabled) {
                    "${protectedPkgs.size} app(s) protected"
                  } else {
                    "Enable protection for selected installed apps"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.textSecondary,
                )
              }
            }
            Switch(
              checked = secConfig.appLockEnabled,
              onCheckedChange = viewModel::setAppLockEnabled,
              modifier = Modifier.testTag("applock_enable_switch"),
            )
          }

          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Persistent Background Guard", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
              Text(
                "Detects foreground app launches even when NEXUS is closed or in background",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
              )
            }
            Switch(
              checked = secConfig.externalMonitorRunning,
              onCheckedChange = viewModel::setExternalMonitorEnabled,
              modifier = Modifier.testTag("applock_external_monitor_switch"),
            )
          }

          Spacer(Modifier.height(8.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            StatusPill(
              text = if (usageGranted) "Usage Access OK" else "Usage Missing",
              color = if (usageGranted) colors.nominal else colors.warning,
            )
            StatusPill(
              text = if (accessibilityEnabled) "Accessibility OK" else "Accessibility Optional",
              color = if (accessibilityEnabled) colors.nominal else colors.textSecondary,
            )
          }

          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
              onClick = { runCatching { context.startActivity(viewModel.permissionManager.buildUsageStatsIntent()) } },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(if (usageGranted) "Usage ✓" else "Usage")
            }
            OutlinedButton(
              onClick = { runCatching { context.startActivity(viewModel.permissionManager.buildAccessibilitySettingsIntent()) } },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(if (accessibilityEnabled) "Access ✓" else "Access")
            }
            OutlinedButton(
              onClick = { runCatching { context.startActivity(viewModel.permissionManager.buildBatteryOptimizationSettingsIntent()) } },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Battery")
            }
          }

          if (activeProfile != null) {
            Spacer(Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Re-lock on Screen Off", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
              Switch(
                checked = activeProfile.lockOnScreenOff,
                onCheckedChange = { v -> viewModel.updateProfileTiming(activeProfile, activeProfile.autoLockDelayMs, v) },
              )
            }
            Text("Re-lock Delay", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf(0L to "Immediate", 30_000L to "30s", 120_000L to "2m", 600_000L to "10m").forEach { (ms, lbl) ->
                FilterChip(
                  selected = activeProfile.autoLockDelayMs == ms,
                  onClick = { viewModel.updateProfileTiming(activeProfile, ms, activeProfile.lockOnScreenOff) },
                  label = { Text(lbl) },
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }

      // 2. Lock Method & Credential Card
      item {
        NexusGlassCard(testTag = "applock_credential_card") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Lock Type & Credential", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Text(
                if (secConfig.hasPinConfigured) {
                  "Active mode: ${secConfig.activeLockType.label} (PBKDF2-SHA256)"
                } else {
                  "No lock credential configured yet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
            }
            StatusPill(
              text = if (secConfig.hasPinConfigured) secConfig.activeLockType.label.uppercase() else "UNSET",
              color = if (secConfig.hasPinConfigured) colors.nominal else colors.warning,
            )
          }

          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Biometric Unlock", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            Switch(
              checked = secConfig.biometricEnabled,
              onCheckedChange = viewModel::setBiometricEnabled,
            )
          }

          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { showCredentialDialog = true },
              modifier = Modifier
                .weight(1f)
                .testTag("applock_configure_pin_button"),
            ) {
              Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (secConfig.hasPinConfigured) "Change Lock" else "Set Lock")
            }
            OutlinedButton(
              onClick = viewModel::lockAllNow,
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Lock All")
            }
          }
        }
      }

      // 3. Searchable Installed Apps List
      item {
        SectionHeader(title = "Installed Apps", trailingText = "${protectedPkgs.size} locked")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
          value = appSearch,
          onValueChange = { appSearch = it },
          label = { Text("Search installed apps") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("applock_app_search_input"),
        )
      }

      items(shownApps, key = { it.packageName }) { app ->
        val checked = app.packageName in protectedPkgs
        NexusGlassCard(
          accentHighlight = checked,
          onClick = { activeProfile?.let { viewModel.toggleExternalPackage(it, app.packageName) } },
          testTag = "applock_app_${app.packageName}",
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(app.appName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Checkbox(
              checked = checked,
              onCheckedChange = { activeProfile?.let { viewModel.toggleExternalPackage(it, app.packageName) } },
            )
          }
        }
      }
    }
  }

  if (showCredentialDialog) {
    var selectedType by remember { mutableStateOf(secConfig.activeLockType) }
    var secret1 by remember { mutableStateOf("") }
    var secret2 by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var patternNodes1 by remember { mutableStateOf<List<Int>>(emptyList()) }
    var patternNodes2 by remember { mutableStateOf<List<Int>>(emptyList()) }
    var patternConfirmStep by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showCredentialDialog = false },
      title = { Text("Configure Lock Method") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NexusLockType.entries.forEach { lt ->
              FilterChip(
                selected = selectedType == lt,
                onClick = {
                  selectedType = lt
                  secret1 = ""
                  secret2 = ""
                  patternNodes1 = emptyList()
                  patternNodes2 = emptyList()
                  patternConfirmStep = false
                  dialogError = null
                },
                label = { Text(lt.label) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("applock_type_${lt.name.lowercase()}"),
              )
            }
          }

          when (selectedType) {
            NexusLockType.PIN -> {
              OutlinedTextField(
                value = secret1,
                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) secret1 = it },
                label = { Text("New 4–8 digit PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("applock_pin1_input"),
              )
              OutlinedTextField(
                value = secret2,
                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) secret2 = it },
                label = { Text("Confirm PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("applock_pin2_input"),
              )
            }

            NexusLockType.PASSWORD -> {
              OutlinedTextField(
                value = secret1,
                onValueChange = { if (it.length <= 64) secret1 = it },
                label = { Text("Password (4+ chars, letters/symbols)") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                  IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                      if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                      contentDescription = "Toggle visibility",
                    )
                  }
                },
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("applock_password1_input"),
              )
              OutlinedTextField(
                value = secret2,
                onValueChange = { if (it.length <= 64) secret2 = it },
                label = { Text("Confirm Password") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("applock_password2_input"),
              )
            }

            NexusLockType.PATTERN -> {
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                  text = if (!patternConfirmStep) "Draw unlock pattern (connect 4+ dots)" else "Draw pattern again to confirm",
                  style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                PatternLockGrid(
                  selectedNodes = if (!patternConfirmStep) patternNodes1 else patternNodes2,
                  onNodesChanged = { nodes ->
                    if (!patternConfirmStep) patternNodes1 = nodes else patternNodes2 = nodes
                  },
                  onPatternCompleted = { nodes ->
                    if (!patternConfirmStep) {
                      if (nodes.size < 4) {
                        dialogError = "Connect at least 4 dots."
                        patternNodes1 = emptyList()
                      } else {
                        dialogError = null
                        patternNodes1 = nodes
                        patternConfirmStep = true
                      }
                    } else {
                      if (nodes != patternNodes1) {
                        dialogError = "Patterns do not match. Draw again."
                        patternNodes2 = emptyList()
                      } else {
                        dialogError = null
                        patternNodes2 = nodes
                      }
                    }
                  },
                  testTag = "applock_config_pattern_grid",
                )
                TextButton(
                  onClick = {
                    patternNodes1 = emptyList()
                    patternNodes2 = emptyList()
                    patternConfirmStep = false
                    dialogError = null
                  },
                ) { Text("Reset Pattern") }
              }
            }
          }

          if (!dialogError.isNullOrBlank()) {
            Text(dialogError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.critical)
          }
        }
      },
      confirmButton = {
        val canSave = when (selectedType) {
          NexusLockType.PIN -> secret1.length in 4..8 && secret1 == secret2
          NexusLockType.PASSWORD -> secret1.length >= 4 && secret1.isNotBlank() && secret1 == secret2
          NexusLockType.PATTERN -> patternNodes1.size >= 4 && patternNodes2 == patternNodes1
        }
        Button(
          onClick = {
            val payload = when (selectedType) {
              NexusLockType.PATTERN -> patternNodes1.joinToString("-")
              else -> secret1
            }
            viewModel.configureCredential(selectedType, payload)
            showCredentialDialog = false
          },
          enabled = canSave,
          modifier = Modifier.testTag("applock_save_pin_button"),
        ) { Text("Save ${selectedType.label}") }
      },
      dismissButton = {
        TextButton(onClick = { showCredentialDialog = false }) { Text("Cancel") }
      },
    )
  }
}
