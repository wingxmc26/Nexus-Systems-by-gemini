package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.system.BatteryDataSource
import com.example.data.system.DisplayDataSource
import com.example.data.system.ShortcutsDataSource
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `app name matches Nexus System`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nexus System", appName)
  }

  @Test
  fun `formatters produce accurate units`() {
    assertEquals("1.00 KB", Formatters.formatBytes(1024L))
    assertEquals("1h 1m 1s", Formatters.formatDuration(3661_000L))
    assertEquals("2.40 GHz", Formatters.formatFrequencyKhz(2_400_000L))
  }

  @Test
  fun `data sources read safely without crashing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val battery = BatteryDataSource(context).readBatterySnapshot()
    assertTrue(battery.statusLabel.isNotBlank())

    val display = DisplayDataSource(context).readDisplaySpecs()
    assertTrue(display.physicalWidthPx > 0)

    val shortcuts = ShortcutsDataSource(context).loadShortcuts()
    assertTrue(shortcuts.isNotEmpty())
  }

  @Test
  fun `main navigation tabs switch without crashes`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_dashboard").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_battery").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("battery_hero_card").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_utilities").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("hub_storage").assertIsDisplayed()
  }
}
