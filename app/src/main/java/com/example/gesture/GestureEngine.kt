package com.example.gesture

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.example.core.NexusActionDispatcher
import com.example.core.NexusActionSpec
import com.example.core.NexusActionType
import com.example.data.local.GestureMappingEntity
import com.example.data.local.GestureProfileEntity
import com.example.data.local.GestureStudioDao
import com.example.ui.navigation.NexusRoutes
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.flow.Flow

enum class GestureType(val label: String) {
  SWIPE_UP("Swipe Up"),
  SWIPE_DOWN("Swipe Down"),
  SWIPE_LEFT("Swipe Left"),
  SWIPE_RIGHT("Swipe Right"),
  DOUBLE_TAP("Double Tap"),
  LONG_PRESS("Long Press"),
  TWO_FINGER_TAP("Two-Finger Tap"),
  TWO_FINGER_SWIPE_UP("Two-Finger Swipe Up"),
  TWO_FINGER_SWIPE_DOWN("Two-Finger Swipe Down"),
}

enum class ActivationZone(val label: String) {
  FULL_SCREEN("Full Canvas"),
  LEFT_EDGE("Left Edge"),
  RIGHT_EDGE("Right Edge"),
  BOTTOM_ZONE("Bottom Bar"),
  TOP_ZONE("Top Zone"),
}

object GestureClassifier {

  fun matchesZone(
    zone: ActivationZone,
    startX: Float,
    startY: Float,
    widthPx: Float,
    heightPx: Float,
    edgeWidthPx: Float,
  ): Boolean {
    if (widthPx <= 0f || heightPx <= 0f) return true
    val clampedEdge = edgeWidthPx.coerceIn(24f, widthPx * 0.45f)
    return when (zone) {
      ActivationZone.FULL_SCREEN -> true
      ActivationZone.LEFT_EDGE -> startX <= clampedEdge
      ActivationZone.RIGHT_EDGE -> startX >= (widthPx - clampedEdge)
      ActivationZone.TOP_ZONE -> startY <= clampedEdge.coerceAtLeast(heightPx * 0.16f)
      ActivationZone.BOTTOM_ZONE -> startY >= (heightPx - clampedEdge.coerceAtLeast(heightPx * 0.16f))
    }
  }

  fun classifySwipe(
    dx: Float,
    dy: Float,
    pointerCount: Int,
    sensitivity: Float,
    density: Float,
  ): GestureType? {
    val thresholdPx = (48f * density / sensitivity.coerceIn(0.4f, 2.0f)).coerceIn(20f, 180f)
    val absX = abs(dx)
    val absY = abs(dy)
    if (pointerCount >= 2) {
      if (absY >= thresholdPx && absY >= absX) {
        return if (dy < 0f) GestureType.TWO_FINGER_SWIPE_UP else GestureType.TWO_FINGER_SWIPE_DOWN
      }
      return null
    }
    if (absX < thresholdPx && absY < thresholdPx) return null
    return if (absX > absY) {
      if (dx > 0f) GestureType.SWIPE_RIGHT else GestureType.SWIPE_LEFT
    } else {
      if (dy < 0f) GestureType.SWIPE_UP else GestureType.SWIPE_DOWN
    }
  }
}

class GestureRepository(
  private val gestureDao: GestureStudioDao,
  private val actionDispatcher: NexusActionDispatcher,
) {
  val profiles: Flow<List<GestureProfileEntity>> = gestureDao.observeProfiles()

  fun observeMappings(profileId: Long): Flow<List<GestureMappingEntity>> = gestureDao.observeMappings(profileId)

  suspend fun ensureDefaultProfile(): GestureProfileEntity {
    val existing = gestureDao.getProfiles()
    val active = existing.firstOrNull { it.isActive } ?: existing.firstOrNull()
    if (active != null) {
      if (!active.isActive) gestureDao.activateProfile(active.id)
      return active.copy(isActive = true)
    }

    val defId = gestureDao.insertProfile(
      GestureProfileEntity(
        name = "Default Flow",
        isActive = true,
        sensitivity = 1.0f,
        inAppEnabled = true,
        systemOverlayEnabled = false,
        zoneWidthDp = 36,
      )
    )
    gestureDao.insertMapping(
      GestureMappingEntity(
        profileId = defId,
        gestureType = GestureType.SWIPE_RIGHT.name,
        activationZone = ActivationZone.LEFT_EDGE.name,
        actionsJson = NexusActionSpec.serializeList(
          listOf(NexusActionSpec(NexusActionType.OPEN_NEXUS_MODULE, "Open Battery", NexusRoutes.BATTERY))
        ),
        label = "Edge Swipe -> Battery Monitor",
      )
    )
    gestureDao.insertMapping(
      GestureMappingEntity(
        profileId = defId,
        gestureType = GestureType.TWO_FINGER_TAP.name,
        activationZone = ActivationZone.FULL_SCREEN.name,
        actionsJson = NexusActionSpec.serializeList(
          listOf(NexusActionSpec(NexusActionType.MEDIA_PLAY_PAUSE, "Toggle Media Play/Pause"))
        ),
        label = "Two-Finger Tap -> Play / Pause Media",
      )
    )
    gestureDao.insertMapping(
      GestureMappingEntity(
        profileId = defId,
        gestureType = GestureType.DOUBLE_TAP.name,
        activationZone = ActivationZone.FULL_SCREEN.name,
        actionsJson = NexusActionSpec.serializeList(
          listOf(NexusActionSpec(NexusActionType.HAPTIC_PULSE, "Haptic Pulse", "50"))
        ),
        label = "Double Tap -> Haptic Pulse",
      )
    )
    return gestureDao.getProfiles().first()
  }

  suspend fun dispatchDetectedGesture(
    context: Context,
    detected: GestureType,
    startX: Float,
    startY: Float,
    widthPx: Float,
    heightPx: Float,
  ): String? {
    val profile = gestureDao.getActiveProfile() ?: return null
    val mappings = gestureDao.getEnabledMappings(profile.id)
    val density = context.resources.displayMetrics.density
    val edgeWidthPx = profile.zoneWidthDp * density

    // Prefer specific zone mapping over FULL_SCREEN fallback if both match
    val candidates = mappings.filter { it.gestureType == detected.name }
      .filter { mapping ->
        val z = runCatching { ActivationZone.valueOf(mapping.activationZone) }
          .getOrDefault(ActivationZone.FULL_SCREEN)
        GestureClassifier.matchesZone(z, startX, startY, widthPx, heightPx, edgeWidthPx)
      }
      .sortedByDescending { it.activationZone != ActivationZone.FULL_SCREEN.name }

    val chosen = candidates.firstOrNull() ?: return null
    val actions = NexusActionSpec.parseList(chosen.actionsJson)
    if (actions.isEmpty()) return null
    actionDispatcher.dispatchAll(
      actions = actions,
      sourceType = "GESTURE",
      sourceId = chosen.id,
      sourceName = "${detected.label}: ${chosen.label}",
      branch = chosen.activationZone,
    )
    return "${detected.label} -> ${chosen.label}"
  }
}

/**
 * Interactive Compose multi-touch gesture recognizer supporting single swipe (4 directions),
 * double tap, long press, two-finger tap, and two-finger vertical swipes.
 */
fun Modifier.nexusGesturePad(
  sensitivity: Float,
  density: Float,
  pass: PointerEventPass = PointerEventPass.Main,
  onGestureRecognized: (type: GestureType, startX: Float, startY: Float, widthPx: Float, heightPx: Float) -> Unit,
): Modifier = this.pointerInput(sensitivity, density) {
  var lastTapTimeMs = 0L
  var lastTapPos = Offset.Zero
  val tapSlopPx = 24f * density

  awaitEachGesture {
    val firstDown = awaitFirstDown(requireUnconsumed = false, pass = pass)
    val downTime = System.currentTimeMillis()
    val startPos = firstDown.position
    var maxPointers = 1
    var secondStartPos: Offset? = null
    var lastPos1 = startPos
    var lastPos2: Offset? = null
    var longPressFired = false

    while (true) {
      val event = awaitPointerEvent(pass = pass)
      val changes = event.changes
      if (changes.size > maxPointers) {
        maxPointers = changes.size
      }
      changes.getOrNull(0)?.let { lastPos1 = it.position }
      changes.getOrNull(1)?.let { c2 ->
        if (secondStartPos == null) secondStartPos = c2.position
        lastPos2 = c2.position
      }

      val now = System.currentTimeMillis()
      val moved1 = hypot((lastPos1.x - startPos.x).toDouble(), (lastPos1.y - startPos.y).toDouble()).toFloat()
      if (!longPressFired && maxPointers == 1 && (now - downTime) >= 520L && moved1 < tapSlopPx && changes.any { it.pressed }) {
        longPressFired = true
        onGestureRecognized(
          GestureType.LONG_PRESS,
          startPos.x,
          startPos.y,
          size.width.toFloat(),
          size.height.toFloat(),
        )
      }

      val allReleased = changes.all { !it.pressed || it.changedToUp() }
      if (allReleased) {
        if (!longPressFired) {
          val elapsed = now - downTime
          if (maxPointers >= 2) {
            val s2 = secondStartPos ?: startPos
            val l2 = lastPos2 ?: lastPos1
            val avgDx = ((lastPos1.x - startPos.x) + (l2.x - s2.x)) / 2f
            val avgDy = ((lastPos1.y - startPos.y) + (l2.y - s2.y)) / 2f
            val twoSwipe = GestureClassifier.classifySwipe(avgDx, avgDy, 2, sensitivity, density)
            if (twoSwipe != null) {
              onGestureRecognized(twoSwipe, startPos.x, startPos.y, size.width.toFloat(), size.height.toFloat())
            } else if (hypot(avgDx.toDouble(), avgDy.toDouble()) < tapSlopPx * 1.4 && elapsed <= 450L) {
              onGestureRecognized(GestureType.TWO_FINGER_TAP, startPos.x, startPos.y, size.width.toFloat(), size.height.toFloat())
            }
          } else {
            val dx = lastPos1.x - startPos.x
            val dy = lastPos1.y - startPos.y
            val swipe = GestureClassifier.classifySwipe(dx, dy, 1, sensitivity, density)
            if (swipe != null) {
              lastTapTimeMs = 0L
              onGestureRecognized(swipe, startPos.x, startPos.y, size.width.toFloat(), size.height.toFloat())
            } else if (moved1 < tapSlopPx && elapsed <= 320L) {
              val doubleDist = hypot((startPos.x - lastTapPos.x).toDouble(), (startPos.y - lastTapPos.y).toDouble())
              if (now - lastTapTimeMs <= 340L && doubleDist < tapSlopPx * 2.5) {
                lastTapTimeMs = 0L
                onGestureRecognized(GestureType.DOUBLE_TAP, startPos.x, startPos.y, size.width.toFloat(), size.height.toFloat())
              } else {
                lastTapTimeMs = now
                lastTapPos = startPos
              }
            }
          }
        }
        break
      }
    }
  }
}
