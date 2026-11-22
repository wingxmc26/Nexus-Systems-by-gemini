package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.local.NexusLockType
import com.example.data.system.PermissionManager
import com.example.ui.theme.NexusThemeExtras
import kotlin.math.hypot

@Composable
fun PatternLockGrid(
  selectedNodes: List<Int>,
  onNodesChanged: (List<Int>) -> Unit,
  onPatternCompleted: (List<Int>) -> Unit,
  modifier: Modifier = Modifier,
  testTag: String = "pattern_lock_grid",
) {
  val colors = NexusThemeExtras.colors
  var dragPoint by remember { mutableStateOf<Offset?>(null) }
  val activeNodes = remember { mutableStateListOf<Int>() }

  // Sync external clears
  if (selectedNodes.isEmpty() && activeNodes.isNotEmpty() && dragPoint == null) {
    activeNodes.clear()
  }

  fun nodeCenter(index: Int, width: Float, height: Float): Offset {
    val row = index / 3
    val col = index % 3
    val cellW = width / 3f
    val cellH = height / 3f
    return Offset(col * cellW + cellW / 2f, row * cellH + cellH / 2f)
  }

  fun hitTestNode(pos: Offset, width: Float, height: Float): Int? {
    val hitRadius = minOf(width, height) / 6.2f
    for (idx in 0..8) {
      val center = nodeCenter(idx, width, height)
      if (hypot(pos.x - center.x, pos.y - center.y) <= hitRadius) {
        return idx
      }
    }
    return null
  }

  Canvas(
    modifier = modifier
      .size(228.dp)
      .testTag(testTag)
      .pointerInput(Unit) {
        detectTapGestures { offset ->
          val w = size.width.toFloat()
          val h = size.height.toFloat()
          val hit = hitTestNode(offset, w, h) ?: return@detectTapGestures
          if (hit !in selectedNodes) {
            val next = selectedNodes + hit
            onNodesChanged(next)
          }
        }
      }
      .pointerInput(Unit) {
        detectDragGestures(
          onDragStart = { start ->
            activeNodes.clear()
            dragPoint = start
            val hit = hitTestNode(start, size.width.toFloat(), size.height.toFloat())
            if (hit != null) {
              activeNodes.add(hit)
              onNodesChanged(activeNodes.toList())
            }
          },
          onDrag = { change, _ ->
            change.consume()
            dragPoint = change.position
            val hit = hitTestNode(change.position, size.width.toFloat(), size.height.toFloat())
            if (hit != null && hit !in activeNodes) {
              activeNodes.add(hit)
              onNodesChanged(activeNodes.toList())
            }
          },
          onDragEnd = {
            dragPoint = null
            val result = activeNodes.toList()
            if (result.isNotEmpty()) {
              onPatternCompleted(result)
            }
          },
          onDragCancel = {
            dragPoint = null
          },
        )
      },
  ) {
    val w = size.width
    val h = size.height
    val lineColor = colors.accent.copy(alpha = 0.78f)
    val nodesToDraw = if (activeNodes.isNotEmpty()) activeNodes.toList() else selectedNodes

    if (nodesToDraw.size >= 2) {
      for (i in 0 until nodesToDraw.lastIndex) {
        drawLine(
          color = lineColor,
          start = nodeCenter(nodesToDraw[i], w, h),
          end = nodeCenter(nodesToDraw[i + 1], w, h),
          strokeWidth = 9f,
          cap = StrokeCap.Round,
        )
      }
    }
    val tail = dragPoint
    if (tail != null && nodesToDraw.isNotEmpty()) {
      drawLine(
        color = lineColor.copy(alpha = 0.45f),
        start = nodeCenter(nodesToDraw.last(), w, h),
        end = tail,
        strokeWidth = 7f,
        cap = StrokeCap.Round,
      )
    }

    for (idx in 0..8) {
      val center = nodeCenter(idx, w, h)
      val selected = idx in nodesToDraw
      drawCircle(
        color = if (selected) colors.accent.copy(alpha = 0.22f) else colors.elevatedSurface,
        radius = if (selected) 26f else 18f,
        center = center,
      )
      drawCircle(
        color = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.65f),
        radius = if (selected) 11f else 8f,
        center = center,
      )
    }
  }
}

@Composable
fun NumericKeypadGrid(
  onDigit: (String) -> Unit,
  onClear: () -> Unit,
  onBackspace: () -> Unit,
  testTagPrefix: String,
) {
  val keypadRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
  )
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    keypadRows.forEach { row ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        row.forEach { digit ->
          OutlinedButton(
            onClick = { onDigit(digit) },
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.weight(1f).testTag("${testTagPrefix}_key_$digit"),
          ) {
            Text(digit, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
          }
        }
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      OutlinedButton(
        onClick = onClear,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.weight(1f).testTag("${testTagPrefix}_key_clr"),
      ) {
        Text("CLR")
      }
      OutlinedButton(
        onClick = { onDigit("0") },
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.weight(1f).testTag("${testTagPrefix}_key_0"),
      ) {
        Text("0", style = MaterialTheme.typography.titleMedium)
      }
      OutlinedButton(
        onClick = onBackspace,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.weight(1f).testTag("${testTagPrefix}_key_back"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", modifier = Modifier.size(18.dp))
      }
    }
  }
}

@Composable
fun SecurityLockGatePane(
  title: String,
  subtitle: String,
  hasPinConfigured: Boolean,
  activeLockType: NexusLockType = NexusLockType.PIN,
  biometricEnabled: Boolean,
  failedAttempts: Int = 0,
  lockoutUntilMs: Long = 0L,
  externalErrorMessage: String? = null,
  onSetupPin: (String) -> Unit,
  onSetupCredential: ((NexusLockType, String) -> Unit)? = null,
  onSubmitPin: (String) -> Unit,
  onRequestBiometric: (FragmentActivity) -> Unit,
  onBack: (() -> Unit)? = null,
  testTagPrefix: String = "security_gate",
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val activity = context as? FragmentActivity
  val canUseBiometrics = remember(context, biometricEnabled) {
    biometricEnabled && PermissionManager(context).canUseBiometrics()
  }

  var setupType by remember { mutableStateOf(activeLockType) }
  var pinInput by remember(activeLockType) { mutableStateOf("") }
  var confirmPinInput by remember(activeLockType) { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var patternNodes by remember(activeLockType) { mutableStateOf<List<Int>>(emptyList()) }
  var confirmPatternNodes by remember(activeLockType) { mutableStateOf<List<Int>>(emptyList()) }
  var patternConfirmStage by remember { mutableStateOf(false) }
  var localError by remember { mutableStateOf<String?>(null) }

  val now = System.currentTimeMillis()
  val lockedOutSec = if (lockoutUntilMs > now) ((lockoutUntilMs - now) / 1000L).toInt().coerceAtLeast(1) else 0
  val effectiveError = externalErrorMessage ?: localError

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background)
      .verticalScroll(rememberScrollState())
      .padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    NexusGlassCard(
      modifier = Modifier.widthIn(max = 440.dp),
      accentHighlight = true,
      testTag = "${testTagPrefix}_card",
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = if (hasPinConfigured) Icons.Default.Lock else Icons.Default.Shield,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(28.dp),
          )
          Spacer(Modifier.width(10.dp))
          Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
          }
        }
        StatusPill(
          text = if (hasPinConfigured) activeLockType.label.uppercase() else "SETUP",
          color = if (hasPinConfigured) colors.accent else colors.warning,
        )
      }

      Spacer(Modifier.height(14.dp))

      if (!hasPinConfigured) {
        Text(
          "Choose Pattern, Numeric PIN, or Password for Secure Vault and App Lock. Salted PBKDF2-SHA256 verification protects access.",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NexusLockType.entries.forEach { lt ->
            FilterChip(
              selected = setupType == lt,
              onClick = {
                setupType = lt
                pinInput = ""
                confirmPinInput = ""
                patternNodes = emptyList()
                confirmPatternNodes = emptyList()
                patternConfirmStage = false
                localError = null
              },
              label = { Text(lt.label) },
              modifier = Modifier.testTag("${testTagPrefix}_setup_type_${lt.name.lowercase()}"),
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        when (setupType) {
          NexusLockType.PIN -> {
            OutlinedTextField(
              value = pinInput,
              onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
              label = { Text("Enter 4–8 digit PIN") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_setup_pin_input"),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = confirmPinInput,
              onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPinInput = it },
              label = { Text("Confirm PIN") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_setup_confirm_input"),
            )
          }
          NexusLockType.PASSWORD -> {
            OutlinedTextField(
              value = pinInput,
              onValueChange = { if (it.length <= 64) pinInput = it },
              label = { Text("Enter Password (4+ chars)") },
              singleLine = true,
              visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                  Icon(
                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle password visibility",
                  )
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_setup_password_input"),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = confirmPinInput,
              onValueChange = { if (it.length <= 64) confirmPinInput = it },
              label = { Text("Confirm Password") },
              singleLine = true,
              visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_setup_confirm_input"),
            )
          }
          NexusLockType.PATTERN -> {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                if (!patternConfirmStage) "Draw unlock pattern (connect 4+ dots)" else "Draw pattern again to confirm",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
              Spacer(Modifier.height(8.dp))
              PatternLockGrid(
                selectedNodes = if (!patternConfirmStage) patternNodes else confirmPatternNodes,
                onNodesChanged = { nodes ->
                  if (!patternConfirmStage) patternNodes = nodes else confirmPatternNodes = nodes
                },
                onPatternCompleted = { nodes ->
                  if (!patternConfirmStage) {
                    if (nodes.size < 4) {
                      localError = "Connect at least 4 dots."
                      patternNodes = emptyList()
                    } else {
                      localError = null
                      patternNodes = nodes
                      patternConfirmStage = true
                    }
                  } else {
                    confirmPatternNodes = nodes
                  }
                },
                testTag = "${testTagPrefix}_setup_pattern_grid",
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  onClick = {
                    patternNodes = emptyList()
                    confirmPatternNodes = emptyList()
                    patternConfirmStage = false
                    localError = null
                  },
                ) {
                  Text("Redraw")
                }
              }
            }
          }
        }
        if (!effectiveError.isNullOrBlank()) {
          Spacer(Modifier.height(8.dp))
          Text(effectiveError, style = MaterialTheme.typography.bodySmall, color = colors.critical)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          if (onBack != null) {
            OutlinedButton(
              onClick = onBack,
              modifier = Modifier.weight(1f).testTag("${testTagPrefix}_back_button"),
            ) {
              Text("Back")
            }
          }
          Button(
            onClick = {
              when (setupType) {
                NexusLockType.PIN -> when {
                  pinInput.length !in 4..8 -> localError = "PIN must be 4 to 8 digits."
                  pinInput != confirmPinInput -> localError = "PIN entries do not match."
                  else -> {
                    localError = null
                    onSetupCredential?.invoke(NexusLockType.PIN, pinInput) ?: onSetupPin(pinInput)
                  }
                }
                NexusLockType.PASSWORD -> when {
                  pinInput.length < 4 || pinInput.isBlank() -> localError = "Password must be at least 4 characters."
                  pinInput != confirmPinInput -> localError = "Passwords do not match."
                  else -> {
                    localError = null
                    onSetupCredential?.invoke(NexusLockType.PASSWORD, pinInput) ?: onSetupPin(pinInput)
                  }
                }
                NexusLockType.PATTERN -> when {
                  patternNodes.size < 4 -> localError = "Connect at least 4 dots."
                  confirmPatternNodes != patternNodes -> localError = "Patterns do not match."
                  else -> {
                    localError = null
                    val encoded = patternNodes.joinToString("-")
                    onSetupCredential?.invoke(NexusLockType.PATTERN, encoded) ?: onSetupPin(encoded)
                  }
                }
              }
            },
            modifier = Modifier.weight(1f).testTag("${testTagPrefix}_create_pin_button"),
          ) {
            Text("Save & Unlock")
          }
        }
      } else {
        when (activeLockType) {
          NexusLockType.PATTERN -> {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                "Draw your 3×3 unlock pattern",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
              Spacer(Modifier.height(8.dp))
              PatternLockGrid(
                selectedNodes = patternNodes,
                onNodesChanged = { patternNodes = it },
                onPatternCompleted = { nodes ->
                  if (nodes.size >= 4) {
                    localError = null
                    onSubmitPin(nodes.joinToString("-"))
                    patternNodes = emptyList()
                  } else {
                    localError = "Connect at least 4 dots."
                    patternNodes = emptyList()
                  }
                },
                testTag = "${testTagPrefix}_pattern_grid",
              )
            }
          }
          NexusLockType.PASSWORD -> {
            OutlinedTextField(
              value = pinInput,
              onValueChange = { if (it.length <= 64) pinInput = it },
              label = { Text("Enter Security Password") },
              singleLine = true,
              visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                  Icon(
                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle password visibility",
                  )
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_password_input"),
            )
          }
          NexusLockType.PIN -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val slots = maxOf(4, pinInput.length)
              for (idx in 0 until slots) {
                val filled = idx < pinInput.length
                Box(
                  modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .size(14.dp)
                    .background(
                      color = if (filled) colors.accent else colors.elevatedSurface,
                      shape = RoundedCornerShape(50),
                    ),
                )
              }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
              value = pinInput,
              onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
              label = { Text("Enter Security PIN") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_pin_input"),
            )

            Spacer(Modifier.height(10.dp))
            NumericKeypadGrid(
              onDigit = { if (pinInput.length < 8) pinInput += it },
              onClear = { pinInput = "" },
              onBackspace = { pinInput = pinInput.dropLast(1) },
              testTagPrefix = testTagPrefix,
            )
          }
        }

        if (lockedOutSec > 0) {
          Spacer(Modifier.height(8.dp))
          Text(
            "Brute-force cooldown active: wait ~${lockedOutSec}s before trying again.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.critical,
          )
        } else if (failedAttempts > 0) {
          Spacer(Modifier.height(8.dp))
          Text(
            "Failed attempts: $failedAttempts/5",
            style = MaterialTheme.typography.bodySmall,
            color = colors.warning,
          )
        }

        if (!effectiveError.isNullOrBlank()) {
          Spacer(Modifier.height(6.dp))
          Text(effectiveError, style = MaterialTheme.typography.bodySmall, color = colors.critical)
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (activeLockType == NexusLockType.PATTERN) {
            OutlinedButton(
              onClick = {
                patternNodes = emptyList()
                localError = null
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Clear")
            }
            Button(
              onClick = {
                if (patternNodes.size < 4) {
                  localError = "Connect at least 4 dots."
                } else {
                  localError = null
                  onSubmitPin(patternNodes.joinToString("-"))
                  patternNodes = emptyList()
                }
              },
              modifier = Modifier.weight(1f).testTag("${testTagPrefix}_unlock_button"),
            ) {
              Text("Unlock")
            }
          } else {
            Button(
              onClick = {
                val minLen = 4
                if (pinInput.length < minLen) {
                  localError = if (activeLockType == NexusLockType.PIN) "Enter at least 4 digits." else "Enter at least 4 characters."
                } else {
                  localError = null
                  onSubmitPin(pinInput)
                  pinInput = ""
                }
              },
              modifier = Modifier.weight(1f).testTag("${testTagPrefix}_unlock_button"),
            ) {
              Text("Unlock")
            }
          }
          if (canUseBiometrics && activity != null) {
            OutlinedButton(
              onClick = { onRequestBiometric(activity) },
              modifier = Modifier.weight(1f).testTag("${testTagPrefix}_biometric_button"),
            ) {
              Icon(Icons.Default.Fingerprint, contentDescription = "Biometric Unlock", modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Biometric")
            }
          }
        }
        if (onBack != null) {
          Spacer(Modifier.height(8.dp))
          OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_back_button"),
          ) {
            Text("Back")
          }
        }
      }
    }
  }
}

