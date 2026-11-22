package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.advancedDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_advanced_prefs")

enum class NexusLockType(val label: String) {
  PATTERN("Pattern"),
  PIN("Numeric PIN"),
  PASSWORD("Password"),
}

data class FloatingToolsConfig(
  val enabled: Boolean = false,
  val bubbleSizeDp: Int = 54,
  val opacity: Float = 0.94f,
  val edgeSnapEnabled: Boolean = true,
  val bubbleX: Int = 24,
  val bubbleY: Int = 340,
  val selectedTools: Set<String> = setOf("NOTES", "CALCULATOR", "TIMER", "APPS", "BROWSER", "SHORTCUTS", "MODULES"),
  val lastBrowserUrl: String = "https://duckduckgo.com",
)

enum class EdgeSide { LEFT, RIGHT }

data class EdgePanelConfig(
  val enabled: Boolean = false,
  val side: EdgeSide = EdgeSide.RIGHT,
  val handleWidthDp: Int = 20,
  val handleHeightDp: Int = 156,
  val verticalOffsetPct: Float = 0.36f,
  val sensitivity: Float = 1.0f,
  val panelWidthDp: Int = 310,
  val opacity: Float = 0.95f,
  val systemGesturesAttached: Boolean = false,
  val selectedTools: Set<String> = setOf("BROWSER", "MEDIA", "APPS", "NOTES", "CALCULATOR", "TIMER"),
  val lastBrowserUrl: String = "https://duckduckgo.com",
  val lastActiveTab: String = "BROWSER",
  val lastCalcExpr: String = "",
)

data class SecuritySettingsConfig(
  val hasPinConfigured: Boolean = false,
  val activeLockType: NexusLockType = NexusLockType.PIN,
  val pinHashBase64: String = "",
  val pinSaltBase64: String = "",
  val biometricEnabled: Boolean = true,
  val vaultAutoLockDelayMs: Long = 60_000L,
  val vaultLockOnScreenOff: Boolean = true,
  val appLockEnabled: Boolean = false,
  val externalMonitorRunning: Boolean = false,
  val failedAttempts: Int = 0,
  val lockoutUntilMs: Long = 0L,
)

/**
 * Synchronous SharedPreferences mirror of critical App Lock & Edge Bar guard flags so
 * background services, BootReceiver, AccessibilityService, and onTaskRemoved() can read state
 * in <1ms without waiting for DataStore IPC before OS process termination.
 */
object NexusGuardPrefs {
  private const val PREF_NAME = "nexus_guard_state"
  private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
  private const val KEY_MONITOR_RUNNING = "external_monitor_running"
  private const val KEY_HAS_PIN = "has_pin_configured"
  private const val KEY_LOCK_SCREEN_OFF = "lock_on_screen_off"
  private const val KEY_AUTO_LOCK_DELAY = "auto_lock_delay_ms"
  private const val KEY_PROTECTED_PKGS = "protected_pkgs_set"
  private const val KEY_EDGE_BAR_ENABLED = "edge_bar_enabled"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

  fun syncSecurityState(
    context: Context,
    appLockEnabled: Boolean? = null,
    monitorRunning: Boolean? = null,
    hasPinConfigured: Boolean? = null,
  ) {
    prefs(context).edit().apply {
      appLockEnabled?.let { putBoolean(KEY_APP_LOCK_ENABLED, it) }
      monitorRunning?.let { putBoolean(KEY_MONITOR_RUNNING, it) }
      hasPinConfigured?.let { putBoolean(KEY_HAS_PIN, it) }
    }.apply()
  }

  fun syncActiveProfile(
    context: Context,
    protectedPackages: Set<String>,
    autoLockDelayMs: Long,
    lockOnScreenOff: Boolean,
  ) {
    prefs(context).edit()
      .putStringSet(KEY_PROTECTED_PKGS, protectedPackages)
      .putLong(KEY_AUTO_LOCK_DELAY, autoLockDelayMs)
      .putBoolean(KEY_LOCK_SCREEN_OFF, lockOnScreenOff)
      .apply()
  }

  fun syncEdgeBarEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_EDGE_BAR_ENABLED, enabled).apply()
  }

  fun shouldRunAppLockGuard(context: Context): Boolean {
    val p = prefs(context)
    return p.getBoolean(KEY_APP_LOCK_ENABLED, false) && p.getBoolean(KEY_MONITOR_RUNNING, false)
  }

  fun shouldRunEdgeBar(context: Context): Boolean = prefs(context).getBoolean(KEY_EDGE_BAR_ENABLED, false)

  fun isPackageProtectedSync(context: Context, pkg: String): Boolean {
    val p = prefs(context)
    if (!p.getBoolean(KEY_APP_LOCK_ENABLED, false)) return false
    val set = p.getStringSet(KEY_PROTECTED_PKGS, emptySet()).orEmpty()
    return pkg in set
  }

  fun getAutoLockDelaySync(context: Context): Long = prefs(context).getLong(KEY_AUTO_LOCK_DELAY, 0L)
  fun getLockOnScreenOffSync(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCK_SCREEN_OFF, true)
}

class AdvancedPreferencesRepository(private val context: Context) {

  private object Keys {
    val FT_ENABLED = booleanPreferencesKey("ft_enabled")
    val FT_BUBBLE_SIZE_DP = intPreferencesKey("ft_bubble_size_dp")
    val FT_OPACITY = floatPreferencesKey("ft_opacity")
    val FT_EDGE_SNAP = booleanPreferencesKey("ft_edge_snap")
    val FT_X = intPreferencesKey("ft_x")
    val FT_Y = intPreferencesKey("ft_y")
    val FT_TOOLS_CSV = stringPreferencesKey("ft_tools_csv")
    val FT_BROWSER_URL = stringPreferencesKey("ft_browser_url")

    val EP_ENABLED = booleanPreferencesKey("ep_enabled")
    val EP_SIDE = stringPreferencesKey("ep_side")
    val EP_HANDLE_WIDTH = intPreferencesKey("ep_handle_width")
    val EP_HANDLE_HEIGHT = intPreferencesKey("ep_handle_height")
    val EP_OFFSET_PCT = floatPreferencesKey("ep_offset_pct")
    val EP_SENSITIVITY = floatPreferencesKey("ep_sensitivity")
    val EP_PANEL_WIDTH = intPreferencesKey("ep_panel_width")
    val EP_OPACITY = floatPreferencesKey("ep_opacity")
    val EP_SYS_GESTURES = booleanPreferencesKey("ep_sys_gestures")
    val EP_TOOLS_CSV = stringPreferencesKey("ep_tools_csv")
    val EP_BROWSER_URL = stringPreferencesKey("ep_browser_url")
    val EP_ACTIVE_TAB = stringPreferencesKey("ep_active_tab")
    val EP_CALC_EXPR = stringPreferencesKey("ep_calc_expr")

    val SEC_LOCK_TYPE = stringPreferencesKey("sec_lock_type")
    val SEC_PIN_HASH = stringPreferencesKey("sec_pin_hash")
    val SEC_PIN_SALT = stringPreferencesKey("sec_pin_salt")
    val SEC_BIOMETRIC = booleanPreferencesKey("sec_biometric")
    val SEC_VAULT_AUTOLOCK_MS = longPreferencesKey("sec_vault_autolock_ms")
    val SEC_VAULT_LOCK_SCREEN_OFF = booleanPreferencesKey("sec_vault_lock_screen_off")
    val SEC_APPLOCK_ENABLED = booleanPreferencesKey("sec_applock_enabled")
    val SEC_EXTERNAL_MONITOR = booleanPreferencesKey("sec_external_monitor")
    val SEC_FAILED_ATTEMPTS = intPreferencesKey("sec_failed_attempts")
    val SEC_LOCKOUT_UNTIL_MS = longPreferencesKey("sec_lockout_until_ms")
  }

  val floatingToolsConfig: Flow<FloatingToolsConfig> = context.advancedDataStore.data.map { p ->
    val tools = p[Keys.FT_TOOLS_CSV]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
      ?: setOf("NOTES", "CALCULATOR", "TIMER", "APPS", "BROWSER")
    FloatingToolsConfig(
      enabled = p[Keys.FT_ENABLED] ?: false,
      bubbleSizeDp = (p[Keys.FT_BUBBLE_SIZE_DP] ?: 54).coerceIn(40, 80),
      opacity = (p[Keys.FT_OPACITY] ?: 0.94f).coerceIn(0.35f, 1.0f),
      edgeSnapEnabled = p[Keys.FT_EDGE_SNAP] ?: true,
      bubbleX = p[Keys.FT_X] ?: 24,
      bubbleY = p[Keys.FT_Y] ?: 340,
      selectedTools = tools,
      lastBrowserUrl = p[Keys.FT_BROWSER_URL]?.takeIf { it.isNotBlank() } ?: "https://duckduckgo.com",
    )
  }

  val edgePanelConfig: Flow<EdgePanelConfig> = context.advancedDataStore.data.map { p ->
    val side = runCatching { EdgeSide.valueOf(p[Keys.EP_SIDE] ?: "RIGHT") }.getOrDefault(EdgeSide.RIGHT)
    val tools = p[Keys.EP_TOOLS_CSV]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
      ?: setOf("BROWSER", "MEDIA", "APPS", "NOTES", "CALCULATOR", "TIMER")
    val lastUrl = p[Keys.EP_BROWSER_URL]?.takeIf { it.isNotBlank() }
      ?: p[Keys.FT_BROWSER_URL]?.takeIf { it.isNotBlank() }
      ?: "https://duckduckgo.com"
    EdgePanelConfig(
      enabled = p[Keys.EP_ENABLED] ?: false,
      side = side,
      handleWidthDp = (p[Keys.EP_HANDLE_WIDTH] ?: 20).coerceIn(12, 44),
      handleHeightDp = (p[Keys.EP_HANDLE_HEIGHT] ?: 156).coerceIn(72, 320),
      verticalOffsetPct = (p[Keys.EP_OFFSET_PCT] ?: 0.36f).coerceIn(0.05f, 0.85f),
      sensitivity = (p[Keys.EP_SENSITIVITY] ?: 1.0f).coerceIn(0.4f, 2.0f),
      panelWidthDp = (p[Keys.EP_PANEL_WIDTH] ?: 310).coerceIn(220, 380),
      opacity = (p[Keys.EP_OPACITY] ?: 0.95f).coerceIn(0.4f, 1.0f),
      systemGesturesAttached = p[Keys.EP_SYS_GESTURES] ?: false,
      selectedTools = tools,
      lastBrowserUrl = lastUrl,
      lastActiveTab = p[Keys.EP_ACTIVE_TAB]?.takeIf { it.isNotBlank() } ?: "BROWSER",
      lastCalcExpr = p[Keys.EP_CALC_EXPR].orEmpty(),
    )
  }

  val securityConfig: Flow<SecuritySettingsConfig> = context.advancedDataStore.data.map { p ->
    val hash = p[Keys.SEC_PIN_HASH].orEmpty()
    val salt = p[Keys.SEC_PIN_SALT].orEmpty()
    val lockType = runCatching {
      NexusLockType.valueOf(p[Keys.SEC_LOCK_TYPE] ?: NexusLockType.PIN.name)
    }.getOrDefault(NexusLockType.PIN)
    SecuritySettingsConfig(
      hasPinConfigured = hash.isNotBlank() && salt.isNotBlank(),
      activeLockType = lockType,
      pinHashBase64 = hash,
      pinSaltBase64 = salt,
      biometricEnabled = p[Keys.SEC_BIOMETRIC] ?: true,
      vaultAutoLockDelayMs = p[Keys.SEC_VAULT_AUTOLOCK_MS] ?: 60_000L,
      vaultLockOnScreenOff = p[Keys.SEC_VAULT_LOCK_SCREEN_OFF] ?: true,
      appLockEnabled = p[Keys.SEC_APPLOCK_ENABLED] ?: false,
      externalMonitorRunning = p[Keys.SEC_EXTERNAL_MONITOR] ?: false,
      failedAttempts = p[Keys.SEC_FAILED_ATTEMPTS] ?: 0,
      lockoutUntilMs = p[Keys.SEC_LOCKOUT_UNTIL_MS] ?: 0L,
    )
  }

  suspend fun getFloatingToolsSnapshot(): FloatingToolsConfig = floatingToolsConfig.first()
  suspend fun getEdgePanelSnapshot(): EdgePanelConfig = edgePanelConfig.first()
  suspend fun getSecuritySnapshot(): SecuritySettingsConfig = securityConfig.first()

  suspend fun updateFloatingToolsConfig(transform: (FloatingToolsConfig) -> FloatingToolsConfig) {
    val current = getFloatingToolsSnapshot()
    val next = transform(current)
    context.advancedDataStore.edit { p ->
      p[Keys.FT_ENABLED] = next.enabled
      p[Keys.FT_BUBBLE_SIZE_DP] = next.bubbleSizeDp.coerceIn(40, 80)
      p[Keys.FT_OPACITY] = next.opacity.coerceIn(0.35f, 1.0f)
      p[Keys.FT_EDGE_SNAP] = next.edgeSnapEnabled
      p[Keys.FT_X] = next.bubbleX
      p[Keys.FT_Y] = next.bubbleY
      p[Keys.FT_TOOLS_CSV] = next.selectedTools.joinToString(",")
      p[Keys.FT_BROWSER_URL] = next.lastBrowserUrl
    }
  }

  suspend fun updateEdgePanelConfig(transform: (EdgePanelConfig) -> EdgePanelConfig) {
    val current = getEdgePanelSnapshot()
    val next = transform(current)
    NexusGuardPrefs.syncEdgeBarEnabled(context, next.enabled)
    context.advancedDataStore.edit { p ->
      p[Keys.EP_ENABLED] = next.enabled
      p[Keys.EP_SIDE] = next.side.name
      p[Keys.EP_HANDLE_WIDTH] = next.handleWidthDp.coerceIn(12, 44)
      p[Keys.EP_HANDLE_HEIGHT] = next.handleHeightDp.coerceIn(72, 320)
      p[Keys.EP_OFFSET_PCT] = next.verticalOffsetPct.coerceIn(0.05f, 0.85f)
      p[Keys.EP_SENSITIVITY] = next.sensitivity.coerceIn(0.4f, 2.0f)
      p[Keys.EP_PANEL_WIDTH] = next.panelWidthDp.coerceIn(220, 380)
      p[Keys.EP_OPACITY] = next.opacity.coerceIn(0.4f, 1.0f)
      p[Keys.EP_SYS_GESTURES] = next.systemGesturesAttached
      p[Keys.EP_TOOLS_CSV] = next.selectedTools.joinToString(",")
      p[Keys.EP_BROWSER_URL] = next.lastBrowserUrl
      p[Keys.EP_ACTIVE_TAB] = next.lastActiveTab
      p[Keys.EP_CALC_EXPR] = next.lastCalcExpr
    }
  }

  suspend fun saveCredentials(
    lockType: NexusLockType,
    hashBase64: String,
    saltBase64: String,
  ) {
    NexusGuardPrefs.syncSecurityState(context, hasPinConfigured = hashBase64.isNotBlank() && saltBase64.isNotBlank())
    context.advancedDataStore.edit { p ->
      p[Keys.SEC_LOCK_TYPE] = lockType.name
      p[Keys.SEC_PIN_HASH] = hashBase64
      p[Keys.SEC_PIN_SALT] = saltBase64
      p[Keys.SEC_FAILED_ATTEMPTS] = 0
      p[Keys.SEC_LOCKOUT_UNTIL_MS] = 0L
    }
  }

  suspend fun savePinCredentials(hashBase64: String, saltBase64: String) {
    saveCredentials(NexusLockType.PIN, hashBase64, saltBase64)
  }

  suspend fun updateSecurityPolicy(
    biometricEnabled: Boolean? = null,
    vaultAutoLockDelayMs: Long? = null,
    vaultLockOnScreenOff: Boolean? = null,
    appLockEnabled: Boolean? = null,
    externalMonitorRunning: Boolean? = null,
  ) {
    NexusGuardPrefs.syncSecurityState(
      context,
      appLockEnabled = appLockEnabled,
      monitorRunning = externalMonitorRunning,
    )
    context.advancedDataStore.edit { p ->
      biometricEnabled?.let { p[Keys.SEC_BIOMETRIC] = it }
      vaultAutoLockDelayMs?.let { p[Keys.SEC_VAULT_AUTOLOCK_MS] = it }
      vaultLockOnScreenOff?.let { p[Keys.SEC_VAULT_LOCK_SCREEN_OFF] = it }
      appLockEnabled?.let { p[Keys.SEC_APPLOCK_ENABLED] = it }
      externalMonitorRunning?.let { p[Keys.SEC_EXTERNAL_MONITOR] = it }
    }
  }

  suspend fun recordFailedAttempt(newCount: Int, lockoutUntilMs: Long) {
    context.advancedDataStore.edit { p ->
      p[Keys.SEC_FAILED_ATTEMPTS] = newCount
      p[Keys.SEC_LOCKOUT_UNTIL_MS] = lockoutUntilMs
    }
  }

  suspend fun resetFailedAttempts() {
    context.advancedDataStore.edit { p ->
      p[Keys.SEC_FAILED_ATTEMPTS] = 0
      p[Keys.SEC_LOCKOUT_UNTIL_MS] = 0L
    }
  }
}
