package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.NexusActionDispatcher
import com.example.core.NexusActionSpec
import com.example.data.local.EdgePanelConfig
import com.example.data.local.EdgeSide
import com.example.data.local.ExecutionLogEntity
import com.example.data.local.FloatingToolsConfig
import com.example.data.local.GestureMappingEntity
import com.example.data.local.GestureProfileEntity
import com.example.data.local.QuickNoteEntity
import com.example.data.local.SharedShortcutEntity
import com.example.data.model.ManagedAppSummary
import com.example.di.NexusContainer
import com.example.gesture.GestureType
import com.example.overlay.CalculatorEvaluator
import com.example.overlay.NexusEdgePanelService
import com.example.overlay.NexusFloatingToolsService
import com.example.overlay.OverlayTimerCoordinator
import com.example.overlay.OverlayTimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GestureStudioViewModel(app: Application) : AndroidViewModel(app) {
  private val container = NexusContainer.get(app)
  private val dao = container.database.gestureStudioDao()
  private val logDao = container.database.executionLogDao()
  val permissionManager = container.permissionManager
  val actionDispatcher: NexusActionDispatcher = container.actionDispatcher

  val profiles: StateFlow<List<GestureProfileEntity>> = dao.observeProfiles()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  val activeMappings: StateFlow<List<GestureMappingEntity>> = profiles
    .map { list -> list.firstOrNull { it.isActive }?.id ?: 0L }
    .flatMapLatest { pid ->
      if (pid > 0L) dao.observeMappings(pid) else flowOf(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val logs: StateFlow<List<ExecutionLogEntity>> = logDao.observeByType("GESTURE")
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _launchableApps = MutableStateFlow<List<ManagedAppSummary>>(emptyList())
  val launchableApps: StateFlow<List<ManagedAppSummary>> = _launchableApps.asStateFlow()

  private val _lastDetectedSummary = MutableStateFlow("Draw or tap a gesture on the test pad below")
  val lastDetectedSummary: StateFlow<String> = _lastDetectedSummary.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      container.gestureRepository.ensureDefaultProfile()
      _launchableApps.value = runCatching {
        container.appManagerDataSource.loadInstalledApps().filter { it.isLaunchable }
      }.getOrDefault(emptyList())
    }
  }

  fun activateProfile(profileId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      dao.clearActiveProfiles()
      dao.activateProfile(profileId)
    }
  }

  fun createProfile(name: String) {
    viewModelScope.launch(Dispatchers.IO) {
      dao.clearActiveProfiles()
      dao.insertProfile(
        GestureProfileEntity(
          name = name.trim().ifBlank { "Custom Profile" },
          isActive = true,
          sensitivity = 1.0f,
          inAppEnabled = true,
        )
      )
    }
  }

  fun deleteProfile(profile: GestureProfileEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      dao.deleteMappingsForProfile(profile.id)
      dao.deleteProfile(profile.id)
      container.gestureRepository.ensureDefaultProfile()
    }
  }

  fun updateProfileSettings(
    profile: GestureProfileEntity,
    sensitivity: Float = profile.sensitivity,
    zoneWidthDp: Int = profile.zoneWidthDp,
    inAppEnabled: Boolean = profile.inAppEnabled,
    systemOverlayEnabled: Boolean = profile.systemOverlayEnabled,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val updated = profile.copy(
        sensitivity = sensitivity.coerceIn(0.4f, 2.0f),
        zoneWidthDp = zoneWidthDp.coerceIn(16, 72),
        inAppEnabled = inAppEnabled,
        systemOverlayEnabled = systemOverlayEnabled,
        updatedAtMs = System.currentTimeMillis(),
      )
      dao.updateProfile(updated)
      container.advancedPreferences.updateEdgePanelConfig {
        it.copy(systemGesturesAttached = systemOverlayEnabled)
      }
      if (systemOverlayEnabled && permissionManager.isOverlayGranted() && !NexusEdgePanelService.isRunning()) {
        container.advancedPreferences.updateEdgePanelConfig { it.copy(enabled = true, systemGesturesAttached = true) }
        NexusEdgePanelService.start(getApplication())
      }
    }
  }

  fun saveMapping(
    profileId: Long,
    gestureType: GestureType,
    activationZone: String,
    actionSpec: NexusActionSpec,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      dao.insertMapping(
        GestureMappingEntity(
          profileId = profileId,
          gestureType = gestureType.name,
          activationZone = activationZone,
          actionsJson = NexusActionSpec.serializeList(listOf(actionSpec)),
          label = actionSpec.label,
          enabled = true,
        )
      )
    }
  }

  fun deleteMapping(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { dao.deleteMapping(id) }
  }

  fun onTestPadGesture(
    context: Context,
    type: GestureType,
    startX: Float,
    startY: Float,
    widthPx: Float,
    heightPx: Float,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val triggered = container.gestureRepository.dispatchDetectedGesture(
        context, type, startX, startY, widthPx, heightPx
      )
      _lastDetectedSummary.value = triggered
        ?: "Detected ${type.label} (no active mapping matched in this zone)"
    }
  }
}

class OverlayHubViewModel(app: Application) : AndroidViewModel(app) {
  private val container = NexusContainer.get(app)
  private val shortcutDao = container.database.sharedShortcutDao()
  private val noteDao = container.database.quickNoteDao()
  val permissionManager = container.permissionManager
  val actionDispatcher: NexusActionDispatcher = container.actionDispatcher

  val floatingConfig: StateFlow<FloatingToolsConfig> = container.advancedPreferences.floatingToolsConfig
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FloatingToolsConfig())

  val edgeConfig: StateFlow<EdgePanelConfig> = container.advancedPreferences.edgePanelConfig
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EdgePanelConfig())

  val shortcuts: StateFlow<List<SharedShortcutEntity>> = container.sharedShortcutRepository.shortcuts
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val notes: StateFlow<List<QuickNoteEntity>> = container.sharedShortcutRepository.notes
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val timerState: StateFlow<OverlayTimerState> = OverlayTimerCoordinator.state

  private val _stopwatchDisplay = MutableStateFlow("00:00.0")
  val stopwatchDisplay: StateFlow<String> = _stopwatchDisplay.asStateFlow()

  private val _countdownDisplay = MutableStateFlow("03:00.0")
  val countdownDisplay: StateFlow<String> = _countdownDisplay.asStateFlow()

  private val _calcExpression = MutableStateFlow("")
  val calcExpression: StateFlow<String> = _calcExpression.asStateFlow()

  private val _calcOutput = MutableStateFlow("0")
  val calcOutput: StateFlow<String> = _calcOutput.asStateFlow()

  private val _launchableApps = MutableStateFlow<List<ManagedAppSummary>>(emptyList())
  val launchableApps: StateFlow<List<ManagedAppSummary>> = _launchableApps.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      container.sharedShortcutRepository.ensureSeededDefaults()
      _launchableApps.value = runCatching {
        container.appManagerDataSource.loadInstalledApps().filter { it.isLaunchable }
      }.getOrDefault(emptyList())
    }
    viewModelScope.launch {
      while (isActive) {
        val now = System.currentTimeMillis()
        _stopwatchDisplay.value = OverlayTimerCoordinator.formatClockMs(OverlayTimerCoordinator.currentStopwatchMs(now))
        _countdownDisplay.value = OverlayTimerCoordinator.formatClockMs(OverlayTimerCoordinator.currentCountdownMs(now))
        delay(120L)
      }
    }
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun setFloatingToolsEnabled(enabled: Boolean) {
    viewModelScope.launch {
      if (enabled && !permissionManager.isOverlayGranted()) {
        _statusBanner.value = "Grant Display Over Other Apps permission to enable the floating bubble."
        return@launch
      }
      container.advancedPreferences.updateFloatingToolsConfig { it.copy(enabled = enabled) }
      if (enabled) NexusFloatingToolsService.start(getApplication())
      else NexusFloatingToolsService.stop(getApplication())
    }
  }

  fun updateFloatingConfig(transform: (FloatingToolsConfig) -> FloatingToolsConfig) {
    viewModelScope.launch(Dispatchers.IO) {
      container.advancedPreferences.updateFloatingToolsConfig(transform)
    }
  }

  fun setEdgePanelEnabled(enabled: Boolean) {
    viewModelScope.launch {
      if (enabled && !permissionManager.isOverlayGranted()) {
        _statusBanner.value = "Grant Display Over Other Apps permission to attach the Edge Panel."
        return@launch
      }
      container.advancedPreferences.updateEdgePanelConfig { it.copy(enabled = enabled) }
      if (enabled) NexusEdgePanelService.start(getApplication())
      else NexusEdgePanelService.stop(getApplication())
    }
  }

  fun updateEdgeConfig(transform: (EdgePanelConfig) -> EdgePanelConfig) {
    viewModelScope.launch(Dispatchers.IO) {
      container.advancedPreferences.updateEdgePanelConfig(transform)
    }
  }

  fun updateEdgeSide(side: EdgeSide) = updateEdgeConfig { it.copy(side = side) }

  fun addShortcut(
    label: String,
    category: String,
    targetSurface: String,
    actions: List<NexusActionSpec>,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val count = shortcutDao.getAll().size
      shortcutDao.insert(
        SharedShortcutEntity(
          label = label.trim().ifBlank { "Shortcut" },
          category = category,
          isFavorite = false,
          sortOrder = count,
          actionsJson = NexusActionSpec.serializeList(actions),
          targetSurface = targetSurface,
        )
      )
      _statusBanner.value = "Added shortcut '$label' (synced to Floating Tools & Edge Panel)"
    }
  }

  fun toggleShortcutFavorite(id: Long, favorite: Boolean) {
    viewModelScope.launch(Dispatchers.IO) { shortcutDao.setFavorite(id, favorite) }
  }

  fun deleteShortcut(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { shortcutDao.deleteById(id) }
  }

  fun moveShortcutUp(list: List<SharedShortcutEntity>, index: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      container.sharedShortcutRepository.moveShortcutOrder(list, index, (index - 1).coerceAtLeast(0))
    }
  }

  fun moveShortcutDown(list: List<SharedShortcutEntity>, index: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      container.sharedShortcutRepository.moveShortcutOrder(list, index, (index + 1).coerceAtMost(list.lastIndex))
    }
  }

  fun runShortcut(shortcut: SharedShortcutEntity, surface: String) {
    viewModelScope.launch(Dispatchers.IO) {
      container.sharedShortcutRepository.executeShortcut(shortcut, surface)
      _statusBanner.value = "Executed '${shortcut.label}'"
    }
  }

  fun saveQuickNote(id: Long, title: String, content: String) {
    viewModelScope.launch(Dispatchers.IO) {
      noteDao.upsert(
        QuickNoteEntity(
          id = id,
          title = title.trim().ifBlank { content.trim().take(28).ifBlank { "Quick Note" } },
          content = content,
          updatedAtMs = System.currentTimeMillis(),
        )
      )
    }
  }

  fun deleteQuickNote(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { noteDao.deleteById(id) }
  }

  fun pressCalcKey(key: String) {
    when (key) {
      "C" -> {
        _calcExpression.value = ""
        _calcOutput.value = "0"
      }
      "⌫" -> {
        _calcExpression.value = _calcExpression.value.dropLast(1)
        evalPreview()
      }
      "=" -> {
        val res = CalculatorEvaluator.evaluate(_calcExpression.value)
        _calcOutput.value = res.map { CalculatorEvaluator.formatResult(it) }.getOrDefault("Error")
        if (res.isSuccess) _calcExpression.value = _calcOutput.value
      }
      else -> {
        _calcExpression.value += key
        evalPreview()
      }
    }
  }

  private fun evalPreview() {
    val exp = _calcExpression.value
    if (exp.isBlank()) {
      _calcOutput.value = "0"
      return
    }
    CalculatorEvaluator.evaluate(exp).onSuccess {
      _calcOutput.value = CalculatorEvaluator.formatResult(it)
    }
  }
}
