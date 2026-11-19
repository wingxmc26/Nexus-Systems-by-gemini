package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AudioEnvironmentInfo
import com.example.data.system.MicSample
import com.example.data.system.SpeakerTestMode
import com.example.di.NexusContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioUiState(
  val environment: AudioEnvironmentInfo? = null,
  val isSpeakerPlaying: Boolean = false,
  val activeSpeakerMode: SpeakerTestMode = SpeakerTestMode.STEREO_1000HZ,
  val isMicRunning: Boolean = false,
  val micSample: MicSample = MicSample(0f, -90f, List(40) { 0f }),
  val micError: String? = null,
  val speakerError: String? = null,
)

class AudioDiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  private val _uiState = MutableStateFlow(AudioUiState())
  val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

  private var speakerJob: Job? = null
  private var micJob: Job? = null

  init {
    refreshEnvironment()
  }

  fun refreshEnvironment() {
    val env = runCatching { container.audioDataSource.readAudioEnvironment() }.getOrNull()
    _uiState.update { it.copy(environment = env) }
  }

  fun startSpeakerTest(mode: SpeakerTestMode) {
    stopSpeakerTest()
    _uiState.update { it.copy(isSpeakerPlaying = true, activeSpeakerMode = mode, speakerError = null) }
    speakerJob = viewModelScope.launch {
      runCatching {
        container.audioDataSource.runSpeakerTest(mode)
      }.onFailure { err ->
        _uiState.update {
          it.copy(isSpeakerPlaying = false, speakerError = err.message ?: "Speaker output error")
        }
      }
    }
  }

  fun stopSpeakerTest() {
    speakerJob?.cancel()
    speakerJob = null
    _uiState.update { it.copy(isSpeakerPlaying = false) }
  }

  fun startMicTest() {
    stopMicTest()
    _uiState.update { it.copy(isMicRunning = true, micError = null) }
    micJob = viewModelScope.launch {
      container.audioDataSource.observeMicrophoneInput()
        .catch { err ->
          _uiState.update {
            it.copy(
              isMicRunning = false,
              micError = err.message ?: "Unable to open microphone",
            )
          }
        }
        .collect { sample ->
          _uiState.update { it.copy(micSample = sample, micError = null) }
        }
    }
  }

  fun stopMicTest() {
    micJob?.cancel()
    micJob = null
    _uiState.update {
      it.copy(
        isMicRunning = false,
        micSample = MicSample(0f, -90f, List(40) { 0f }),
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    speakerJob?.cancel()
    micJob?.cancel()
  }
}
