package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.data.local.VaultItemEntity
import com.example.security.VaultFramedMediaDataSource
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SecurityLockGatePane
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.SecureVaultViewModel
import com.example.ui.viewmodel.VaultPreviewState
import com.example.util.Formatters
import java.io.File

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecureVaultScreen(
  viewModel: SecureVaultViewModel,
  onBack: () -> Unit,
  contentPadding: PaddingValues,
) {
  val colors = NexusThemeExtras.colors
  val secConfig by viewModel.securityConfig.collectAsStateWithLifecycle()
  val unlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
  val folders by viewModel.folders.collectAsStateWithLifecycle()
  val items by viewModel.items.collectAsStateWithLifecycle()
  val previewState by viewModel.previewState.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
  val manifestVerified by viewModel.manifestVerified.collectAsStateWithLifecycle()

  var pinError by remember { mutableStateOf<String?>(null) }

  if (!unlocked) {
    SecurityLockGatePane(
      title = "Secure Vault",
      subtitle = "Hardware-backed AES-256-GCM encrypted file container",
      hasPinConfigured = secConfig.hasPinConfigured,
      activeLockType = secConfig.activeLockType,
      biometricEnabled = secConfig.biometricEnabled,
      failedAttempts = secConfig.failedAttempts,
      lockoutUntilMs = secConfig.lockoutUntilMs,
      externalErrorMessage = pinError,
      onSetupPin = { pin ->
        viewModel.setupPin(pin) { ok, msg -> if (!ok) pinError = msg else pinError = null }
      },
      onSetupCredential = { type, secret ->
        viewModel.setupCredential(type, secret) { ok, msg -> pinError = if (ok) null else msg }
      },
      onSubmitPin = { pin ->
        viewModel.unlockWithPin(pin) { ok, msg -> pinError = if (ok) null else msg }
      },
      onRequestBiometric = { activity ->
        viewModel.triggerBiometricUnlock(activity) { err -> pinError = err }
      },
      onBack = onBack,
      testTagPrefix = "vault_gate",
    )
    return
  }

  var activeFolderId by remember { mutableLongStateOf(-1L) } // -1 = all folders
  var activeCategory by remember { mutableStateOf("ALL") }
  var pendingExportItem by remember { mutableStateOf<VaultItemEntity?>(null) }
  var showCreateFolderDialog by remember { mutableStateOf(false) }
  var renameTargetItem by remember { mutableStateOf<VaultItemEntity?>(null) }
  var moveTargetItem by remember { mutableStateOf<VaultItemEntity?>(null) }
  var backupDialogMode by remember { mutableStateOf<String?>(null) } // "EXPORT" | "RESTORE"
  var backupPassphrase by remember { mutableStateOf("") }

  val targetImportFolder = if (activeFolderId > 0L) activeFolderId else 0L

  val visualMediaPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(20),
  ) { uris ->
    viewModel.markUserActivity()
    if (uris.isNotEmpty()) viewModel.importUris(uris, targetImportFolder)
  }

  val documentPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    viewModel.markUserActivity()
    if (uris.isNotEmpty()) viewModel.importUris(uris, targetImportFolder)
  }

  val exportFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("*/*"),
  ) { uri ->
    val target = pendingExportItem
    pendingExportItem = null
    if (uri != null && target != null) {
      viewModel.exportItem(target, uri)
    }
  }

  val exportBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
  ) { uri ->
    val pass = backupPassphrase
    backupPassphrase = ""
    if (uri != null && pass.length >= 6) {
      viewModel.exportBackup(pass, uri)
    }
  }

  val restoreBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    val pass = backupPassphrase
    backupPassphrase = ""
    if (uri != null && pass.length >= 6) {
      viewModel.restoreBackup(pass, uri)
    }
  }

  val filteredItems = remember(items, activeFolderId, activeCategory) {
    items.filter { item ->
      (activeFolderId < 0L || item.folderId == activeFolderId) &&
        (activeCategory == "ALL" || item.category == activeCategory)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Secure Vault",
      subtitle = "AES-256-GCM Framed Encryption • Zero Temp Files",
      onBack = onBack,
      trailingContent = {
        IconButton(
          onClick = viewModel::lockVault,
          modifier = Modifier.testTag("lock_vault_now_button"),
        ) {
          Icon(Icons.Default.Lock, contentDescription = "Lock Vault Now", tint = colors.accent)
        }
      },
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("secure_vault_list"),
      contentPadding = PaddingValues(16.dp),
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
              Text(statusMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
              TextButton(onClick = viewModel::clearStatus) { Text("OK") }
            }
          }
        }
      }

      item {
        NexusGlassCard(testTag = "vault_actions_card") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("${items.size} Encrypted Files", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Text(
                "Imports create AES-256-GCM copies inside NEXUS. Original external files are untouched.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
            }
            StatusPill(
              text = if (manifestVerified) "MANIFEST OK" else "NEW VAULT",
              color = if (manifestVerified) colors.nominal else colors.textSecondary,
            )
          }

          Spacer(Modifier.height(10.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                visualMediaPicker.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
              },
              modifier = Modifier.weight(1f).testTag("vault_import_media_button"),
            ) {
              Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Photo / Video")
            }
            OutlinedButton(
              onClick = { documentPicker.launch(arrayOf("*/*")) },
              modifier = Modifier.weight(1f).testTag("vault_import_docs_button"),
            ) {
              Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Docs / Audio")
            }
          }

          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = { backupDialogMode = "EXPORT" },
              modifier = Modifier.weight(1f).testTag("vault_backup_export_button"),
            ) {
              Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Encrypted Backup")
            }
            OutlinedButton(
              onClick = { backupDialogMode = "RESTORE" },
              modifier = Modifier.weight(1f).testTag("vault_backup_restore_button"),
            ) {
              Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Restore Archive")
            }
          }

          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Lock Vault on Screen Off", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            Switch(
              checked = secConfig.vaultLockOnScreenOff,
              onCheckedChange = { v -> viewModel.updateAutoLockPolicy(secConfig.vaultAutoLockDelayMs, v) },
            )
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15_000L to "15s", 60_000L to "1m", 300_000L to "5m", 0L to "Manual").forEach { (ms, lbl) ->
              FilterChip(
                selected = secConfig.vaultAutoLockDelayMs == ms,
                onClick = { viewModel.updateAutoLockPolicy(ms, secConfig.vaultLockOnScreenOff) },
                label = { Text(lbl) },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Folders & Filters", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
          TextButton(onClick = { showCreateFolderDialog = true }) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("New Folder")
          }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          FilterChip(selected = activeFolderId == -1L, onClick = { activeFolderId = -1L }, label = { Text("All Folders") })
          FilterChip(selected = activeFolderId == 0L, onClick = { activeFolderId = 0L }, label = { Text("Root") })
          folders.forEach { folder ->
            FilterChip(
              selected = activeFolderId == folder.id,
              onClick = { activeFolderId = folder.id },
              label = { Text(folder.name) },
            )
          }
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("ALL", "PHOTO", "VIDEO", "AUDIO", "DOCUMENT").forEach { cat ->
            FilterChip(selected = activeCategory == cat, onClick = { activeCategory = cat }, label = { Text(cat) })
          }
        }
      }

      items(filteredItems, key = { it.id }) { item ->
        val icon = when (item.category) {
          "PHOTO" -> Icons.Default.Image
          "VIDEO" -> Icons.Default.VideoFile
          "AUDIO" -> Icons.Default.AudioFile
          else -> Icons.Default.Description
        }
        NexusGlassCard(
          onClick = { viewModel.openPreview(item) },
          testTag = "vault_item_card_${item.id}",
        ) {
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(item.displayName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Text(
                "${item.category} • ${Formatters.formatBytes(item.originalSizeBytes)} • SHA-256 ${item.sha256Hex.take(10)}...",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
            }
          }
          Spacer(Modifier.height(6.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { viewModel.verifyIntegrity(item) }) {
              Icon(Icons.Default.VerifiedUser, contentDescription = "Verify Integrity", tint = colors.nominal)
            }
            IconButton(onClick = { renameTargetItem = item }) {
              Icon(Icons.Default.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = { moveTargetItem = item }) {
              Icon(Icons.Default.DriveFileMove, contentDescription = "Move Folder")
            }
            IconButton(onClick = {
              pendingExportItem = item
              exportFileLauncher.launch(item.displayName)
            }) {
              Icon(Icons.Default.FileDownload, contentDescription = "Export Decrypted Copy", tint = colors.accent)
            }
            IconButton(onClick = { viewModel.deleteItem(item) }) {
              Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.critical)
            }
          }
        }
      }
    }
  }

  // RAM Preview Dialog (Image, Text, or Media3 Streaming from NXSV1 encrypted file)
  if (previewState !is VaultPreviewState.None) {
    VaultPreviewDialog(
      state = previewState,
      cryptoEngine = viewModel.vaultRepo.cryptoEngine,
      onDismiss = viewModel::closePreview,
    )
  }

  if (showCreateFolderDialog) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreateFolderDialog = false },
      title = { Text("Create Vault Folder") },
      text = {
        OutlinedTextField(
          value = folderName,
          onValueChange = { folderName = it },
          label = { Text("Folder Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(onClick = {
          if (folderName.isNotBlank()) {
            viewModel.createFolder(folderName)
            showCreateFolderDialog = false
          }
        }) { Text("Create") }
      },
      dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } },
    )
  }

  renameTargetItem?.let { target ->
    var nextName by remember(target.id) { mutableStateOf(target.displayName) }
    AlertDialog(
      onDismissRequest = { renameTargetItem = null },
      title = { Text("Rename Vault Item") },
      text = {
        OutlinedTextField(
          value = nextName,
          onValueChange = { nextName = it },
          label = { Text("Display Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(onClick = {
          viewModel.renameItem(target.id, nextName)
          renameTargetItem = null
        }) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { renameTargetItem = null }) { Text("Cancel") } },
    )
  }

  moveTargetItem?.let { target ->
    AlertDialog(
      onDismissRequest = { moveTargetItem = null },
      title = { Text("Move to Folder") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = { viewModel.moveItem(target.id, 0L); moveTargetItem = null },
            modifier = Modifier.fillMaxWidth(),
          ) { Text("Root Vault") }
          folders.forEach { folder ->
            OutlinedButton(
              onClick = { viewModel.moveItem(target.id, folder.id); moveTargetItem = null },
              modifier = Modifier.fillMaxWidth(),
            ) { Text(folder.name) }
          }
        }
      },
      confirmButton = { TextButton(onClick = { moveTargetItem = null }) { Text("Close") } },
    )
  }

  backupDialogMode?.let { mode ->
    AlertDialog(
      onDismissRequest = { backupDialogMode = null },
      title = { Text(if (mode == "EXPORT") "Export Encrypted Backup" else "Restore Encrypted Backup") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Enter a backup passphrase (min 6 characters). Archives use PBKDF2-SHA256 + AES-256-GCM.",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = backupPassphrase,
            onValueChange = { backupPassphrase = it },
            label = { Text("Passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            backupDialogMode = null
            if (mode == "EXPORT") {
              exportBackupLauncher.launch("nexus_vault_backup_${System.currentTimeMillis()}.nxsbackup")
            } else {
              restoreBackupLauncher.launch(arrayOf("*/*"))
            }
          },
          enabled = backupPassphrase.length >= 6,
        ) {
          Text(if (mode == "EXPORT") "Choose Destination" else "Pick Backup File")
        }
      },
      dismissButton = {
        TextButton(onClick = { backupDialogMode = null; backupPassphrase = "" }) { Text("Cancel") }
      },
    )
  }
}

@OptIn(UnstableApi::class)
@Composable
private fun VaultPreviewDialog(
  state: VaultPreviewState,
  cryptoEngine: com.example.security.VaultCryptoEngine,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val colors = NexusThemeExtras.colors

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val label = when (state) {
          is VaultPreviewState.Loading -> state.item.displayName
          is VaultPreviewState.ImagePreview -> state.item.displayName
          is VaultPreviewState.TextPreview -> state.item.displayName
          is VaultPreviewState.MediaStreamPreview -> state.item.displayName
          is VaultPreviewState.Error -> state.item.displayName
          VaultPreviewState.None -> "Preview"
        }
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close Preview") }
      }
    },
    text = {
      when (state) {
        is VaultPreviewState.Loading -> {
          Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.accent)
          }
        }
        is VaultPreviewState.ImagePreview -> {
          Image(
            bitmap = state.bitmap.asImageBitmap(),
            contentDescription = state.item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 380.dp)
              .clip(RoundedCornerShape(12.dp)),
          )
        }
        is VaultPreviewState.TextPreview -> {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 360.dp)
              .verticalScroll(rememberScrollState()),
          ) {
            Text(state.text, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
          }
        }
        is VaultPreviewState.MediaStreamPreview -> {
          val encFile: File = state.encryptedFile
          val player = remember(encFile.absolutePath) {
            val factory = VaultFramedMediaDataSource.Factory(encFile, cryptoEngine)
            val mediaSource = ProgressiveMediaSource.Factory(factory)
              .createMediaSource(MediaItem.fromUri(Uri.fromFile(encFile)))
            ExoPlayer.Builder(context).build().apply {
              setMediaSource(mediaSource)
              prepare()
              playWhenReady = true
            }
          }
          DisposableEffect(player) {
            onDispose { player.release() }
          }
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Streaming directly from AES-256-GCM frames in RAM", style = MaterialTheme.typography.labelSmall, color = colors.nominal)
            AndroidView(
              factory = { ctx ->
                PlayerView(ctx).apply {
                  this.player = player
                  useController = true
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
            )
          }
        }
        is VaultPreviewState.Error -> {
          Text(state.message, color = colors.critical)
        }
        VaultPreviewState.None -> Unit
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}
