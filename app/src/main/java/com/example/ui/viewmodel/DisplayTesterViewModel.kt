package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import com.example.data.model.DisplaySpecs
import com.example.di.NexusContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DisplayTestMode {
  NONE,
  DEAD_PIXEL,
  BANDING,
  MULTITOUCH,
}

data class DeadPixelColor(val name: String, val color: Color, val isDark: Boolean)

class DisplayTesterViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  val specs: DisplaySpecs = container.displayDataSource.readDisplaySpecs()

  val deadPixelColors = listOf(
    DeadPixelColor("Pure Red (#FF0000)", Color(0xFFFF0000), isDark = true),
    DeadPixelColor("Pure Green (#00FF00)", Color(0xFF00FF00), isDark = false),
    DeadPixelColor("Pure Blue (#0000FF)", Color(0xFF0000FF), isDark = true),
    DeadPixelColor("Pure White (#FFFFFF)", Color(0xFFFFFFFF), isDark = false),
    DeadPixelColor("Pure Black (#000000)", Color(0xFF000000), isDark = true),
  )

  private val _activeTest = MutableStateFlow(DisplayTestMode.NONE)
  val activeTest: StateFlow<DisplayTestMode> = _activeTest.asStateFlow()

  private val _colorIndex = MutableStateFlow(0)
  val colorIndex: StateFlow<Int> = _colorIndex.asStateFlow()

  fun startDeadPixelTest(startIndex: Int = 0) {
    _colorIndex.value = startIndex.coerceIn(0, deadPixelColors.lastIndex)
    _activeTest.value = DisplayTestMode.DEAD_PIXEL
  }

  fun nextDeadPixelColor() {
    _colorIndex.value = (_colorIndex.value + 1) % deadPixelColors.size
  }

  fun startBandingTest() {
    _activeTest.value = DisplayTestMode.BANDING
  }

  fun startMultiTouchTest() {
    _activeTest.value = DisplayTestMode.MULTITOUCH
  }

  fun exitTest() {
    _activeTest.value = DisplayTestMode.NONE
  }
}
