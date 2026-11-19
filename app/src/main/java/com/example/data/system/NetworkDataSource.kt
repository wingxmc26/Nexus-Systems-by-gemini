package com.example.data.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.data.model.MetricValue
import com.example.data.model.NetworkDiagnosticCheck
import com.example.data.model.NetworkDiagnostics
import com.example.data.model.NetworkInterfaceEntry
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkDataSource(private val context: Context) {

  @Suppress("DEPRECATION")
  suspend fun readNetworkDiagnostics(): NetworkDiagnostics = withContext(Dispatchers.IO) {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    val activeNetwork = cm?.activeNetwork
    val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
    val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

    val isAirplane = runCatching {
      Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }.getOrDefault(false)

    val transportLabel = when {
      caps == null -> "Disconnected"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
      caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
      else -> "Other Transport"
    }

    val isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)?.not() ?: false
    val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false

    val wifiInfo: WifiInfo? = when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps?.transportInfo is WifiInfo ->
        caps.transportInfo as WifiInfo
      caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ->
        runCatching { wm?.connectionInfo }.getOrNull()
      else -> null
    }

    val linkSpeedMetric = wifiInfo?.linkSpeed?.takeIf { it > 0 }
      ?.let { MetricValue.Available(it) }
      ?: MetricValue.Unavailable(if (transportLabel == "Wi-Fi") "Link speed restricted" else "Wi-Fi inactive")

    val freqMetric = wifiInfo?.frequency?.takeIf { it > 0 }
      ?.let { MetricValue.Available(it) }
      ?: MetricValue.Unavailable(if (transportLabel == "Wi-Fi") "Frequency restricted" else "Wi-Fi inactive")

    val rssiMetric = wifiInfo?.rssi?.takeIf { it > -127 && it <= 0 }
      ?.let { MetricValue.Available(it) }
      ?: MetricValue.Unavailable(if (transportLabel == "Wi-Fi") "Signal RSSI unavailable" else "Wi-Fi inactive")

    val wifiStdMetric: MetricValue<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifiInfo != null) {
      val std = when (wifiInfo.wifiStandard) {
        android.net.wifi.ScanResult.WIFI_STANDARD_LEGACY -> "Wi-Fi 1–3 (Legacy 802.11a/b/g)"
        android.net.wifi.ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
        android.net.wifi.ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
        android.net.wifi.ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 / 6E (802.11ax)"
        android.net.wifi.ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
        else -> null
      }
      if (std != null) MetricValue.Available(std) else MetricValue.Unavailable("Unknown standard")
    } else {
      MetricValue.Unavailable("Requires Android 11+ & active Wi-Fi")
    }

    val ipv4 = mutableListOf<String>()
    val ipv6 = mutableListOf<String>()
    linkProps?.linkAddresses?.forEach { linkAddr ->
      val addr = linkAddr.address
      val host = addr.hostAddress?.substringBefore('%') ?: return@forEach
      when (addr) {
        is Inet4Address -> ipv4.add("$host/${linkAddr.prefixLength}")
        is Inet6Address -> ipv6.add("$host/${linkAddr.prefixLength}")
      }
    }

    val dnsList = linkProps?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
    val ifaceName = linkProps?.interfaceName?.takeIf { it.isNotBlank() }
      ?.let { MetricValue.Available(it) }
      ?: MetricValue.Unavailable("No active interface")

    val interfaces = readNetworkInterfaces()

    fun trafficMetric(bytes: Long): MetricValue<Long> =
      if (bytes >= 0L && bytes != TrafficStats.UNSUPPORTED.toLong()) MetricValue.Available(bytes)
      else MetricValue.Unavailable("Counter unsupported on device")

    val uid = Process.myUid()
    val checks = listOf(
      NetworkDiagnosticCheck(
        title = "Airplane Mode State",
        detail = if (isAirplane) "Airplane mode is ON (radios disabled)" else "Airplane mode is OFF",
        passed = !isAirplane,
      ),
      NetworkDiagnosticCheck(
        title = "Active Transport Link",
        detail = if (isConnected) "Active transport: $transportLabel" else "No active network link detected",
        passed = isConnected,
      ),
      NetworkDiagnosticCheck(
        title = "Local IP Assignment",
        detail = if (ipv4.isNotEmpty() || ipv6.isNotEmpty()) {
          "Assigned ${(ipv4 + ipv6).first()}"
        } else {
          "No local IP address assigned"
        },
        passed = ipv4.isNotEmpty() || ipv6.isNotEmpty(),
      ),
      NetworkDiagnosticCheck(
        title = "DNS Resolver Config",
        detail = if (dnsList.isNotEmpty()) "Configured: ${dnsList.joinToString(", ")}" else "No DNS servers advertised by link",
        passed = dnsList.isNotEmpty(),
      ),
      NetworkDiagnosticCheck(
        title = "OS Capability Validation",
        detail = if (isValidated) "System reports validated link" else "Link unvalidated or offline",
        passed = isValidated,
      ),
    )

    NetworkDiagnostics(
      isConnected = isConnected,
      transportLabel = transportLabel,
      isMetered = isMetered,
      isValidated = isValidated,
      isAirplaneModeOn = isAirplane,
      wifiLinkSpeedMbps = linkSpeedMetric,
      wifiFrequencyMhz = freqMetric,
      wifiRssiDbm = rssiMetric,
      wifiStandard = wifiStdMetric,
      interfaceName = ifaceName,
      localIpv4Addresses = ipv4,
      localIpv6Addresses = ipv6,
      dnsServers = dnsList,
      totalRxBytes = trafficMetric(TrafficStats.getTotalRxBytes()),
      totalTxBytes = trafficMetric(TrafficStats.getTotalTxBytes()),
      mobileRxBytes = trafficMetric(TrafficStats.getMobileRxBytes()),
      mobileTxBytes = trafficMetric(TrafficStats.getMobileTxBytes()),
      appRxBytes = trafficMetric(TrafficStats.getUidRxBytes(uid)),
      appTxBytes = trafficMetric(TrafficStats.getUidTxBytes(uid)),
      interfaces = interfaces,
      checks = checks,
    )
  }

  private fun readNetworkInterfaces(): List<NetworkInterfaceEntry> {
    return runCatching {
      val list = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
      list.map { ni ->
        val addrs = ni.inetAddresses.toList().mapNotNull { it.hostAddress?.substringBefore('%') }
        val mtuVal = runCatching { ni.mtu }.getOrDefault(-1)
        NetworkInterfaceEntry(
          name = ni.name,
          isUp = runCatching { ni.isUp }.getOrDefault(false),
          isLoopback = runCatching { ni.isLoopback }.getOrDefault(false),
          mtu = if (mtuVal > 0) MetricValue.Available(mtuVal) else MetricValue.Unavailable("N/A"),
          addresses = addrs,
        )
      }.sortedWith(compareByDescending<NetworkInterfaceEntry> { it.isUp }.thenBy { it.name })
    }.getOrDefault(emptyList())
  }
}
