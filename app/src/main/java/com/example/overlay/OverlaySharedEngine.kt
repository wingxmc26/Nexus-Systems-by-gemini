package com.example.overlay

import android.content.Context
import com.example.core.NexusActionDispatcher
import com.example.core.NexusActionSpec
import com.example.core.NexusActionType
import com.example.data.local.QuickNoteDao
import com.example.data.local.QuickNoteEntity
import com.example.data.local.SharedShortcutDao
import com.example.data.local.SharedShortcutEntity
import com.example.ui.navigation.NexusRoutes
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlaySurfaceFilter {
  fun matchesFloatingTools(targetSurface: String): Boolean =
    targetSurface == "BOTH" || targetSurface == "FLOATING_TOOLS" || targetSurface == "FLOATING"

  fun matchesEdgePanel(targetSurface: String): Boolean =
    targetSurface == "BOTH" || targetSurface == "EDGE_PANEL" || targetSurface == "EDGE"
}

class SharedShortcutRepository(
  private val dao: SharedShortcutDao,
  private val noteDao: QuickNoteDao,
  private val actionDispatcher: NexusActionDispatcher,
) {
  val shortcuts: Flow<List<SharedShortcutEntity>> = dao.observeAll()
  val notes: Flow<List<QuickNoteEntity>> = noteDao.observeAll()

  suspend fun ensureSeededDefaults(): List<SharedShortcutEntity> {
    val existing = dao.getAll()
    if (existing.isNotEmpty()) return existing

    val defaults = listOf(
      SharedShortcutEntity(
        label = "Battery & Charging",
        category = "NEXUS",
        isFavorite = true,
        sortOrder = 0,
        actionsJson = NexusActionSpec.serializeList(
          listOf(NexusActionSpec(NexusActionType.OPEN_NEXUS_MODULE, "Battery Monitor", NexusRoutes.BATTERY))
        ),
        targetSurface = "BOTH",
        iconKey = "battery",
      ),
      SharedShortcutEntity(
        label = "App Lock & Guard",
        category = "NEXUS",
        isFavorite = true,
        sortOrder = 1,
        actionsJson = NexusActionSpec.serializeList(
          listOf(NexusActionSpec(NexusActionType.OPEN_NEXUS_MODULE, "App Lock", NexusRoutes.APP_LOCK))
        ),
        targetSurface = "BOTH",
        iconKey = "lock",
      ),
      SharedShortcutEntity(
        label = "Secure Vault",
        category = "NEXUS",
        isFavorite = true,
        sortOrder = 2,
        actionsJson = NexusActionSpec.serializeList(
          listOf(NexusActionSpec(NexusActionType.OPEN_NEXUS_MODULE, "Secure Vault", NexusRoutes.SECURE_VAULT))
        ),
        targetSurface = "BOTH",
        iconKey = "lock",
      ),
      SharedShortcutEntity(
        label = "Media Play / Pause",
        category = "Quick Actions",
        isFavorite = true,
        sortOrder = 3,
        actionsJson = NexusActionSpec.serializeList(
          listOf(NexusActionSpec(NexusActionType.MEDIA_PLAY_PAUSE, "Media Play/Pause"))
        ),
        targetSurface = "BOTH",
        iconKey = "music",
      ),
      SharedShortcutEntity(
        label = "Refresh + Lock Down",
        category = "Combos",
        isFavorite = false,
        sortOrder = 4,
        actionsJson = NexusActionSpec.serializeList(
          listOf(
            NexusActionSpec(NexusActionType.REFRESH_WIDGETS, "Refresh Widgets"),
            NexusActionSpec(NexusActionType.LOCK_VAULT_AND_APPS, "Lock Vault & Modules"),
            NexusActionSpec(NexusActionType.HAPTIC_PULSE, "Haptic Confirm", "60"),
          )
        ),
        targetSurface = "BOTH",
        iconKey = "combo",
      ),
    )
    defaults.forEach { dao.insert(it) }
    return dao.getAll()
  }

  suspend fun executeShortcut(shortcut: SharedShortcutEntity, surfaceName: String) {
    val actions = NexusActionSpec.parseList(shortcut.actionsJson)
    actionDispatcher.dispatchAll(
      actions = actions,
      sourceType = surfaceName,
      sourceId = shortcut.id,
      sourceName = shortcut.label,
      branch = shortcut.category,
    )
  }

  suspend fun moveShortcutOrder(items: List<SharedShortcutEntity>, fromIndex: Int, toIndex: Int) {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return
    val mutable = items.toMutableList()
    val moved = mutable.removeAt(fromIndex)
    mutable.add(toIndex, moved)
    mutable.forEachIndexed { idx, entity ->
      dao.setSortOrder(entity.id, idx)
    }
  }
}

/**
 * Pure Kotlin mathematical expression parser & evaluator supporting +, -, *, /, %, parentheses,
 * unary negation, and floating-point arithmetic.
 */
object CalculatorEvaluator {

  fun evaluate(expression: String): Result<Double> {
    val clean = expression.replace(" ", "").replace("×", "*").replace("÷", "/")
    if (clean.isBlank()) return Result.failure(IllegalArgumentException("Empty expression"))

    return runCatching {
      val tokens = tokenize(clean)
      val rpn = toRpn(tokens)
      evalRpn(rpn)
    }
  }

  fun formatResult(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "Error"
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) {
      asLong.toString()
    } else {
      String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
    }
  }

  private fun tokenize(expr: String): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < expr.length) {
      val ch = expr[i]
      when {
        ch.isDigit() || ch == '.' -> {
          val sb = StringBuilder()
          while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
            sb.append(expr[i])
            i++
          }
          out += sb.toString()
        }
        ch == '-' && (out.isEmpty() || out.last() in listOf("(", "+", "-", "*", "/", "%")) -> {
          // Unary minus
          i++
          val sb = StringBuilder("-")
          if (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
            while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
              sb.append(expr[i])
              i++
            }
            out += sb.toString()
          } else {
            out += "-1"
            out += "*"
          }
        }
        ch in charArrayOf('+', '-', '*', '/', '%', '(', ')') -> {
          out += ch.toString()
          i++
        }
        else -> throw IllegalArgumentException("Invalid character: $ch")
      }
    }
    return out
  }

  private fun precedence(op: String): Int = when (op) {
    "+", "-" -> 1
    "*", "/", "%" -> 2
    else -> 0
  }

  private fun toRpn(tokens: List<String>): List<String> {
    val output = mutableListOf<String>()
    val stack = ArrayDeque<String>()
    for (t in tokens) {
      when {
        t.toDoubleOrNull() != null -> output += t
        t == "(" -> stack.addLast(t)
        t == ")" -> {
          while (stack.isNotEmpty() && stack.last() != "(") {
            output += stack.removeLast()
          }
          if (stack.isNotEmpty() && stack.last() == "(") stack.removeLast()
          else throw IllegalArgumentException("Mismatched parentheses")
        }
        else -> {
          while (stack.isNotEmpty() && precedence(stack.last()) >= precedence(t)) {
            output += stack.removeLast()
          }
          stack.addLast(t)
        }
      }
    }
    while (stack.isNotEmpty()) {
      val top = stack.removeLast()
      if (top == "(" || top == ")") throw IllegalArgumentException("Mismatched parentheses")
      output += top
    }
    return output
  }

  private fun evalRpn(rpn: List<String>): Double {
    val stack = ArrayDeque<Double>()
    for (t in rpn) {
      val num = t.toDoubleOrNull()
      if (num != null) {
        stack.addLast(num)
      } else {
        if (stack.size < 2) throw IllegalArgumentException("Malformed expression")
        val b = stack.removeLast()
        val a = stack.removeLast()
        val res = when (t) {
          "+" -> a + b
          "-" -> a - b
          "*" -> a * b
          "/" -> {
            if (b == 0.0) throw ArithmeticException("Division by zero")
            a / b
          }
          "%" -> a % b
          else -> throw IllegalArgumentException("Unknown operator: $t")
        }
        stack.addLast(res)
      }
    }
    if (stack.size != 1) throw IllegalArgumentException("Malformed expression")
    return stack.last()
  }
}

data class OverlayTimerState(
  val stopwatchRunning: Boolean = false,
  val stopwatchElapsedMs: Long = 0L,
  val stopwatchStartedBaseMs: Long = 0L,
  val countdownTotalMs: Long = 180_000L,
  val countdownRemainingMs: Long = 180_000L,
  val countdownRunning: Boolean = false,
  val countdownEndWallMs: Long = 0L,
  val countdownCompletedAlert: Boolean = false,
)

object OverlayTimerCoordinator {
  private val _state = MutableStateFlow(OverlayTimerState())
  val state: StateFlow<OverlayTimerState> = _state.asStateFlow()

  fun toggleStopwatch(nowMs: Long = System.currentTimeMillis()) {
    val cur = _state.value
    _state.value = if (cur.stopwatchRunning) {
      val total = cur.stopwatchElapsedMs + (nowMs - cur.stopwatchStartedBaseMs).coerceAtLeast(0L)
      cur.copy(stopwatchRunning = false, stopwatchElapsedMs = total)
    } else {
      cur.copy(stopwatchRunning = true, stopwatchStartedBaseMs = nowMs)
    }
  }

  fun resetStopwatch() {
    _state.value = _state.value.copy(stopwatchRunning = false, stopwatchElapsedMs = 0L, stopwatchStartedBaseMs = 0L)
  }

  fun setCountdownPreset(durationMs: Long) {
    val safe = durationMs.coerceIn(5_000L, 3_600_000L)
    _state.value = _state.value.copy(
      countdownTotalMs = safe,
      countdownRemainingMs = safe,
      countdownRunning = false,
      countdownCompletedAlert = false,
    )
  }

  fun toggleCountdown(nowMs: Long = System.currentTimeMillis()) {
    val cur = _state.value
    if (cur.countdownRunning) {
      val rem = (cur.countdownEndWallMs - nowMs).coerceAtLeast(0L)
      _state.value = cur.copy(countdownRunning = false, countdownRemainingMs = rem)
    } else {
      val rem = if (cur.countdownRemainingMs <= 0L) cur.countdownTotalMs else cur.countdownRemainingMs
      _state.value = cur.copy(
        countdownRunning = true,
        countdownRemainingMs = rem,
        countdownEndWallMs = nowMs + rem,
        countdownCompletedAlert = false,
      )
    }
  }

  fun resetCountdown() {
    val cur = _state.value
    _state.value = cur.copy(
      countdownRunning = false,
      countdownRemainingMs = cur.countdownTotalMs,
      countdownCompletedAlert = false,
    )
  }

  fun currentStopwatchMs(nowMs: Long = System.currentTimeMillis()): Long {
    val cur = _state.value
    return if (cur.stopwatchRunning) {
      cur.stopwatchElapsedMs + (nowMs - cur.stopwatchStartedBaseMs).coerceAtLeast(0L)
    } else cur.stopwatchElapsedMs
  }

  fun currentCountdownMs(nowMs: Long = System.currentTimeMillis()): Long {
    val cur = _state.value
    if (!cur.countdownRunning) return cur.countdownRemainingMs
    val rem = (cur.countdownEndWallMs - nowMs).coerceAtLeast(0L)
    if (rem == 0L) {
      _state.value = cur.copy(
        countdownRunning = false,
        countdownRemainingMs = 0L,
        countdownCompletedAlert = true,
      )
    }
    return rem
  }

  fun formatClockMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val tenths = (ms % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", min, sec, tenths)
  }
}
