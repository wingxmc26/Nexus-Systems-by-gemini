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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.media.SoundBoosterEngine
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.SoundBoosterViewModel
import java.util.Locale

@Composable
fun SoundBoosterScreen(
  viewModel: SoundBoosterViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val eng = state.engineState
  var profileName by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Sound Booster & EQ",
      subtitle = "10-Band Equalizer, Bass & Loudness",
      onBack = onBack,
      trailingContent = {
        IconButton(onClick = { viewModel.reset() }, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.RestartAlt, contentDescription = "Reset EQ", tint = colors.textPrimary)
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
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        NexusGlassCard(accentHighlight = true, testTag = "sound_booster_master_card") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Master Audio Effects", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Text(eng.statusMessage, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            Switch(
              checked = eng.enabled,
              onCheckedChange = { viewModel.setEnabled(it) },
              modifier = Modifier.testTag("sound_booster_master_switch"),
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
              text = if (eng.audioSessionId > 0) "Session #${eng.audioSessionId}" else "Waiting for Music Session",
              color = if (eng.audioSessionId > 0) colors.nominal else colors.warning,
            )
            StatusPill(text = "Preset: ${eng.activePresetName}", color = colors.accentSecondary)
          }
        }
      }

      item {
        NexusGlassCard {
          SectionHeader(title = "Presets", icon = Icons.Default.GraphicEq)
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            viewModel.builtInPresets.forEach { preset ->
              FilterChip(
                selected = eng.activePresetName == preset.name,
                onClick = { viewModel.applyBuiltInPreset(preset) },
                label = { Text(preset.name) },
              )
            }
          }
        }
      }

      item {
        NexusGlassCard {
          SectionHeader(title = "Bass, Treble, Virtualizer & Safe Preamp")
          Spacer(modifier = Modifier.height(8.dp))

          EffectSlider(
            label = "Bass Boost",
            valueText = "${eng.bassStrength / 10}%",
            value = eng.bassStrength.toFloat(),
            range = 0f..1000f,
            onValueChange = { viewModel.updateBass(it.toInt()) },
          )
          EffectSlider(
            label = "Treble Gain",
            valueText = String.format(Locale.US, "%+.1f dB", eng.trebleBoostMb / 100f),
            value = eng.trebleBoostMb.toFloat(),
            range = -600f..600f,
            onValueChange = { viewModel.updateTreble(it.toInt()) },
          )
          EffectSlider(
            label = "Spatial Virtualizer",
            valueText = "${eng.virtualizerStrength / 10}%",
            value = eng.virtualizerStrength.toFloat(),
            range = 0f..1000f,
            onValueChange = { viewModel.updateVirtualizer(it.toInt()) },
          )
          EffectSlider(
            label = "Safe Preamp / Loudness Boost (Max +8 dB)",
            valueText = String.format(Locale.US, "+%.1f dB", eng.preampGainMb / 100f),
            value = eng.preampGainMb.toFloat(),
            range = 0f..SoundBoosterEngine.SAFE_MAX_PREAMP_MB.toFloat(),
            onValueChange = { viewModel.updatePreamp(it.toInt()) },
          )
        }
      }

      item {
        NexusGlassCard {
          SectionHeader(title = "10-Band Equalizer", icon = Icons.Default.Equalizer, trailingText = "-12 to +12 dB")
          Spacer(modifier = Modifier.height(8.dp))
          SoundBoosterEngine.TEN_BAND_LABELS.forEachIndexed { idx, freq ->
            val mb = eng.tenBandOffsetsMb.getOrElse(idx) { 0 }
            EffectSlider(
              label = freq,
              valueText = String.format(Locale.US, "%+.1f dB", mb / 100f),
              value = mb.toFloat(),
              range = -1200f..1200f,
              onValueChange = { viewModel.updateBand(idx, it.toInt()) },
            )
          }
        }
      }

      item {
        NexusGlassCard {
          SectionHeader(title = "Save Custom EQ Profile", icon = Icons.Default.Save)
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedTextField(
              value = profileName,
              onValueChange = { profileName = it },
              placeholder = { Text("Profile name...") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            Button(
              onClick = {
                viewModel.saveCustomProfile(profileName)
                profileName = ""
              },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            ) { Text("Save") }
          }
          state.feedback?.let { fb ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(fb, style = MaterialTheme.typography.bodyMedium, color = colors.nominal)
          }
        }
      }

      if (state.customProfiles.isNotEmpty()) {
        item { SectionHeader(title = "Saved EQ Profiles", trailingText = "${state.customProfiles.size}") }
        items(state.customProfiles, key = { it.id }) { prof ->
          NexusGlassCard(onClick = { viewModel.loadCustomProfile(prof) }) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                Text(prof.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                  "Bass ${prof.bassStrength / 10}% • Preamp +${prof.preampGainMb / 100}dB",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.textSecondary,
                )
              }
              IconButton(onClick = { viewModel.deleteCustomProfile(prof.id) }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete profile", tint = colors.critical)
              }
            }
          }
        }
      }

      item {
        NexusGlassCard {
          eng.capabilities?.let { cap ->
            MetricKeyValueRow("Equalizer Bands", "${cap.bandCount}")
            MetricKeyValueRow("BassBoost Supported", if (cap.hasBassBoost) "Yes" else "Unavailable")
            MetricKeyValueRow("Virtualizer Supported", if (cap.hasVirtualizer) "Yes" else "Unavailable")
            MetricKeyValueRow("LoudnessEnhancer", if (cap.hasLoudnessEnhancer) "Yes" else "Unavailable")
            Spacer(modifier = Modifier.height(6.dp))
          }
          Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              "Android restricts non-root third-party equalizers from overriding all external streaming apps globally. Effects attach directly to Nexus Music Player & Video Player sessions using android.media.audiofx.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textSecondary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EffectSlider(
  label: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
) {
  val colors = NexusThemeExtras.colors
  Column {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
      Text(valueText, style = MaterialTheme.typography.labelLarge, color = colors.accentSecondary)
    }
    Slider(
      value = value.coerceIn(range.start, range.endInclusive),
      onValueChange = onValueChange,
      valueRange = range,
      colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
    )
  }
}
