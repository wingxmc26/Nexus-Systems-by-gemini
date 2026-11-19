package com.example.data.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.example.data.model.BatteryTelemetry
import com.example.data.model.MetricValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryDataSource(private val context: Context) {

  fun observeBattery(pollIntervalMs: Long = 2500L): Flow<BatteryTelemetry> = callbackFlow {
    fun emitSnapshot(intent: Intent?) {
      trySend(readBatterySnapshot(intent))
    }

    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val stickyIntent = context.registerReceiver(null, filter)
    emitSnapshot(stickyIntent)

    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        emitSnapshot(intent)
      }
    }
    context.registerReceiver(receiver, filter)

    val pollJob = launch(Dispatchers.IO) {
      while (isActive) {
        delay(pollIntervalMs)
        val latest = context.registerReceiver(null, filter)
        emitSnapshot(latest)
      }
    }

    awaitClose {
      pollJob.cancel()
      runCatching { context.unregisterReceiver(receiver) }
    }
  }.flowOn(Dispatchers.IO)

  fun readBatterySnapshot(intentOverride: Intent? = null): BatteryTelemetry {
    val intent = intentOverride ?: runCatching {
      context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()

    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percentage: MetricValue<Int> = when {
      level >= 0 && scale > 0 -> MetricValue.Available(((level * 100f) / scale).toInt().coerceIn(0, 100))
      else -> {
        val cap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: Int.MIN_VALUE
        if (cap in 0..100) MetricValue.Available(cap)
        else MetricValue.Unavailable("Battery level not exposed")
      }
    }

    val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
      ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val statusLabel = when (statusInt) {
      BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
      BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
      BatteryManager.BATTERY_STATUS_FULL -> "Full"
      BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
      else -> "Unknown"
    }

    val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val chargeSource = when {
      pluggedInt == BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
      pluggedInt == BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
      pluggedInt == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        pluggedInt == BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
      pluggedInt == 0 -> "Battery"
      else -> "External Source"
    }

    val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
      statusInt == BatteryManager.BATTERY_STATUS_FULL ||
      pluggedInt != 0

    val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
    val tempMetric = if (rawTemp != Int.MIN_VALUE && rawTemp > -500 && rawTemp < 1500) {
      MetricValue.Available(rawTemp / 10.0f)
    } else {
      MetricValue.Unavailable("Thermal sensor unavailable")
    }

    val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
    val voltageMetric = if (rawVoltage > 0) {
      val volts = if (rawVoltage > 100) rawVoltage / 1000.0f else rawVoltage.toFloat()
      MetricValue.Available(volts)
    } else {
      MetricValue.Unavailable("Voltage unavailable")
    }

    val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.trim().orEmpty()
    val techMetric = if (tech.isNotEmpty()) {
      MetricValue.Available(tech)
    } else {
      MetricValue.Unavailable("Technology not reported")
    }

    val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
      ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
    val healthLabel = when (healthInt) {
      BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
      BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
      BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
      BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
      BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
      BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
      else -> "Unavailable"
    }

    val currentNow = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
    val currentMetric = if (currentNow != Int.MIN_VALUE && currentNow != 0) {
      MetricValue.Available(currentNow)
    } else {
      MetricValue.Unavailable("Current draw not exposed")
    }

    val chargeCounter = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Long.MIN_VALUE
    val chargeCounterMetric = if (chargeCounter != Long.MIN_VALUE && chargeCounter > 0L) {
      MetricValue.Available(chargeCounter)
    } else {
      MetricValue.Unavailable("Charge counter not exposed")
    }

    val energyCounter = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) ?: Long.MIN_VALUE
    val energyMetric = if (energyCounter != Long.MIN_VALUE && energyCounter > 0L) {
      MetricValue.Available(energyCounter)
    } else {
      MetricValue.Unavailable("Energy counter not exposed")
    }

    val cycleMetric: MetricValue<Int> = if (Build.VERSION.SDK_INT >= 34 && intent != null) {
      val cycles = intent.getIntExtra("android.os.extra.CYCLE_COUNT", -1)
      if (cycles >= 0) MetricValue.Available(cycles)
      else MetricValue.Unavailable("Not exposed by device")
    } else {
      MetricValue.Unavailable("Requires Android 14+ & hardware support")
    }

    return BatteryTelemetry(
      percentage = percentage,
      statusLabel = statusLabel,
      isCharging = isCharging,
      chargeSource = chargeSource,
      temperatureCelsius = tempMetric,
      voltageVolts = voltageMetric,
      technology = techMetric,
      healthLabel = healthLabel,
      currentMicroAmps = currentMetric,
      chargeCounterMicroAh = chargeCounterMetric,
      energyCounterNwh = energyMetric,
      cycleCount = cycleMetric,
      timestampMs = System.currentTimeMillis(),
    )
  }
}
