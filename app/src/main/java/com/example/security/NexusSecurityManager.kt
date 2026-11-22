package com.example.security

import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.local.AdvancedPreferencesRepository
import com.example.data.local.AppLockDao
import com.example.data.local.AuthHistoryEntity
import com.example.data.local.NexusLockType
import com.example.data.local.SecuritySettingsConfig
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared authentication core used by both Secure Vault and App Lock.
 * Supports Pattern, Numeric PIN, and Password using PBKDF2WithHmacSHA256
 * (120,000 iterations + per-credential 16-byte random salt) with constant-time
 * comparison and brute-force lockout protection.
 */
object NexusSecurityManager {

  private const val PIN_PBKDF2_ITERATIONS = 120_000
  private const val PIN_KEY_BITS = 256
  private val secureRandom = SecureRandom()

  private val _vaultUnlocked = MutableStateFlow(false)
  val vaultUnlocked: StateFlow<Boolean> = _vaultUnlocked.asStateFlow()

  private val _vaultLastActivityMs = MutableStateFlow(0L)

  private val _unlockedNexusRoutes = MutableStateFlow<Map<String, Long>>(emptyMap())
  val unlockedNexusRoutes: StateFlow<Map<String, Long>> = _unlockedNexusRoutes.asStateFlow()

  private val _unlockedExternalPackages = MutableStateFlow<Map<String, Long>>(emptyMap())
  val unlockedExternalPackages: StateFlow<Map<String, Long>> = _unlockedExternalPackages.asStateFlow()

  @Volatile
  private var activeUnlockedExternalPackage: String? = null

  fun lockAllImmediately() {
    _vaultUnlocked.value = false
    _unlockedNexusRoutes.value = emptyMap()
    _unlockedExternalPackages.value = emptyMap()
    activeUnlockedExternalPackage = null
  }

  fun lockVault() {
    _vaultUnlocked.value = false
  }

  fun markVaultActive() {
    if (_vaultUnlocked.value) {
      _vaultLastActivityMs.value = System.currentTimeMillis()
    }
  }

  fun checkVaultAutoLock(config: SecuritySettingsConfig) {
    if (!_vaultUnlocked.value) return
    val delay = config.vaultAutoLockDelayMs
    if (delay <= 0L) return
    val elapsed = System.currentTimeMillis() - _vaultLastActivityMs.value
    if (_vaultLastActivityMs.value > 0L && elapsed >= delay) {
      _vaultUnlocked.value = false
    }
  }

  fun onScreenOffSync(appLockOnScreenOff: Boolean = true) {
    _vaultUnlocked.value = false
    if (appLockOnScreenOff) {
      _unlockedNexusRoutes.value = emptyMap()
      _unlockedExternalPackages.value = emptyMap()
      activeUnlockedExternalPackage = null
    }
  }

  fun onScreenOff(config: SecuritySettingsConfig, appLockOnScreenOff: Boolean = true) {
    if (config.vaultLockOnScreenOff) {
      _vaultUnlocked.value = false
    }
    if (appLockOnScreenOff) {
      _unlockedNexusRoutes.value = emptyMap()
      _unlockedExternalPackages.value = emptyMap()
      activeUnlockedExternalPackage = null
    }
  }

  fun onForegroundPackageTransition(packageName: String) {
    if (packageName.isBlank() || packageName == "com.android.systemui") return
    val currentActive = activeUnlockedExternalPackage
    if (currentActive != null && packageName != currentActive) {
      activeUnlockedExternalPackage = null
    }
  }

  fun isNexusRouteUnlocked(route: String, delayMs: Long): Boolean {
    val ts = _unlockedNexusRoutes.value[route] ?: return false
    if (delayMs <= 0L) return false
    val valid = (System.currentTimeMillis() - ts) <= delayMs
    if (!valid) {
      _unlockedNexusRoutes.value = _unlockedNexusRoutes.value - route
    }
    return valid
  }

  fun markNexusRouteUnlocked(route: String) {
    _unlockedNexusRoutes.value = _unlockedNexusRoutes.value + (route to System.currentTimeMillis())
  }

  fun isExternalPackageUnlocked(pkg: String, delayMs: Long): Boolean {
    if (delayMs <= 0L) {
      val stillActive = activeUnlockedExternalPackage == pkg && _unlockedExternalPackages.value.containsKey(pkg)
      if (!stillActive) {
        _unlockedExternalPackages.value = _unlockedExternalPackages.value - pkg
      }
      return stillActive
    }
    val ts = _unlockedExternalPackages.value[pkg] ?: return false
    val valid = (System.currentTimeMillis() - ts) <= delayMs
    if (!valid) {
      _unlockedExternalPackages.value = _unlockedExternalPackages.value - pkg
      if (activeUnlockedExternalPackage == pkg) activeUnlockedExternalPackage = null
    }
    return valid
  }

  fun markExternalPackageUnlocked(pkg: String) {
    activeUnlockedExternalPackage = pkg
    _unlockedExternalPackages.value = _unlockedExternalPackages.value + (pkg to System.currentTimeMillis())
  }

  fun validateCredentialFormat(type: NexusLockType, rawSecret: String): Result<String> {
    val trimmed = rawSecret.trim()
    return when (type) {
      NexusLockType.PATTERN -> {
        val nodes = trimmed.split("-").filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
        if (nodes.size < 4 || nodes.size != nodes.distinct().size || nodes.any { it !in 0..8 }) {
          Result.failure(IllegalArgumentException("Draw a pattern connecting at least 4 unique dots"))
        } else {
          Result.success(nodes.joinToString("-"))
        }
      }
      NexusLockType.PIN -> {
        if (trimmed.length !in 4..8 || !trimmed.all(Char::isDigit)) {
          Result.failure(IllegalArgumentException("PIN must be 4 to 8 numeric digits"))
        } else {
          Result.success(trimmed)
        }
      }
      NexusLockType.PASSWORD -> {
        if (rawSecret.length < 4 || rawSecret.length > 64 || rawSecret.isBlank()) {
          Result.failure(IllegalArgumentException("Password must be 4 to 64 characters"))
        } else {
          Result.success(rawSecret)
        }
      }
    }
  }

  suspend fun configureCredential(
    type: NexusLockType,
    secret: String,
    prefs: AdvancedPreferencesRepository,
    appLockDao: AppLockDao,
  ): Result<Unit> {
    val normalized = validateCredentialFormat(type, secret).getOrElse { return Result.failure(it) }
    val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
    val hash = pbkdf2Secret(normalized, salt)
    prefs.saveCredentials(
      lockType = type,
      hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP),
      saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
    )
    appLockDao.insertAuthHistory(
      AuthHistoryEntity(
        targetType = "SECURITY_SETUP",
        targetName = "Shared Security ${type.label}",
        method = type.name,
        success = true,
      )
    )
    return Result.success(Unit)
  }

  suspend fun configurePin(
    pin: String,
    prefs: AdvancedPreferencesRepository,
    appLockDao: AppLockDao,
  ): Result<Unit> = configureCredential(NexusLockType.PIN, pin, prefs, appLockDao)

  data class PinVerifyResult(
    val success: Boolean,
    val message: String,
    val remainingLockoutSec: Int = 0,
  )

  suspend fun verifyCredential(
    secret: String,
    targetType: String,
    targetName: String,
    prefs: AdvancedPreferencesRepository,
    appLockDao: AppLockDao,
    unlockVaultOnSuccess: Boolean = false,
    nexusRouteToUnlock: String? = null,
    externalPackageToUnlock: String? = null,
  ): PinVerifyResult {
    val config = prefs.getSecuritySnapshot()
    if (!config.hasPinConfigured) {
      return PinVerifyResult(false, "Security credential has not been configured yet")
    }
    val now = System.currentTimeMillis()
    if (config.lockoutUntilMs > now) {
      val rem = ((config.lockoutUntilMs - now) / 1000L).toInt().coerceAtLeast(1)
      appLockDao.insertAuthHistory(
        AuthHistoryEntity(
          targetType = targetType,
          targetName = targetName,
          method = config.activeLockType.name,
          success = false,
          failureReason = "Cooldown active (${rem}s remaining)",
        )
      )
      return PinVerifyResult(false, "Too many attempts. Wait ${rem}s.", rem)
    }

    val normalized = when (config.activeLockType) {
      NexusLockType.PATTERN -> secret.trim()
      NexusLockType.PIN -> secret.trim()
      NexusLockType.PASSWORD -> secret
    }
    if (normalized.isEmpty()) {
      return PinVerifyResult(false, "Enter your ${config.activeLockType.label.lowercase()}")
    }

    val salt = runCatching { Base64.decode(config.pinSaltBase64, Base64.NO_WRAP) }.getOrNull()
    val expected = runCatching { Base64.decode(config.pinHashBase64, Base64.NO_WRAP) }.getOrNull()
    if (salt == null || expected == null || expected.isEmpty()) {
      return PinVerifyResult(false, "Corrupted security credential; reset in App Lock")
    }

    val candidate = pbkdf2Secret(normalized, salt)
    val match = MessageDigest.isEqual(expected, candidate)

    return if (match) {
      prefs.resetFailedAttempts()
      if (unlockVaultOnSuccess) {
        _vaultUnlocked.value = true
        _vaultLastActivityMs.value = now
      }
      if (!nexusRouteToUnlock.isNullOrBlank()) {
        markNexusRouteUnlocked(nexusRouteToUnlock)
      }
      if (!externalPackageToUnlock.isNullOrBlank()) {
        markExternalPackageUnlocked(externalPackageToUnlock)
      }
      appLockDao.insertAuthHistory(
        AuthHistoryEntity(
          targetType = targetType,
          targetName = targetName,
          method = config.activeLockType.name,
          success = true,
        )
      )
      PinVerifyResult(true, "Unlocked")
    } else {
      val nextFailures = config.failedAttempts + 1
      val lockMs = if (nextFailures >= 5) now + 30_000L else 0L
      prefs.recordFailedAttempt(nextFailures, lockMs)
      val typeLabel = config.activeLockType.label
      appLockDao.insertAuthHistory(
        AuthHistoryEntity(
          targetType = targetType,
          targetName = targetName,
          method = config.activeLockType.name,
          success = false,
          failureReason = if (lockMs > 0L) "Incorrect $typeLabel (30s cooldown triggered)" else "Incorrect $typeLabel (attempt $nextFailures/5)",
        )
      )
      PinVerifyResult(
        success = false,
        message = if (lockMs > 0L) "Incorrect $typeLabel. Locked for 30s." else "Incorrect $typeLabel ($nextFailures/5)",
        remainingLockoutSec = if (lockMs > 0L) 30 else 0,
      )
    }
  }

  suspend fun verifyPin(
    pin: String,
    targetType: String,
    targetName: String,
    prefs: AdvancedPreferencesRepository,
    appLockDao: AppLockDao,
    unlockVaultOnSuccess: Boolean = false,
    nexusRouteToUnlock: String? = null,
    externalPackageToUnlock: String? = null,
  ): PinVerifyResult = verifyCredential(
    secret = pin,
    targetType = targetType,
    targetName = targetName,
    prefs = prefs,
    appLockDao = appLockDao,
    unlockVaultOnSuccess = unlockVaultOnSuccess,
    nexusRouteToUnlock = nexusRouteToUnlock,
    externalPackageToUnlock = externalPackageToUnlock,
  )

  suspend fun recordBiometricOutcome(
    success: Boolean,
    targetType: String,
    targetName: String,
    reason: String,
    appLockDao: AppLockDao,
    unlockVaultOnSuccess: Boolean = false,
    nexusRouteToUnlock: String? = null,
    externalPackageToUnlock: String? = null,
  ) {
    if (success) {
      if (unlockVaultOnSuccess) {
        _vaultUnlocked.value = true
        _vaultLastActivityMs.value = System.currentTimeMillis()
      }
      if (!nexusRouteToUnlock.isNullOrBlank()) markNexusRouteUnlocked(nexusRouteToUnlock)
      if (!externalPackageToUnlock.isNullOrBlank()) markExternalPackageUnlocked(externalPackageToUnlock)
    }
    appLockDao.insertAuthHistory(
      AuthHistoryEntity(
        targetType = targetType,
        targetName = targetName,
        method = "BIOMETRIC",
        success = success,
        failureReason = if (success) "" else reason,
      )
    )
  }

  fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    fallbackLabel: String = "Use Passcode",
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
  ) {
    val bm = BiometricManager.from(activity)
    val canAuth = bm.canAuthenticate(
      BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    )
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
      onFailure("No enrolled biometrics or hardware unavailable; use your lock credential.")
      return
    }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
      activity,
      executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          onFailure(errString.toString())
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          onFailure("Biometric not recognized")
        }
      },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.BIOMETRIC_WEAK
      )
      .setNegativeButtonText(fallbackLabel)
      .build()
    prompt.authenticate(info)
  }

  private fun pbkdf2Secret(secret: String, salt: ByteArray): ByteArray {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(secret.toCharArray(), salt, PIN_PBKDF2_ITERATIONS, PIN_KEY_BITS)
    return factory.generateSecret(spec).encoded
  }
}
