package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.model.SystemShortcut
import com.example.di.NexusContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShortcutsViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  private val _shortcuts = MutableStateFlow<List<SystemShortcut>>(emptyList())
  val shortcuts: StateFlow<List<SystemShortcut>> = _shortcuts.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    _shortcuts.value = container.shortcutsDataSource.loadShortcuts()
  }

  fun openShortcut(shortcut: SystemShortcut): Boolean {
    return container.shortcutsDataSource.openShortcut(shortcut)
  }
}
