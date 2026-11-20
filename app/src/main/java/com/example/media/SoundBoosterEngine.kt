package com.example.media

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.example.data.model.AudioEffectCapabilities
import com.example.data.model.SoundBoosterState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

data class EqPresetSpec(
  val name: String,
  val tenBandOffsetsMb: List<Int>,
  val bassStrength: Int,
  val trebleBoostMb: Int,
  val virtualizerStrength: Int,
  val preampGainMb: Int,
)

class SoundBoosterEngine {
  companion object {
    val TEN_BAND_LABELS = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    const val SAFE_MAX_PREAMP_MB = 800

    val BUILT_IN_PRESETS = listOf(
      EqPresetSpec("Normal", List(10) { 0 }, 0, 0, 0, 0),
      EqPresetSpec("Bass Boost", listOf(550, 480, 350, 180, 0, 0, 0, 0, 100, 100), 650, 0, 150, 150),
      EqPresetSpec("Treble Boost", listOf(0, 0, 0, 0, 80, 150, 300, 450, 550, 600), 0, 450, 100, 150),
      EqPresetSpec("Vocal", listOf(-150, -100, 0, 200, 380, 420, 340, 180, 50, -50), 100, 100, 200, 100),
      EqPresetSpec("Classical", listOf(300, 220, 140, 0, -80, -80, 0, 180, 280, 320), 150, 150, 350, 100),
      EqPresetSpec("Rock", listOf(420, 300, 150, -100, -150, 80, 250, 380, 420, 350), 400, 250, 250, 200),
      EqPresetSpec("Pop", listOf(-100, 150, 320, 350, 200, -80, -120, 120, 240, 260), 250, 150, 200, 150),
    )
  }

  private val _state = MutableStateFlow(SoundBoosterState())
  val state: StateFlow<SoundBoosterState> = _state.asStateFlow()

  private var equalizer: Equalizer? = null
  private var bassBoost: BassBoost? = null
  private var virtualizer: Virtualizer? = null
  private var loudnessEnhancer: LoudnessEnhancer? = null

  @Synchronized
  fun attachToSession(audioSessionId: Int) {
    if (audioSessionId <= 0) return
    if (_state.value.audioSessionId == audioSessionId && equalizer != null) {
      applyCurrentParameters()
      return
    }
    releaseEffects()

    var hasEq = false
    var bandCount = 0
    val centerHz = mutableListOf<Int>()
    var minLvl = -1500
    var maxLvl = 1500
    var hasBass = false
    var hasVirt = false
    var hasLoud = false

    runCatching {
      val eq = Equalizer(0, audioSessionId)
      equalizer = eq
      hasEq = true
      bandCount = eq.numberOfBands.toInt()
      val range = eq.bandLevelRange
      minLvl = range[0].toInt()
      maxLvl = range[1].toInt()
      for (b in 0 until bandCount) {
        centerHz.add(eq.getCenterFreq(b.toShort()) / 1000)
      }
    }

    runCatching {
      val bb = BassBoost(0, audioSessionId)
      bassBoost = bb
      hasBass = bb.strengthSupported
    }

    runCatching {
      val virt = Virtualizer(0, audioSessionId)
      virtualizer = virt
      hasVirt = virt.strengthSupported
    }

    runCatching {
      loudnessEnhancer = LoudnessEnhancer(audioSessionId)
      hasLoud = true
    }

    val caps = AudioEffectCapabilities(
      hasEqualizer = hasEq,
      bandCount = bandCount,
      bandCenterHz = centerHz,
      minBandLevelMb = minLvl,
      maxBandLevelMb = maxLvl,
      hasBassBoost = hasBass,
      hasVirtualizer = hasVirt,
      hasLoudnessEnhancer = hasLoud,
    )

    val status = if (hasEq || hasBass || hasLoud) {
      "Attached to Nexus Music audio session #$audioSessionId ($bandCount hardware EQ bands)."
    } else {
      "Hardware audio effects are unavailable for session #$audioSessionId on this device."
    }

    _state.update {
      it.copy(
        audioSessionId = audioSessionId,
        capabilities = caps,
        statusMessage = status,
      )
    }
    applyCurrentParameters()
  }

  @Synchronized
  fun setEnabled(enabled: Boolean) {
    _state.update { it.copy(enabled = enabled) }
    applyCurrentParameters()
  }

  @Synchronized
  fun applyPreset(preset: EqPresetSpec) {
    _state.update {
      it.copy(
        activePresetName = preset.name,
        tenBandOffsetsMb = preset.tenBandOffsetsMb.take(10),
        bassStrength = preset.bassStrength.coerceIn(0, 1000),
        trebleBoostMb = preset.trebleBoostMb.coerceIn(-600, 600),
        virtualizerStrength = preset.virtualizerStrength.coerceIn(0, 1000),
        preampGainMb = preset.preampGainMb.coerceIn(0, SAFE_MAX_PREAMP_MB),
      )
    }
    applyCurrentParameters()
  }

  @Synchronized
  fun updateBand(index: Int, offsetMb: Int) {
    if (index !in 0 until 10) return
    val updated = _state.value.tenBandOffsetsMb.toMutableList()
    updated[index] = offsetMb.coerceIn(-1200, 1200)
    _state.update {
      it.copy(
        activePresetName = "Custom",
        tenBandOffsetsMb = updated,
      )
    }
    applyCurrentParameters()
  }

  @Synchronized
  fun updateBassStrength(strength: Int) {
    _state.update { it.copy(activePresetName = "Custom", bassStrength = strength.coerceIn(0, 1000)) }
    applyCurrentParameters()
  }

  @Synchronized
  fun updateTrebleBoost(trebleMb: Int) {
    _state.update { it.copy(activePresetName = "Custom", trebleBoostMb = trebleMb.coerceIn(-600, 600)) }
    applyCurrentParameters()
  }

  @Synchronized
  fun updateVirtualizer(strength: Int) {
    _state.update { it.copy(activePresetName = "Custom", virtualizerStrength = strength.coerceIn(0, 1000)) }
    applyCurrentParameters()
  }

  @Synchronized
  fun updatePreampGain(gainMb: Int) {
    _state.update {
      it.copy(
        activePresetName = "Custom",
        preampGainMb = gainMb.coerceIn(0, SAFE_MAX_PREAMP_MB),
      )
    }
    applyCurrentParameters()
  }

  @Synchronized
  fun reset() {
    applyPreset(BUILT_IN_PRESETS.first())
  }

  @Synchronized
  fun applySavedSnapshot(
    enabled: Boolean,
    presetName: String,
    bands: List<Int>,
    bass: Int,
    treble: Int,
    virtualizer: Int,
    preamp: Int,
  ) {
    val cleanBands = if (bands.size == 10) bands else List(10) { 0 }
    _state.update {
      it.copy(
        enabled = enabled,
        activePresetName = presetName,
        tenBandOffsetsMb = cleanBands,
        bassStrength = bass.coerceIn(0, 1000),
        trebleBoostMb = treble.coerceIn(-600, 600),
        virtualizerStrength = virtualizer.coerceIn(0, 1000),
        preampGainMb = preamp.coerceIn(0, SAFE_MAX_PREAMP_MB),
      )
    }
    applyCurrentParameters()
  }

  @Synchronized
  private fun applyCurrentParameters() {
    val cur = _state.value
    val active = cur.enabled

    equalizer?.let { eq ->
      runCatching {
        eq.enabled = active
        if (active) {
          val n = eq.numberOfBands.toInt()
          val range = eq.bandLevelRange
          val minL = range[0].toInt()
          val maxL = range[1].toInt()
          for (b in 0 until n) {
            val mappedIdx = if (n <= 1) 0 else ((b.toFloat() / (n - 1).coerceAtLeast(1)) * 9f).roundToInt().coerceIn(0, 9)
            val trebleContribution = if (mappedIdx >= 7) cur.trebleBoostMb else 0
            val lvl = (cur.tenBandOffsetsMb[mappedIdx] + trebleContribution).coerceIn(minL, maxL)
            eq.setBandLevel(b.toShort(), lvl.toShort())
          }
        }
      }
    }

    bassBoost?.let { bb ->
      runCatching {
        bb.enabled = active
        if (active && bb.strengthSupported) {
          bb.setStrength(cur.bassStrength.toShort())
        }
      }
    }

    virtualizer?.let { virt ->
      runCatching {
        virt.enabled = active
        if (active && virt.strengthSupported) {
          virt.setStrength(cur.virtualizerStrength.toShort())
        }
      }
    }

    loudnessEnhancer?.let { le ->
      runCatching {
        le.enabled = active && cur.preampGainMb > 0
        if (active) {
          le.setTargetGain(cur.preampGainMb.coerceIn(0, SAFE_MAX_PREAMP_MB))
        }
      }
    }
  }

  @Synchronized
  fun releaseEffects() {
    runCatching { equalizer?.release() }
    runCatching { bassBoost?.release() }
    runCatching { virtualizer?.release() }
    runCatching { loudnessEnhancer?.release() }
    equalizer = null
    bassBoost = null
    virtualizer = null
    loudnessEnhancer = null
  }
}
