package com.example.ui.viewmodel

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.EqProfileEntity
import com.example.data.model.SoundBoosterState
import com.example.di.NexusContainer
import com.example.media.EqPresetSpec
import com.example.media.SoundBoosterEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SoundBoosterUiState(
  val engineState: SoundBoosterState = SoundBoosterState(),
  val customProfiles: List<EqProfileEntity> = emptyList(),
  val feedback: String? = null,
)

class SoundBoosterViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val engine = container.soundBoosterEngine
  private val dao = container.database.eqProfileDao()
  private val _feedback = MutableStateFlow<String?>(null)

  val builtInPresets: List<EqPresetSpec> = SoundBoosterEngine.BUILT_IN_PRESETS

  val uiState: StateFlow<SoundBoosterUiState> = combine(
    engine.state,
    dao.observeProfiles(),
    _feedback,
  ) { eng, profiles, msg ->
    SoundBoosterUiState(
      engineState = eng,
      customProfiles = profiles,
      feedback = msg,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = SoundBoosterUiState(),
  )

  init {
    viewModelScope.launch {
      val prefs = container.preferencesRepository.getSoundBoosterSnapshot()
      val enabled = prefs[booleanPreferencesKey("sound_booster_enabled")] ?: false
      val preset = prefs[stringPreferencesKey("sound_booster_preset")] ?: "Normal"
      val bands = prefs[stringPreferencesKey("sound_booster_bands")]
        ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        ?: List(10) { 0 }
      val bass = prefs[intPreferencesKey("sound_booster_bass")] ?: 0
      val treble = prefs[intPreferencesKey("sound_booster_treble")] ?: 0
      val virt = prefs[intPreferencesKey("sound_booster_virtualizer")] ?: 0
      val preamp = prefs[intPreferencesKey("sound_booster_preamp")] ?: 0
      engine.applySavedSnapshot(enabled, preset, bands, bass, treble, virt, preamp)
    }
  }

  private fun persistCurrent() {
    val s = engine.state.value
    viewModelScope.launch {
      container.preferencesRepository.saveSoundBoosterState(
        enabled = s.enabled,
        preset = s.activePresetName,
        bands = s.tenBandOffsetsMb,
        bass = s.bassStrength,
        treble = s.trebleBoostMb,
        virtualizer = s.virtualizerStrength,
        preamp = s.preampGainMb,
      )
    }
  }

  fun setEnabled(enabled: Boolean) {
    engine.setEnabled(enabled)
    persistCurrent()
  }

  fun applyBuiltInPreset(spec: EqPresetSpec) {
    engine.applyPreset(spec)
    persistCurrent()
    _feedback.value = "Applied preset '${spec.name}'."
  }

  fun updateBand(index: Int, offsetMb: Int) {
    engine.updateBand(index, offsetMb)
    persistCurrent()
  }

  fun updateBass(strength: Int) {
    engine.updateBassStrength(strength)
    persistCurrent()
  }

  fun updateTreble(trebleMb: Int) {
    engine.updateTrebleBoost(trebleMb)
    persistCurrent()
  }

  fun updateVirtualizer(strength: Int) {
    engine.updateVirtualizer(strength)
    persistCurrent()
  }

  fun updatePreamp(gainMb: Int) {
    engine.updatePreampGain(gainMb)
    persistCurrent()
  }

  fun reset() {
    engine.reset()
    persistCurrent()
    _feedback.value = "Reset Sound Booster & EQ to flat response."
  }

  fun saveCustomProfile(name: String) {
    val clean = name.trim().ifBlank { "Custom EQ" }
    val s = engine.state.value
    viewModelScope.launch {
      dao.insert(
        EqProfileEntity(
          name = clean,
          bandsCsv = s.tenBandOffsetsMb.joinToString(","),
          bassStrength = s.bassStrength,
          trebleBoostMb = s.trebleBoostMb,
          virtualizerStrength = s.virtualizerStrength,
          preampGainMb = s.preampGainMb,
        )
      )
      _feedback.value = "Saved EQ profile '$clean'."
    }
  }

  fun loadCustomProfile(profile: EqProfileEntity) {
    val bands = profile.bandsCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
    engine.applyPreset(
      EqPresetSpec(
        name = profile.name,
        tenBandOffsetsMb = if (bands.size == 10) bands else List(10) { 0 },
        bassStrength = profile.bassStrength,
        trebleBoostMb = profile.trebleBoostMb,
        virtualizerStrength = profile.virtualizerStrength,
        preampGainMb = profile.preampGainMb,
      )
    )
    persistCurrent()
    _feedback.value = "Loaded EQ profile '${profile.name}'."
  }

  fun deleteCustomProfile(id: Long) {
    viewModelScope.launch { dao.deleteById(id) }
  }
}
