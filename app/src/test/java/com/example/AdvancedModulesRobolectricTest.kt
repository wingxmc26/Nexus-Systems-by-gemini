package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.example.core.NexusActionSpec
import com.example.data.local.GestureProfileEntity
import com.example.data.local.NexusLockType
import com.example.data.local.QuickNoteEntity
import com.example.data.local.SecuritySettingsConfig
import com.example.data.local.SharedShortcutEntity
import com.example.di.NexusContainer
import com.example.gesture.ActivationZone
import com.example.gesture.GestureClassifier
import com.example.gesture.GestureType
import com.example.overlay.CalculatorEvaluator
import com.example.overlay.OverlaySurfaceFilter
import com.example.overlay.OverlayTimerCoordinator
import com.example.security.AppLockMonitorCoordinator
import com.example.security.NexusSecurityManager
import com.example.security.VaultCryptoEngine
import com.example.security.VaultFramedMediaDataSource
import com.example.security.VaultKeyProvider
import com.example.security.VaultRepository
import com.example.widget.NexusWidgetJsBridge
import com.example.widget.WidgetSizePreset
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdvancedModulesRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  private class InMemoryTestVaultKeyProvider : VaultKeyProvider {
    private val key: SecretKey by lazy {
      KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
    override val isHardwareKeystoreBacked: Boolean = false
    override fun getOrCreateMasterKey(): SecretKey = key
  }

  // 1. GESTURE STUDIO
  @Test
  fun `gesture classifier classifies swipes with sensitivity & edge activation zones`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dao = NexusContainer.get(context).database.gestureStudioDao()

    assertEquals(GestureType.SWIPE_UP, GestureClassifier.classifySwipe(10f, -200f, 1, 1.0f, 2.0f))
    assertEquals(GestureType.SWIPE_RIGHT, GestureClassifier.classifySwipe(240f, 15f, 1, 1.0f, 2.0f))
    assertEquals(GestureType.TWO_FINGER_SWIPE_DOWN, GestureClassifier.classifySwipe(5f, 190f, 2, 1.0f, 2.0f))
    assertNull(GestureClassifier.classifySwipe(8f, 8f, 1, 1.0f, 2.0f))

    assertTrue(GestureClassifier.matchesZone(ActivationZone.LEFT_EDGE, 18f, 300f, 1080f, 2400f, edgeWidthPx = 48f))
    assertFalse(GestureClassifier.matchesZone(ActivationZone.LEFT_EDGE, 400f, 300f, 1080f, 2400f, edgeWidthPx = 48f))
    assertTrue(GestureClassifier.matchesZone(ActivationZone.RIGHT_EDGE, 1055f, 300f, 1080f, 2400f, edgeWidthPx = 48f))
    assertTrue(GestureClassifier.matchesZone(ActivationZone.BOTTOM_ZONE, 500f, 2370f, 1080f, 2400f, edgeWidthPx = 48f))

    val pid = dao.insertProfile(GestureProfileEntity(name = "Unit Profile", isActive = false))
    assertTrue(dao.getProfiles().any { it.id == pid })
    dao.deleteProfile(pid)
  }

  // 2 & 3. FLOATING TOOLS + EDGE PANEL SHARED CORE
  @Test
  fun `calculator evaluator, timer coordinator & shared shortcut repo execute accurately`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val container = NexusContainer.get(context)

    assertEquals(14.0, CalculatorEvaluator.evaluate("2 + 3 * 4").getOrThrow(), 0.0001)
    assertEquals(20.0, CalculatorEvaluator.evaluate("(2 + 3) * 4").getOrThrow(), 0.0001)
    assertEquals(-7.5, CalculatorEvaluator.evaluate("-15 / 2").getOrThrow(), 0.0001)
    assertTrue(CalculatorEvaluator.evaluate("10 / 0").isFailure)

    OverlayTimerCoordinator.resetStopwatch()
    OverlayTimerCoordinator.toggleStopwatch(nowMs = 1_000L)
    assertEquals(2_500L, OverlayTimerCoordinator.currentStopwatchMs(nowMs = 3_500L))
    OverlayTimerCoordinator.toggleStopwatch(nowMs = 3_500L)
    assertEquals("00:02.5", OverlayTimerCoordinator.formatClockMs(OverlayTimerCoordinator.currentStopwatchMs(4_000L)))

    OverlayTimerCoordinator.setCountdownPreset(5_000L)
    OverlayTimerCoordinator.toggleCountdown(nowMs = 10_000L)
    assertEquals(3_000L, OverlayTimerCoordinator.currentCountdownMs(nowMs = 12_000L))
    assertEquals(0L, OverlayTimerCoordinator.currentCountdownMs(nowMs = 16_000L))
    assertTrue(OverlayTimerCoordinator.state.value.countdownCompletedAlert)

    val noteId = container.database.quickNoteDao().upsert(QuickNoteEntity(title = "Note", content = "Offline Check"))
    assertTrue(noteId > 0L)
    assertTrue(container.database.quickNoteDao().observeAll().first().any { it.content == "Offline Check" })
    container.database.quickNoteDao().deleteById(noteId)

    val scId = container.database.sharedShortcutDao().insert(
      SharedShortcutEntity(
        label = "Edge Shortcut Test",
        category = "Combos",
        isFavorite = true,
        actionsJson = NexusActionSpec.serializeList(emptyList()),
      )
    )
    assertTrue(container.database.sharedShortcutDao().getAll().any { it.id == scId })
    container.database.sharedShortcutDao().deleteById(scId)

    // Verify consistent surface filter parity between UI & Services
    assertTrue(OverlaySurfaceFilter.matchesFloatingTools("BOTH"))
    assertTrue(OverlaySurfaceFilter.matchesFloatingTools("FLOATING_TOOLS"))
    assertFalse(OverlaySurfaceFilter.matchesFloatingTools("EDGE_PANEL"))
    assertTrue(OverlaySurfaceFilter.matchesEdgePanel("BOTH"))
    assertTrue(OverlaySurfaceFilter.matchesEdgePanel("EDGE_PANEL"))
    assertFalse(OverlaySurfaceFilter.matchesEdgePanel("FLOATING_TOOLS"))
  }

  // 4. SECURE VAULT (AES-256-GCM FRAMED CRYPTO, SEEKABLE STREAM, TAMPER CHECK & BACKUP)
  @Test
  fun `vault crypto engine encrypts frames, detects tampering, streams RAM seeks & restores backup`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val container = NexusContainer.get(context)
    val crypto = VaultCryptoEngine(InMemoryTestVaultKeyProvider())
    val vaultRepo = VaultRepository(context, container.database.vaultDao(), crypto)

    val tempDir = File(context.cacheDir, "vault_test_${System.currentTimeMillis()}").apply { mkdirs() }
    try {
      val payload = ByteArray(150_000) { i -> ((i * 31 + 7) and 0xFF).toByte() }
      val encFile = File(tempDir, "sample.nxsv")
      val summary = crypto.encryptStreamToFile(ByteArrayInputStream(payload), encFile, chunkSize = 32_768)
      assertEquals(payload.size.toLong(), summary.originalSizeBytes)

      val out = ByteArrayOutputStream()
      crypto.decryptFileToStream(encFile, out, summary.sha256Hex)
      assertArrayEquals(payload, out.toByteArray())

      val ds = VaultFramedMediaDataSource(encFile, crypto)
      val seekOffset = 31_000L
      val openedLen = ds.open(DataSpec.Builder().setUri(Uri.fromFile(encFile)).setPosition(seekOffset).setLength(4000).build())
      assertEquals(4000L, openedLen)
      val readSlice = ByteArray(4000)
      var total = 0
      while (total < 4000) {
        val n = ds.read(readSlice, total, 4000 - total)
        if (n <= 0) break
        total += n
      }
      ds.close()
      assertEquals(4000, total)
      assertArrayEquals(payload.copyOfRange(31_000, 35_000), readSlice)

      val backupStream = ByteArrayOutputStream()
      crypto.exportEncryptedBackup("strong-pass-123", """{"folders":[],"items":[]}""", listOf("item1.nxsv" to encFile), backupStream)
      val restored = crypto.decryptBackupArchive("strong-pass-123", ByteArrayInputStream(backupStream.toByteArray()))
      assertArrayEquals(payload, restored.decryptedEntries["item1.nxsv"])
      assertTrue(runCatching {
        crypto.decryptBackupArchive("wrong-pass", ByteArrayInputStream(backupStream.toByteArray()))
      }.isFailure)

      val bytes = encFile.readBytes()
      bytes[bytes.size - 10] = (bytes[bytes.size - 10].toInt() xor 0xFF).toByte()
      encFile.writeBytes(bytes)
      assertTrue(runCatching { crypto.decryptFileToBytes(encFile, summary.sha256Hex) }.isFailure)

      val blob = crypto.encryptSmallBlob("secret-manifest".toByteArray())
      assertEquals("secret-manifest", crypto.decryptSmallBlob(blob).toString(Charsets.UTF_8))
      assertNotNull(vaultRepo)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  // 5. MULTI-MODE SECURITY (PATTERN, PIN, PASSWORD, RELOCK & GUARD TRANSITIONS)
  @Test
  fun `security manager enforces Pattern PIN Password verification and external relock on transition`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val container = NexusContainer.get(context)
    val prefs = container.advancedPreferences
    val appLockDao = container.database.appLockDao()

    AppLockMonitorCoordinator.ensureDefaultProfiles(context)
    NexusSecurityManager.lockAllImmediately()

    // PIN mode checks
    assertTrue(NexusSecurityManager.configureCredential(NexusLockType.PIN, "123", prefs, appLockDao).isFailure)
    assertTrue(NexusSecurityManager.configureCredential(NexusLockType.PIN, "2580", prefs, appLockDao).isSuccess)
    assertFalse(
      NexusSecurityManager.verifyCredential(
        secret = "0000",
        targetType = "APP_LOCK_EXTERNAL",
        targetName = "Target",
        prefs = prefs,
        appLockDao = appLockDao,
      ).success
    )
    assertTrue(
      NexusSecurityManager.verifyCredential(
        secret = "2580",
        targetType = "VAULT",
        targetName = "Secure Vault",
        prefs = prefs,
        appLockDao = appLockDao,
        unlockVaultOnSuccess = true,
        nexusRouteToUnlock = "battery",
      ).success
    )
    assertTrue(NexusSecurityManager.vaultUnlocked.value)
    assertTrue(NexusSecurityManager.isNexusRouteUnlocked("battery", 30_000L))

    // Pattern mode checks
    assertTrue(NexusSecurityManager.configureCredential(NexusLockType.PATTERN, "0-1", prefs, appLockDao).isFailure)
    assertTrue(NexusSecurityManager.configureCredential(NexusLockType.PATTERN, "0-1-2-5-8", prefs, appLockDao).isSuccess)
    assertFalse(
      NexusSecurityManager.verifyCredential(
        secret = "0-1-2-4-6",
        targetType = "APP_LOCK_EXTERNAL",
        targetName = "Target",
        prefs = prefs,
        appLockDao = appLockDao,
      ).success
    )
    assertTrue(
      NexusSecurityManager.verifyCredential(
        secret = "0-1-2-5-8",
        targetType = "APP_LOCK_EXTERNAL",
        targetName = "Target",
        prefs = prefs,
        appLockDao = appLockDao,
        externalPackageToUnlock = "com.test.target",
      ).success
    )
    assertTrue(NexusSecurityManager.isExternalPackageUnlocked("com.test.target", 0L))
    NexusSecurityManager.onForegroundPackageTransition("com.other.app")
    assertFalse(NexusSecurityManager.isExternalPackageUnlocked("com.test.target", 0L))

    // Password mode checks
    assertTrue(NexusSecurityManager.configureCredential(NexusLockType.PASSWORD, "abc", prefs, appLockDao).isFailure)
    assertTrue(NexusSecurityManager.configureCredential(NexusLockType.PASSWORD, "Nexus@2026", prefs, appLockDao).isSuccess)
    assertFalse(
      NexusSecurityManager.verifyCredential(
        secret = "WrongPassword",
        targetType = "APP_LOCK_INTERNAL",
        targetName = "Battery",
        prefs = prefs,
        appLockDao = appLockDao,
      ).success
    )
    assertTrue(
      NexusSecurityManager.verifyCredential(
        secret = "Nexus@2026",
        targetType = "APP_LOCK_INTERNAL",
        targetName = "Battery",
        prefs = prefs,
        appLockDao = appLockDao,
      ).success
    )

    // Delay <= 0L forces immediate relock on internal routes
    assertFalse(NexusSecurityManager.isNexusRouteUnlocked("battery", 0L))

    // Screen-off behavior
    NexusSecurityManager.onScreenOff(
      config = SecuritySettingsConfig(vaultLockOnScreenOff = true),
      appLockOnScreenOff = true,
    )
    assertFalse(NexusSecurityManager.vaultUnlocked.value)
    assertFalse(NexusSecurityManager.isNexusRouteUnlocked("battery", 30_000L))
    assertTrue(appLockDao.observeAuthHistory().first().isNotEmpty())
  }

  // 6. WIDGET STUDIO FULL HTML5 DOCUMENT COMPOSITION & AUTO-DP
  @Test
  fun `widget html composer preserves full html5 documents without nesting and injects auto-dp vars`() {
    val fullHtmlDoc = """
      <!DOCTYPE html>
      <html>
        <head><title>Custom</title></head>
        <body><div id="clock">12:00</div></body>
      </html>
    """.trimIndent()

    val composed = NexusWidgetJsBridge.composeHtmlDocument(
      rawHtml = fullHtmlDoc,
      rawCss = ".clock { color: #fff; /* $10 */ }",
      rawJs = "console.log('ready');",
      widthDp = 328,
      heightDp = 160,
      transparentBg = true,
      isAutoSize = true,
    )

    assertEquals(1, Regex("<html", RegexOption.IGNORE_CASE).findAll(composed).count())
    assertEquals(1, Regex("<body", RegexOption.IGNORE_CASE).findAll(composed).count())
    assertTrue(composed.contains("--nexus-width-dp: 328px"))
    assertTrue(composed.contains("window.NexusRuntime"))
    assertTrue(composed.contains("width=device-width"))
    assertTrue(composed.contains(".clock { color: #fff; /* $10 */ }"))
    assertEquals(WidgetSizePreset.AUTO, WidgetSizePreset.fromName("AUTO"))
  }

  // 7. COMPOSE CUJ NAVIGATION FOR KEPT MODULES & TOP-LEVEL APP LOCK
  @Test
  fun `utilities hub and top-level app lock navigate cleanly without crashes`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_applock").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("app_lock_list").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_utilities").performClick()
    composeTestRule.waitForIdle()

    val targets = listOf(
      Triple("hub_gesture_studio", "gesture_studio_list", "back_button"),
      Triple("hub_floating_tools", "floating_tools_list", "back_button"),
      Triple("hub_edge_panel", "edge_panel_list", "back_button"),
      Triple("hub_secure_vault", "vault_gate_card", "vault_gate_back_button"),
    )

    targets.forEach { (hubTag, screenTag, backTag) ->
      try {
        composeTestRule.onNodeWithTag("utilities_hub_list").performScrollToNode(hasTestTag(hubTag))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(hubTag).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(screenTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(backTag).performClick()
        composeTestRule.waitForIdle()
      } catch (t: Throwable) {
        throw AssertionError("Failed navigating $hubTag -> $screenTag (back=$backTag): ${t.message}", t)
      }
    }

    // App Lock card from Utilities Hub navigates cleanly as well
    composeTestRule.onNodeWithTag("utilities_hub_list").performScrollToNode(hasTestTag("hub_app_lock"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("hub_app_lock").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("app_lock_list").assertIsDisplayed()
  }
}
