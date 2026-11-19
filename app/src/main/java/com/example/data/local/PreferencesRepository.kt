package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DashboardModuleId
import com.example.data.model.DashboardPreferences
import com.example.ui.theme.AccentPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nexusDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_prefs")

class PreferencesRepository(private val context: Context) {

  private object Keys {
    val ACCENT_ID = stringPreferencesKey("accent_id")
    val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
    val REFRESH_INTERVAL_MS = longPreferencesKey("refresh_interval_ms")
    val CARD_ORDER = stringPreferencesKey("card_order")
    val HIDDEN_MODULES = stringPreferencesKey("hidden_modules")
  }

  val preferences: Flow<DashboardPreferences> = context.nexusDataStore.data.map { prefs ->
    val accent = AccentPalette.fromId(prefs[Keys.ACCENT_ID])
    val amoled = prefs[Keys.AMOLED_MODE] ?: true
    val refresh = prefs[Keys.REFRESH_INTERVAL_MS] ?: 2000L

    val storedOrder = prefs[Keys.CARD_ORDER]
      ?.split(",")
      ?.mapNotNull { DashboardModuleId.fromId(it.trim()) }
      .orEmpty()
    val completeOrder = (storedOrder + DashboardModuleId.defaultOrder).distinct()

    val hidden = prefs[Keys.HIDDEN_MODULES]
      ?.split(",")
      ?.mapNotNull { DashboardModuleId.fromId(it.trim()) }
      ?.toSet()
      .orEmpty()

    DashboardPreferences(
      accentPalette = accent,
      amoledMode = amoled,
      refreshIntervalMs = refresh.coerceIn(1000L, 10_000L),
      cardOrder = completeOrder,
      hiddenModules = hidden,
    )
  }

  suspend fun setAccentPalette(palette: AccentPalette) {
    context.nexusDataStore.edit { it[Keys.ACCENT_ID] = palette.id }
  }

  suspend fun setAmoledMode(enabled: Boolean) {
    context.nexusDataStore.edit { it[Keys.AMOLED_MODE] = enabled }
  }

  suspend fun setRefreshIntervalMs(intervalMs: Long) {
    context.nexusDataStore.edit { it[Keys.REFRESH_INTERVAL_MS] = intervalMs }
  }

  suspend fun setCardOrder(order: List<DashboardModuleId>) {
    context.nexusDataStore.edit { it[Keys.CARD_ORDER] = order.joinToString(",") { m -> m.id } }
  }

  suspend fun setModuleVisibility(moduleId: DashboardModuleId, visible: Boolean) {
    context.nexusDataStore.edit { prefs ->
      val current = prefs[Keys.HIDDEN_MODULES]
        ?.split(",")
        ?.mapNotNull { DashboardModuleId.fromId(it.trim()) }
        ?.toMutableSet() ?: mutableSetOf()
      if (visible) current.remove(moduleId) else current.add(moduleId)
      prefs[Keys.HIDDEN_MODULES] = current.joinToString(",") { it.id }
    }
  }

  suspend fun resetDefaults() {
    context.nexusDataStore.edit { it.clear() }
  }
}
