package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.MetricValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.NetworkUiState
import com.example.ui.viewmodel.NetworkViewModel
import com.example.util.Formatters

@Composable
fun NetworkScreen(
  viewModel: NetworkViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Network Monitor",
      subtitle = "OFFLINE INTERFACE & LINK DIAGNOSTICS",
      onBack = onBack,
      trailingContent = {
        IconButton(
          onClick = { viewModel.refresh() },
          modifier = Modifier.testTag("network_refresh_button"),
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh Network", tint = colors.textPrimary)
        }
      },
    )

    when (val s = state) {
      is NetworkUiState.Loading -> LoadingTelemetryState("Inspecting local network stack...")
      is NetworkUiState.Error -> {
        Column(modifier = Modifier.padding(16.dp)) {
          EmptyOrErrorState(
            icon = Icons.Default.ErrorOutline,
            title = "Network Diagnostics Error",
            description = s.message,
          )
        }
      }
      is NetworkUiState.Success -> {
        val net = s.data
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
          ),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          item {
            NexusGlassCard(accentHighlight = true, testTag = "network_status_card") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                SectionHeader(title = "Connection Link", icon = Icons.Default.Wifi)
              }
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                StatusPill(
                  text = if (net.isConnected) "LINK UP: ${net.transportLabel.uppercase()}" else "DISCONNECTED",
                  color = if (net.isConnected) colors.nominal else colors.critical,
                )
                StatusPill(
                  text = if (net.isMetered) "METERED" else "UNMETERED",
                  color = if (net.isMetered) colors.warning else colors.info,
                )
              }
              Spacer(modifier = Modifier.height(10.dp))
              MetricValueRow("Active Interface", net.interfaceName)
              MetricValueRow("Wi-Fi Standard", net.wifiStandard)
              MetricValueRow("Link Speed", net.wifiLinkSpeedMbps) { "$it Mbps" }
              MetricValueRow("Frequency", net.wifiFrequencyMhz) { "$it MHz" }
              MetricValueRow("Signal (RSSI)", net.wifiRssiDbm) { "$it dBm" }
            }
          }

          item {
            NexusGlassCard(testTag = "network_ip_card") {
              SectionHeader(title = "Local IP & Routing", icon = Icons.Default.Router)
              Spacer(modifier = Modifier.height(6.dp))
              MetricKeyValueRow(
                label = "Local IPv4",
                value = net.localIpv4Addresses.joinToString("\n").ifEmpty { "None assigned" },
              )
              MetricKeyValueRow(
                label = "Local IPv6",
                value = net.localIpv6Addresses.take(2).joinToString("\n").ifEmpty { "None assigned" },
              )
              MetricKeyValueRow(
                label = "DNS Servers",
                value = net.dnsServers.joinToString(", ").ifEmpty { "None advertised" },
              )
            }
          }

          item {
            NexusGlassCard(testTag = "network_traffic_card") {
              SectionHeader(title = "Traffic Counters (Since Boot)", icon = Icons.Default.SwapVert)
              Spacer(modifier = Modifier.height(6.dp))
              MetricValueRow("Total Received (RX)", net.totalRxBytes) { Formatters.formatBytes(it) }
              MetricValueRow("Total Transmitted (TX)", net.totalTxBytes) { Formatters.formatBytes(it) }
              MetricValueRow("Mobile RX", net.mobileRxBytes) { Formatters.formatBytes(it) }
              MetricValueRow("Mobile TX", net.mobileTxBytes) { Formatters.formatBytes(it) }
              MetricValueRow("Nexus App RX / TX", net.appRxBytes) { rx ->
                val tx = (net.appTxBytes as? com.example.data.model.MetricValue.Available)?.value ?: 0L
                "${Formatters.formatBytes(rx)} / ${Formatters.formatBytes(tx)}"
              }
            }
          }

          item {
            NexusGlassCard(testTag = "network_diagnostics_card") {
              SectionHeader(title = "Offline Link Diagnostics", icon = Icons.Default.NetworkCheck)
              Spacer(modifier = Modifier.height(8.dp))
              net.checks.forEach { chk ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = if (chk.passed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (chk.passed) colors.nominal else colors.warning,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(chk.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(chk.detail, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                  }
                }
              }
            }
          }

          item {
            SectionHeader(
              title = "System Network Interfaces",
              icon = Icons.Default.Lan,
              trailingText = "${net.interfaces.size} INTERFACES",
            )
          }

          items(net.interfaces, key = { it.name }) { iface ->
            NexusGlassCard {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(iface.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                StatusPill(
                  text = if (iface.isUp) "UP" else "DOWN",
                  color = if (iface.isUp) colors.nominal else colors.textMuted,
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              MetricValueRow("MTU", iface.mtu) { it.toString() }
              MetricKeyValueRow(
                label = "Addresses",
                value = iface.addresses.take(2).joinToString(", ").ifEmpty { "None" },
              )
            }
          }
        }
      }
    }
  }
}
