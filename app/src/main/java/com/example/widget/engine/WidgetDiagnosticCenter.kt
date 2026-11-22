package com.example.widget.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Bounded diagnostic ring-buffer tracking widget rendering errors, JS exceptions,
 * permission failures, and provider status without leaking sensitive user content.
 */
object WidgetDiagnosticCenter {
  private const val MAX_ENTRIES = 80
  private val _entries = MutableStateFlow<List<WidgetDiagnosticEntry>>(emptyList())
  val entries: StateFlow<List<WidgetDiagnosticEntry>> = _entries.asStateFlow()

  fun record(
    widgetKey: String,
    category: String,
    severity: DiagnosticSeverity,
    message: String,
    widgetId: Int? = null,
    suggestion: String? = null,
  ) {
    val sanitizedMsg = message.take(240)
    val entry = WidgetDiagnosticEntry(
      widgetKey = widgetKey,
      widgetId = widgetId,
      category = category,
      severity = severity,
      message = sanitizedMsg,
      suggestion = suggestion?.take(200),
    )
    _entries.update { current ->
      (listOf(entry) + current).take(MAX_ENTRIES)
    }
  }

  fun latestForWidget(widgetId: Int): WidgetDiagnosticEntry? {
    return _entries.value.firstOrNull { it.widgetId == widgetId }
  }

  fun latestErrors(limit: Int = 10): List<WidgetDiagnosticEntry> {
    return _entries.value.filter { it.severity == DiagnosticSeverity.ERROR || it.severity == DiagnosticSeverity.WARN }
      .take(limit)
  }

  fun clear() {
    _entries.value = emptyList()
  }
}
