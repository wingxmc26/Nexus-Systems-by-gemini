package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppLockProfileEntity
import com.example.data.local.AuthHistoryEntity
import com.example.data.local.NexusLockType
import com.example.data.local.SecuritySettingsConfig
import com.example.data.local.VaultFolderEntity
import com.example.data.local.VaultItemEntity
import com.example.data.model.ManagedAppSummary
import com.example.di.NexusContainer
import com.example.security.AppLockMonitorCoordinator
import com.example.security.NexusAppLockMonitorService
import com.example.security.NexusSecurityManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VaultPreviewState {
  data object None : VaultPreviewState
  data class Loading(val item: VaultItemEntity) : VaultPreviewState
  data class ImagePreview(val item: VaultItemEntity, val bitmap: Bitmap) : VaultPreviewState
  data class TextPreview(val item: VaultItemEntity, val text: String) : VaultPreviewState
  data class MediaStreamPreview(val item: VaultItemEntity, val encryptedFile: File) : VaultPreviewState
  data class Error(val item: VaultItemEntity, val message: String) : VaultPreviewState
}

class SecureVaultViewModel(app: Application) : AndroidViewModel(app) {
  private val container = NexusContainer.get(app)
  val vaultRepo = container.vaultRepository
  val permissionManager = container.permissionManager
  val prefs = container.advancedPreferences
  val appLockDao = container.database.appLockDao()

  val securityConfig: StateFlow<SecuritySettingsConfig> = prefs.securityConfig
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecuritySettingsConfig())

  val isUnlocked: StateFlow<Boolean> = NexusSecurityManager.vaultUnlocked

  val folders: StateFlow<List<VaultFolderEntity>> = vaultRepo.folders
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val items: StateFlow<List<VaultItemEntity>> = vaultRepo.items
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _previewState = MutableStateFlow<VaultPreviewState>(VaultPreviewState.None)
  val previewState: StateFlow<VaultPreviewState> = _previewState.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  private val _manifestVerified = MutableStateFlow(false)
  val manifestVerified: StateFlow<Boolean> = _manifestVerified.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      NexusSecurityManager.checkVaultAutoLock(prefs.getSecuritySnapshot())
      _manifestVerified.value = vaultRepo.readEncryptedManifestStatus()
    }
  }

  fun clearStatus() {
    _statusMessage.value = null
  }

  fun markUserActivity() {
    NexusSecurityManager.markVaultActive()
  }

  fun setupCredential(type: NexusLockType, secret: String, onDone: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val res = NexusSecurityManager.configureCredential(type, secret, prefs, appLockDao)
      if (res.isSuccess) {
        NexusSecurityManager.verifyCredential(
          secret = secret,
          targetType = "VAULT",
          targetName = "Secure Vault",
          prefs = prefs,
          appLockDao = appLockDao,
          unlockVaultOnSuccess = true,
        )
        withContext(Dispatchers.Main) { onDone(true, "${type.label} created & Vault unlocked") }
      } else {
        withContext(Dispatchers.Main) { onDone(false, res.exceptionOrNull()?.message ?: "Invalid credential") }
      }
    }
  }

  fun setupPin(pin: String, onDone: (Boolean, String) -> Unit) {
    setupCredential(NexusLockType.PIN, pin, onDone)
  }

  fun unlockWithPin(pin: String, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val res = NexusSecurityManager.verifyCredential(
        secret = pin,
        targetType = "VAULT",
        targetName = "Secure Vault",
        prefs = prefs,
        appLockDao = appLockDao,
        unlockVaultOnSuccess = true,
      )
      withContext(Dispatchers.Main) { onResult(res.success, res.message) }
    }
  }

  fun triggerBiometricUnlock(activity: FragmentActivity, onError: (String) -> Unit) {
    NexusSecurityManager.showBiometricPrompt(
      activity = activity,
      title = "Unlock Secure Vault",
      subtitle = "Authenticate with biometrics to decrypt vault items",
      onSuccess = {
        viewModelScope.launch(Dispatchers.IO) {
          NexusSecurityManager.recordBiometricOutcome(
            success = true,
            targetType = "VAULT",
            targetName = "Secure Vault",
            reason = "",
            appLockDao = appLockDao,
            unlockVaultOnSuccess = true,
          )
        }
      },
      onFailure = { msg ->
        viewModelScope.launch(Dispatchers.IO) {
          NexusSecurityManager.recordBiometricOutcome(
            success = false,
            targetType = "VAULT",
            targetName = "Secure Vault",
            reason = msg,
            appLockDao = appLockDao,
          )
        }
        onError(msg)
      },
    )
  }

  fun lockVault() {
    closePreview()
    NexusSecurityManager.lockVault()
  }

  fun updateAutoLockPolicy(delayMs: Long, lockOnScreenOff: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      prefs.updateSecurityPolicy(
        vaultAutoLockDelayMs = delayMs,
        vaultLockOnScreenOff = lockOnScreenOff,
      )
    }
  }

  fun importUris(uris: List<Uri>, folderId: Long, forcedCategory: String? = null) {
    if (uris.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
      var okCount = 0
      var failMsg: String? = null
      for (u in uris) {
        val r = vaultRepo.importFromUri(u, folderId, forcedCategory)
        if (r.isSuccess) okCount++ else failMsg = r.exceptionOrNull()?.message
      }
      _manifestVerified.value = vaultRepo.readEncryptedManifestStatus()
      _statusMessage.value = if (failMsg == null) {
        "Encrypted $okCount file(s) with AES-256-GCM. Originals untouched."
      } else {
        "Imported $okCount; error: $failMsg"
      }
    }
  }

  fun exportItem(item: VaultItemEntity, destUri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      val res = vaultRepo.exportItemToUri(item, destUri)
      _statusMessage.value = if (res.isSuccess) {
        "Decrypted & exported '${item.displayName}' cleanly (integrity verified)"
      } else {
        "Export failed: ${res.exceptionOrNull()?.message}"
      }
    }
  }

  fun verifyIntegrity(item: VaultItemEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      val ok = vaultRepo.verifyItemIntegrity(item).getOrDefault(false)
      _statusMessage.value = if (ok) {
        "Integrity PASSED (AES-GCM tags + SHA-256 matched) for '${item.displayName}'"
      } else {
        "Integrity FAILED for '${item.displayName}'!"
      }
    }
  }

  fun createFolder(name: String) {
    viewModelScope.launch(Dispatchers.IO) {
      vaultRepo.createFolder(name)
      _statusMessage.value = "Created folder '$name'"
    }
  }

  fun renameFolder(id: Long, newName: String) {
    viewModelScope.launch(Dispatchers.IO) { vaultRepo.renameFolder(id, newName) }
  }

  fun deleteFolder(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { vaultRepo.deleteFolder(id) }
  }

  fun renameItem(id: Long, newName: String) {
    viewModelScope.launch(Dispatchers.IO) { vaultRepo.renameItem(id, newName) }
  }

  fun moveItem(id: Long, folderId: Long) {
    viewModelScope.launch(Dispatchers.IO) { vaultRepo.moveItemToFolder(id, folderId) }
  }

  fun deleteItem(item: VaultItemEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      closePreview()
      vaultRepo.deleteItem(item)
      _manifestVerified.value = vaultRepo.readEncryptedManifestStatus()
      _statusMessage.value = "Deleted encrypted item '${item.displayName}'"
    }
  }

  fun openPreview(item: VaultItemEntity) {
    _previewState.value = VaultPreviewState.Loading(item)
    NexusSecurityManager.markVaultActive()
    viewModelScope.launch(Dispatchers.IO) {
      val encFile = File(item.encryptedFilePath)
      if (!encFile.exists()) {
        _previewState.value = VaultPreviewState.Error(item, "Encrypted file missing on disk")
        return@launch
      }
      when (item.category) {
        "PHOTO" -> {
          val bytesRes = vaultRepo.decryptItemBytesForPreview(item)
          bytesRes.fold(
            onSuccess = { bytes ->
              val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
              bytes.fill(0)
              if (bmp != null) {
                _previewState.value = VaultPreviewState.ImagePreview(item, bmp)
              } else {
                _previewState.value = VaultPreviewState.Error(item, "Unsupported image format")
              }
            },
            onFailure = { err ->
              _previewState.value = VaultPreviewState.Error(item, err.message ?: "Decryption failed")
            },
          )
        }
        "VIDEO", "AUDIO" -> {
          _previewState.value = VaultPreviewState.MediaStreamPreview(item, encFile)
        }
        else -> {
          val isTextLike = item.mimeType.startsWith("text/") ||
            item.displayName.endsWith(".txt", true) ||
            item.displayName.endsWith(".json", true) ||
            item.displayName.endsWith(".csv", true) ||
            item.displayName.endsWith(".log", true) ||
            item.displayName.endsWith(".md", true)
          if (isTextLike) {
            val bytesRes = vaultRepo.decryptItemBytesForPreview(item, maxBytes = 2 * 1024 * 1024)
            bytesRes.fold(
              onSuccess = { b ->
                val txt = b.toString(Charsets.UTF_8)
                b.fill(0)
                _previewState.value = VaultPreviewState.TextPreview(item, txt)
              },
              onFailure = { err ->
                _previewState.value = VaultPreviewState.Error(item, err.message ?: "Decryption failed")
              },
            )
          } else {
            // Verify integrity in memory cleanly and summarize binary document for export
            val verified = vaultRepo.verifyItemIntegrity(item).getOrDefault(false)
            _previewState.value = if (verified) {
              VaultPreviewState.TextPreview(
                item,
                "Binary Document (${item.mimeType}, ${item.originalSizeBytes} bytes)\n" +
                  "AES-256-GCM Integrity: VERIFIED\nSHA-256: ${item.sha256Hex}\n\n" +
                  "Use 'Export' to save a decrypted copy to external storage without touching the encrypted vault copy.",
              )
            } else {
              VaultPreviewState.Error(item, "Integrity check failed for binary document")
            }
          }
        }
      }
    }
  }

  fun closePreview() {
    val cur = _previewState.value
    if (cur is VaultPreviewState.ImagePreview && !cur.bitmap.isRecycled) {
      cur.bitmap.recycle()
    }
    _previewState.value = VaultPreviewState.None
  }

  fun exportBackup(passphrase: String, destUri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      vaultRepo.exportEncryptedBackup(passphrase, destUri).fold(
        onSuccess = { count -> _statusMessage.value = "Exported encrypted backup ($count item(s))" },
        onFailure = { err -> _statusMessage.value = "Backup export error: ${err.message}" },
      )
    }
  }

  fun restoreBackup(passphrase: String, srcUri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      vaultRepo.restoreEncryptedBackup(passphrase, srcUri).fold(
        onSuccess = { count ->
          _manifestVerified.value = vaultRepo.readEncryptedManifestStatus()
          _statusMessage.value = "Restored $count item(s) cleanly into encrypted vault"
        },
        onFailure = { err -> _statusMessage.value = "Backup restore failed: ${err.message}" },
      )
    }
  }
}

class AppLockViewModel(app: Application) : AndroidViewModel(app) {
  private val container = NexusContainer.get(app)
  private val dao = container.database.appLockDao()
  val prefs = container.advancedPreferences
  val permissionManager = container.permissionManager

  val securityConfig: StateFlow<SecuritySettingsConfig> = prefs.securityConfig
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecuritySettingsConfig())

  val profiles: StateFlow<List<AppLockProfileEntity>> = dao.observeProfiles()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val authHistory: StateFlow<List<AuthHistoryEntity>> = dao.observeAuthHistory()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _installedApps = MutableStateFlow<List<ManagedAppSummary>>(emptyList())
  val installedApps: StateFlow<List<ManagedAppSummary>> = _installedApps.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      AppLockMonitorCoordinator.ensureDefaultProfiles(getApplication())
      _installedApps.value = runCatching {
        container.appManagerDataSource.loadInstalledApps()
          .filter { it.isLaunchable && it.packageName != getApplication<Application>().packageName }
      }.getOrDefault(emptyList())
    }
  }

  fun clearStatus() {
    _statusMessage.value = null
  }

  fun setAppLockEnabled(enabled: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      val cfg = prefs.getSecuritySnapshot()
      if (enabled && !cfg.hasPinConfigured) {
        _statusMessage.value = "Set a Pattern, PIN, or Password first before enabling App Lock."
        return@launch
      }
      prefs.updateSecurityPolicy(appLockEnabled = enabled)
      _statusMessage.value = if (enabled) "App Lock activated" else "App Lock paused"
    }
  }

  fun setBiometricEnabled(enabled: Boolean) {
    viewModelScope.launch(Dispatchers.IO) { prefs.updateSecurityPolicy(biometricEnabled = enabled) }
  }

  fun setExternalMonitorEnabled(enabled: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      val canDetect = permissionManager.isUsageStatsGranted() || permissionManager.isAccessibilityServiceEnabled()
      if (enabled && !canDetect) {
        _statusMessage.value = "Grant Usage Access (or enable Accessibility Service) to run the background App Lock guard."
        return@launch
      }
      prefs.updateSecurityPolicy(externalMonitorRunning = enabled)
      if (enabled) NexusAppLockMonitorService.start(getApplication())
      else NexusAppLockMonitorService.stop(getApplication())
    }
  }

  fun configureCredential(type: NexusLockType, secret: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val res = NexusSecurityManager.configureCredential(type, secret, prefs, dao)
      _statusMessage.value = if (res.isSuccess) {
        "Shared App Lock & Vault ${type.label} updated (PBKDF2-SHA256)"
      } else {
        res.exceptionOrNull()?.message
      }
    }
  }

  fun configurePin(pin: String) {
    configureCredential(NexusLockType.PIN, pin)
  }

  fun activateProfile(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      dao.clearActiveProfiles()
      dao.activateProfile(id)
    }
  }

  fun createProfile(name: String) {
    viewModelScope.launch(Dispatchers.IO) {
      dao.clearActiveProfiles()
      dao.insertProfile(AppLockProfileEntity(name = name.trim().ifBlank { "Custom Profile" }, isActive = true))
    }
  }

  fun deleteProfile(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      dao.deleteProfile(id)
      AppLockMonitorCoordinator.ensureDefaultProfiles(getApplication())
    }
  }

  fun toggleNexusRoute(profile: AppLockProfileEntity, route: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val cur = AppLockMonitorCoordinator.parseStringList(profile.protectedNexusRoutesJson).toMutableSet()
      if (route in cur) cur.remove(route) else cur.add(route)
      dao.updateProfile(
        profile.copy(
          protectedNexusRoutesJson = AppLockMonitorCoordinator.serializeStringList(cur.toList()),
          updatedAtMs = System.currentTimeMillis(),
        )
      )
    }
  }

  fun toggleExternalPackage(profile: AppLockProfileEntity, packageName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val cur = AppLockMonitorCoordinator.parseStringList(profile.protectedPackagesJson).toMutableSet()
      if (packageName in cur) cur.remove(packageName) else cur.add(packageName)
      dao.updateProfile(
        profile.copy(
          protectedPackagesJson = AppLockMonitorCoordinator.serializeStringList(cur.toList()),
          updatedAtMs = System.currentTimeMillis(),
        )
      )
    }
  }

  fun updateProfileTiming(profile: AppLockProfileEntity, delayMs: Long, lockScreenOff: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      dao.updateProfile(
        profile.copy(
          autoLockDelayMs = delayMs,
          lockOnScreenOff = lockScreenOff,
          updatedAtMs = System.currentTimeMillis(),
        )
      )
    }
  }

  fun lockAllNow() {
    NexusSecurityManager.lockAllImmediately()
    _statusMessage.value = "Locked Vault & all internal protected NEXUS modules"
  }

  fun clearHistory() {
    viewModelScope.launch(Dispatchers.IO) { dao.clearAuthHistory() }
  }
}
