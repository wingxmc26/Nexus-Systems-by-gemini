package com.example.ui.viewmodel

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.WidgetClockSerializer
import com.example.data.local.WidgetInstanceEntity
import com.example.data.local.WidgetProjectEntity
import com.example.data.model.VisualElementKind
import com.example.data.model.VisualWidgetElement
import com.example.data.model.VisualWidgetSpec
import com.example.di.NexusContainer
import com.example.util.NexusBitmapUtils
import com.example.widget.NexusCustomWidgetProvider
import com.example.widget.NexusWidgetJsBridge
import com.example.widget.NexusWidgetUpdater
import com.example.widget.WidgetBitmapRenderer
import com.example.widget.WidgetSizePreset
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WidgetTemplate(
  val title: String,
  val kind: String,
  val visualSpec: VisualWidgetSpec = WidgetClockSerializer.defaultTelemetryVisualSpec(),
  val html: String = "",
  val css: String = "",
  val js: String = "",
)

data class WidgetStudioUiState(
  val projects: List<WidgetProjectEntity> = emptyList(),
  val placedInstances: List<WidgetInstanceEntity> = emptyList(),
  val targetAppWidgetId: Int? = null,
  val editingId: Long = 0L,
  val projectName: String = "AMOLED Telemetry",
  val editorMode: String = "VISUAL", // VISUAL or HTML
  val visualSpec: VisualWidgetSpec = WidgetClockSerializer.defaultTelemetryVisualSpec(),
  val selectedElementId: String? = null,
  val htmlCode: String = DEFAULT_WIDGET_HTML,
  val cssCode: String = DEFAULT_WIDGET_CSS,
  val jsCode: String = DEFAULT_WIDGET_JS,
  val liveValues: Map<VisualElementKind, String> = emptyMap(),
  val importedAssetNames: List<String> = emptyList(),
  val statusMessage: String? = null,
)

private const val DEFAULT_WIDGET_HTML = """<div class="card">
  <div class="row">
    <span class="tag">NEXUS LIVE</span>
    <span id="bat" class="bat">--%</span>
  </div>
  <div id="clock" class="clock">--:--</div>
  <div id="ram" class="meta">RAM --</div>
  <div id="storage" class="meta">Storage --</div>
</div>"""

private const val DEFAULT_WIDGET_CSS = """* { box-sizing: border-box; margin: 0; padding: 0; font-family: sans-serif; }
body { background: transparent; color: #FFFFFF; padding: 0; margin: 0; }
.card { background: linear-gradient(135deg, #0A0B0E, #160A10); border: 1px solid rgba(225,29,72,0.45); border-radius: 18px; padding: 16px; width: 100%; }
.row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.tag { color: #E11D48; font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.bat { color: #10B981; font-size: 13px; font-weight: 700; }
.clock { font-size: 30px; font-weight: 700; margin-bottom: 6px; }
.meta { color: #9CA3AF; font-size: 12px; margin-top: 3px; }"""

private const val DEFAULT_WIDGET_JS = """function updateWidget() {
  if (typeof NexusBridge === 'undefined') return;
  try {
    var bat = JSON.parse(NexusBridge.getBatteryJson());
    var mem = JSON.parse(NexusBridge.getMemoryJson());
    var sto = JSON.parse(NexusBridge.getStorageJson());
    var clk = JSON.parse(NexusBridge.getClockJson());
    document.getElementById('clock').innerText = clk.time;
    document.getElementById('bat').innerText = bat.percentage + '% ' + (bat.isCharging ? '⚡' : '');
    document.getElementById('ram').innerText = 'RAM ' + mem.usagePercent + '% • ' + mem.availableText + ' free';
    document.getElementById('storage').innerText = 'Free Storage: ' + sto.availableText;
  } catch (e) {}
}
updateWidget();
setInterval(updateWidget, 3000);"""

private const val IOS_CLOCK_HTML = """<div class="ios-card">
  <div class="top-row">
    <span id="date" class="date">FRI, NOV 20</span>
    <span id="bat" class="bat">88%</span>
  </div>
  <div id="clock" class="big-time">09:41</div>
  <div class="bottom-bar">
    <span id="ram">RAM --</span>
    <span id="dev">NEXUS</span>
  </div>
</div>"""

private const val IOS_CLOCK_CSS = """* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, sans-serif; }
body { background: transparent; color: #FFFFFF; padding: 0; }
.ios-card { width: 100%; min-height: 100%; border-radius: 26px; padding: 20px; background: linear-gradient(155deg, rgba(16,18,26,0.92), rgba(28,12,24,0.88)); border: 1px solid rgba(255,255,255,0.16); display: flex; flex-direction: column; justify-content: space-between; }
.top-row { display: flex; justify-content: space-between; font-size: 13px; font-weight: 700; letter-spacing: 1px; color: #F43F5E; }
.bat { color: #34D399; }
.big-time { font-size: 64px; font-weight: 800; letter-spacing: -2px; text-align: center; margin: 22px 0; text-shadow: 0 6px 22px rgba(0,0,0,0.45); }
.bottom-bar { display: flex; justify-content: space-between; font-size: 12px; color: #CBD5E1; }"""

private const val IOS_CLOCK_JS = """function tickIos() {
  if (typeof NexusWidget === 'undefined') return;
  try {
    var d = JSON.parse(NexusWidget.getAllTelemetryJson());
    document.getElementById('clock').innerText = d.time24;
    document.getElementById('date').innerText = d.dateShort.toUpperCase();
    document.getElementById('bat').innerText = d.batteryPercent + '% ' + (d.isCharging ? '⚡' : '');
    document.getElementById('ram').innerText = 'RAM ' + d.ramUsagePercent + '%';
    document.getElementById('dev').innerText = d.freeStorage + ' free';
  } catch (e) {}
}
tickIos();
setInterval(tickIos, 2000);"""

private const val ANIM_PULSE_HTML = """<div class="pulse-card">
  <div class="ring"></div>
  <div class="top">
    <span id="date" class="dt">FRI, NOV 20</span>
    <span id="bat" class="bt">88%</span>
  </div>
  <div id="clock" class="clk">09:41</div>
  <div id="sub" class="sub">NEXUS ANIMATED HUD</div>
</div>"""

private const val ANIM_PULSE_CSS = """* { box-sizing: border-box; margin: 0; padding: 0; font-family: sans-serif; }
body { background: transparent; color: #FFFFFF; }
@keyframes neonPulse {
  0%, 100% { box-shadow: 0 0 8px rgba(225,29,72,0.45), inset 0 0 12px rgba(225,29,72,0.18); border-color: rgba(225,29,72,0.75); }
  50% { box-shadow: 0 0 22px rgba(56,189,248,0.85), inset 0 0 20px rgba(56,189,248,0.28); border-color: rgba(56,189,248,0.95); }
}
@keyframes orbit {
  0% { transform: rotate(0deg) scale(0.95); opacity: 0.45; }
  50% { transform: rotate(180deg) scale(1.06); opacity: 0.9; }
  100% { transform: rotate(360deg) scale(0.95); opacity: 0.45; }
}
.pulse-card { position: relative; overflow: hidden; width: 100%; min-height: 100%; border-radius: 22px; padding: 16px; background: linear-gradient(145deg, #090A10, #170C18); border: 2px solid rgba(225,29,72,0.7); animation: neonPulse 1.6s ease-in-out infinite; display: flex; flex-direction: column; justify-content: space-between; }
.ring { position: absolute; right: -26px; top: -26px; width: 96px; height: 96px; border-radius: 50%; border: 3px dashed rgba(244,63,94,0.55); animation: orbit 2.2s linear infinite; }
.top { display: flex; justify-content: space-between; font-size: 12px; font-weight: 700; color: #F43F5E; }
.bt { color: #34D399; }
.clk { font-size: 46px; font-weight: 800; letter-spacing: -1px; text-align: center; text-shadow: 0 2px 16px rgba(225,29,72,0.5); }
.sub { font-size: 11px; color: #CBD5E1; text-align: center; letter-spacing: 1px; }"""

private const val ANIM_PULSE_JS = """function tickPulse() {
  if (typeof NexusWidget === 'undefined') return;
  try {
    var d = JSON.parse(NexusWidget.getAllTelemetryJson());
    document.getElementById('clock').innerText = d.time24;
    document.getElementById('date').innerText = d.dateShort.toUpperCase();
    document.getElementById('bat').innerText = d.batteryPercent + '% ' + (d.isCharging ? '⚡' : '');
    document.getElementById('sub').innerText = 'RAM ' + d.ramUsagePercent + '% • ' + d.freeStorage + ' FREE';
  } catch (e) {}
}
tickPulse();
setInterval(tickPulse, 1000);"""

private const val ANIM_WAVE_HTML = """<div class="wave-box">
  <div class="hdr"><span class="ttl">NEXUS SPECTRUM</span><span id="clk">09:41</span></div>
  <div class="bars">
    <span class="b b1"></span><span class="b b2"></span><span class="b b3"></span>
    <span class="b b4"></span><span class="b b5"></span><span class="b b6"></span>
    <span class="b b7"></span><span class="b b8"></span>
  </div>
  <div id="info" class="info">Battery -- • RAM --</div>
</div>"""

private const val ANIM_WAVE_CSS = """* { box-sizing: border-box; margin: 0; padding: 0; font-family: sans-serif; }
body { background: transparent; color: #FFF; }
@keyframes eq { 0%, 100% { transform: scaleY(0.28); } 50% { transform: scaleY(1); } }
.wave-box { width: 100%; min-height: 100%; border-radius: 20px; padding: 14px; background: linear-gradient(135deg, #07090F, #0F172A); border: 1px solid rgba(56,189,248,0.45); display: flex; flex-direction: column; justify-content: space-between; }
.hdr { display: flex; justify-content: space-between; align-items: center; font-weight: 700; font-size: 13px; }
.ttl { color: #38BDF8; letter-spacing: 1px; }
#clk { font-size: 22px; color: #FFF; }
.bars { height: 48px; display: flex; align-items: flex-end; justify-content: space-between; gap: 6px; margin: 8px 0; }
.b { flex: 1; height: 100%; border-radius: 6px; transform-origin: bottom; background: linear-gradient(to top, #E11D48, #38BDF8); animation: eq 1.1s ease-in-out infinite; }
.b2 { animation-delay: 0.14s; } .b3 { animation-delay: 0.28s; } .b4 { animation-delay: 0.42s; }
.b5 { animation-delay: 0.56s; } .b6 { animation-delay: 0.70s; } .b7 { animation-delay: 0.84s; } .b8 { animation-delay: 0.98s; }
.info { font-size: 12px; color: #94A3B8; }"""

private const val ANIM_WAVE_JS = """function tickWave() {
  if (typeof NexusWidget === 'undefined') return;
  try {
    var d = JSON.parse(NexusWidget.getAllTelemetryJson());
    document.getElementById('clk').innerText = d.time24;
    document.getElementById('info').innerText = 'Battery ' + d.batteryPercent + '% • RAM ' + d.ramUsagePercent + '%';
  } catch (e) {}
}
tickWave();
setInterval(tickWave, 1500);"""

private const val MEDIA_HUD_HTML = """<div class="m-card">
  <div class="m-top">
    <span id="m-src" class="m-src">MEDIA ENGINE</span>
    <span id="m-state" class="m-state">IDLE</span>
  </div>
  <div class="m-body">
    <img id="m-art" class="m-art" style="display:none" />
    <div class="m-info">
      <div id="m-title" class="m-title">Waiting for media...</div>
      <div id="m-artist" class="m-artist">Play Spotify, YT Music or Nexus Audio</div>
      <div id="m-time" class="m-time">0:00 / 0:00</div>
    </div>
  </div>
  <div class="m-bar"><div id="m-fill" class="m-fill"></div></div>
</div>"""

private const val MEDIA_HUD_CSS = """* { box-sizing: border-box; margin: 0; padding: 0; font-family: sans-serif; }
body { background: transparent; color: #FFF; }
.m-card { width: 100%; min-height: 100%; border-radius: 20px; padding: 14px; background: linear-gradient(140deg, rgba(10,12,18,0.95), rgba(30,10,22,0.92)); border: 1px solid rgba(225,29,72,0.45); display: flex; flex-direction: column; justify-content: space-between; }
.m-top { display: flex; justify-content: space-between; font-size: 10px; font-weight: 700; letter-spacing: 1px; color: #F43F5E; }
.m-state { color: #34D399; }
.m-body { display: flex; align-items: center; gap: 10px; margin: 8px 0; }
.m-art { width: 46px; height: 46px; border-radius: 10px; object-fit: cover; }
.m-info { flex: 1; min-width: 0; }
.m-title { font-size: 14px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.m-artist { font-size: 11px; color: #94A3B8; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 2px; }
.m-time { font-size: 10px; color: #CBD5E1; margin-top: 3px; }
.m-bar { width: 100%; height: 4px; border-radius: 99px; background: rgba(255,255,255,0.12); overflow: hidden; }
.m-fill { width: 0%; height: 100%; background: linear-gradient(90deg, #E11D48, #38BDF8); }"""

private const val MEDIA_HUD_JS = """function fmtMs(ms){ var s=Math.max(0,Math.floor(ms/1000)); return Math.floor(s/60)+':'+String(s%60).padStart(2,'0'); }
function syncMedia(){
  if (typeof NexusWidget === 'undefined') return;
  try {
    var m = JSON.parse(NexusWidget.getMediaSession());
    if (m.status !== 'available') {
      document.getElementById('m-src').innerText = 'MEDIA PERMISSION';
      document.getElementById('m-state').innerText = 'REQUIRED';
      document.getElementById('m-title').innerText = 'Enable Notification Access';
      document.getElementById('m-artist').innerText = m.reason || 'Grant permission in Widgets screen';
      return;
    }
    document.getElementById('m-src').innerText = (m.sourceAppName || 'MEDIA').toUpperCase();
    document.getElementById('m-state').innerText = m.isPlaying ? 'PLAYING' : (m.hasActiveSession ? 'PAUSED' : 'IDLE');
    document.getElementById('m-title').innerText = m.title;
    document.getElementById('m-artist').innerText = m.artist || '';
    var art = document.getElementById('m-art');
    if (m.artworkFileUrl) { art.src = m.artworkFileUrl; art.style.display = 'block'; } else { art.style.display = 'none'; }
    if (m.durationMs > 0) {
      document.getElementById('m-time').innerText = fmtMs(m.positionMs) + ' / ' + fmtMs(m.durationMs);
      document.getElementById('m-fill').style.width = Math.min(100, Math.round((m.positionMs * 100) / m.durationMs)) + '%';
    } else {
      document.getElementById('m-time').innerText = '';
      document.getElementById('m-fill').style.width = m.isPlaying ? '35%' : '0%';
    }
  } catch (e) {}
}
syncMedia();
window.addEventListener('nexus:data', syncMedia);
setInterval(syncMedia, 2000);"""

private const val SYS_NET_HTML = """<div class="sn-card">
  <div class="sn-head">
    <span id="sn-time" class="sn-time">--:--</span>
    <span id="sn-net" class="sn-net">--</span>
  </div>
  <div id="sn-dev" class="sn-dev">NEXUS</div>
  <div class="sn-grid">
    <div id="sn-bat" class="sn-pill">BAT --%</div>
    <div id="sn-ram" class="sn-pill">RAM --%</div>
  </div>
</div>"""

private const val SYS_NET_CSS = """* { box-sizing: border-box; margin: 0; padding: 0; font-family: sans-serif; }
body { background: transparent; color: #FFF; }
.sn-card { width: 100%; min-height: 100%; border-radius: 20px; padding: 14px; background: linear-gradient(155deg, #070B12, #0F172A); border: 1px solid rgba(56,189,248,0.4); display: flex; flex-direction: column; justify-content: space-between; }
.sn-head { display: flex; justify-content: space-between; align-items: baseline; }
.sn-time { font-size: 28px; font-weight: 800; }
.sn-net { font-size: 11px; font-weight: 700; color: #38BDF8; }
.sn-dev { font-size: 11px; color: #94A3B8; margin: 4px 0; }
.sn-grid { display: flex; gap: 8px; }
.sn-pill { flex: 1; background: rgba(255,255,255,0.06); border-radius: 10px; padding: 6px 8px; font-size: 11px; font-weight: 700; text-align: center; }"""

private const val SYS_NET_JS = """function syncSys(){
  if (typeof NexusWidget === 'undefined') return;
  try {
    var t = JSON.parse(NexusWidget.getCurrentTime());
    var n = JSON.parse(NexusWidget.getNetworkInfo());
    var b = JSON.parse(NexusWidget.getBatteryInfo());
    var m = JSON.parse(NexusWidget.getMemoryInfo());
    var d = JSON.parse(NexusWidget.getDeviceInfo());
    document.getElementById('sn-time').innerText = t.is24HourFormat ? t.time24 : t.time12;
    document.getElementById('sn-net').innerText = (n.isConnected ? n.transportType : 'OFFLINE').toUpperCase();
    document.getElementById('sn-dev').innerText = d.manufacturer + ' ' + d.model + ' • ' + t.timezoneDisplay;
    document.getElementById('sn-bat').innerText = 'BAT ' + b.percentage + '%' + (b.isCharging ? '⚡' : '');
    document.getElementById('sn-ram').innerText = 'RAM ' + m.usagePercent + '%';
  } catch (e) {}
}
syncSys();
window.addEventListener('nexus:data', syncSys);"""

class WidgetStudioViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val dao = container.database.customWidgetDao()
  private val instanceDao = container.database.widgetInstanceDao()

  private val _editingId = MutableStateFlow(0L)
  private val _targetAppWidgetId = MutableStateFlow<Int?>(null)
  private val _projectName = MutableStateFlow("AMOLED Telemetry")
  private val _editorMode = MutableStateFlow("VISUAL")
  private val _visualSpec = MutableStateFlow(WidgetClockSerializer.defaultTelemetryVisualSpec())
  private val _selectedElementId = MutableStateFlow<String?>("el_clock")
  private val _htmlCode = MutableStateFlow(DEFAULT_WIDGET_HTML)
  private val _cssCode = MutableStateFlow(DEFAULT_WIDGET_CSS)
  private val _jsCode = MutableStateFlow(DEFAULT_WIDGET_JS)
  private val _liveValues = MutableStateFlow<Map<VisualElementKind, String>>(emptyMap())
  private val _importedAssets = MutableStateFlow<List<String>>(emptyList())
  private val _statusMessage = MutableStateFlow<String?>(null)

  val templates = listOf(
    WidgetTemplate(
      title = "AMOLED Telemetry",
      kind = "VISUAL",
      visualSpec = WidgetClockSerializer.defaultTelemetryVisualSpec(),
    ),
    WidgetTemplate(
      title = "iOS Large Depth Clock (4×4)",
      kind = "VISUAL",
      visualSpec = VisualWidgetSpec(
        backgroundColorArgb = 0xFF0A0D16L,
        gradientEndColorArgb = 0xFF1D1024L,
        isTransparent = false,
        isGlassStyle = true,
        borderColorArgb = 0x40FFFFFFL,
        borderWidthDp = 1.5f,
        cornerRadiusDp = 28,
        sizePresetName = WidgetSizePreset.IOS_LARGE_CLOCK.name,
        targetWidthDp = WidgetSizePreset.IOS_LARGE_CLOCK.widthDp,
        targetHeightDp = WidgetSizePreset.IOS_LARGE_CLOCK.heightDp,
        containerPaddingDp = 16,
        refreshIntervalMinutes = 1,
        elements = listOf(
          VisualWidgetElement(
            id = "ios_date",
            kind = VisualElementKind.DATE,
            xFraction = 0.06f,
            yFraction = 0.06f,
            widthFraction = 0.60f,
            heightFraction = 0.12f,
            fontSizeSp = 15f,
            fontWeightBold = true,
            textColorArgb = 0xFFF43F5EL,
            alignment = 0,
            layerOrder = 0,
          ),
          VisualWidgetElement(
            id = "ios_bat",
            kind = VisualElementKind.BATTERY,
            xFraction = 0.64f,
            yFraction = 0.06f,
            widthFraction = 0.30f,
            heightFraction = 0.12f,
            fontSizeSp = 14f,
            fontWeightBold = true,
            textColorArgb = 0xFF34D399L,
            alignment = 2,
            layerOrder = 1,
          ),
          VisualWidgetElement(
            id = "ios_clock",
            kind = VisualElementKind.CLOCK,
            xFraction = 0.04f,
            yFraction = 0.24f,
            widthFraction = 0.92f,
            heightFraction = 0.34f,
            fontSizeSp = 54f,
            fontWeightBold = true,
            textColorArgb = 0xFFFFFFFFL,
            alignment = 1,
            layerOrder = 2,
          ),
          VisualWidgetElement(
            id = "ios_ram",
            kind = VisualElementKind.RAM,
            xFraction = 0.06f,
            yFraction = 0.68f,
            widthFraction = 0.88f,
            heightFraction = 0.12f,
            fontSizeSp = 14f,
            fontWeightBold = false,
            textColorArgb = 0xFFE2E8F0L,
            alignment = 1,
            layerOrder = 3,
          ),
          VisualWidgetElement(
            id = "ios_storage",
            kind = VisualElementKind.STORAGE,
            xFraction = 0.06f,
            yFraction = 0.82f,
            widthFraction = 0.88f,
            heightFraction = 0.12f,
            fontSizeSp = 13f,
            fontWeightBold = false,
            textColorArgb = 0xFF94A3B8L,
            alignment = 1,
            layerOrder = 4,
          ),
        ),
      ),
    ),
    WidgetTemplate(
      title = "XL Wide Banner (4×1)",
      kind = "VISUAL",
      visualSpec = VisualWidgetSpec(
        backgroundColorArgb = 0xFF07090FL,
        gradientEndColorArgb = 0xFF150811L,
        isGlassStyle = true,
        borderColorArgb = 0x55E11D48L,
        cornerRadiusDp = 22,
        sizePresetName = WidgetSizePreset.WIDE.name,
        targetWidthDp = WidgetSizePreset.WIDE.widthDp,
        targetHeightDp = WidgetSizePreset.WIDE.heightDp,
        containerPaddingDp = 12,
        refreshIntervalMinutes = 5,
        elements = listOf(
          VisualWidgetElement("wd_clk", VisualElementKind.CLOCK, xFraction = 0.04f, yFraction = 0.14f, widthFraction = 0.34f, heightFraction = 0.72f, fontSizeSp = 30f, alignment = 0, textColorArgb = 0xFFFFFFFFL),
          VisualWidgetElement("wd_bat", VisualElementKind.BATTERY, xFraction = 0.42f, yFraction = 0.16f, widthFraction = 0.54f, heightFraction = 0.30f, fontSizeSp = 14f, alignment = 2, textColorArgb = 0xFF10B981L),
          VisualWidgetElement("wd_ram", VisualElementKind.RAM, xFraction = 0.42f, yFraction = 0.54f, widthFraction = 0.54f, heightFraction = 0.30f, fontSizeSp = 13f, fontWeightBold = false, alignment = 2, textColorArgb = 0xFFCBD5E1L),
        ),
      ),
    ),
    WidgetTemplate(
      title = "Now Playing & Battery",
      kind = "VISUAL",
      visualSpec = VisualWidgetSpec(
        backgroundColorArgb = 0xFF090A0FL,
        gradientEndColorArgb = 0xFF141824L,
        isGlassStyle = true,
        borderColorArgb = 0x4D3B82F6L,
        cornerRadiusDp = 22,
        elements = listOf(
          VisualWidgetElement("np_bat", VisualElementKind.BATTERY, xFraction = 0.07f, yFraction = 0.12f, widthFraction = 0.45f, heightFraction = 0.22f, fontSizeSp = 15f, textColorArgb = 0xFF10B981L),
          VisualWidgetElement("np_clk", VisualElementKind.CLOCK, xFraction = 0.56f, yFraction = 0.10f, widthFraction = 0.37f, heightFraction = 0.26f, fontSizeSp = 22f, alignment = 2),
          VisualWidgetElement("np_mus", VisualElementKind.MUSIC, xFraction = 0.07f, yFraction = 0.46f, widthFraction = 0.86f, heightFraction = 0.24f, fontSizeSp = 14f, textColorArgb = 0xFFFFFFFFL),
          VisualWidgetElement("np_dev", VisualElementKind.DEVICE, xFraction = 0.07f, yFraction = 0.74f, widthFraction = 0.86f, heightFraction = 0.16f, fontSizeSp = 12f, fontWeightBold = false, textColorArgb = 0xFF9CA3AFL),
        ),
      ),
    ),
    WidgetTemplate(
      title = "HTML iOS Large Clock",
      kind = "HTML",
      visualSpec = VisualWidgetSpec(
        htmlTransparent = true,
        sizePresetName = WidgetSizePreset.IOS_LARGE_CLOCK.name,
        targetWidthDp = WidgetSizePreset.IOS_LARGE_CLOCK.widthDp,
        targetHeightDp = WidgetSizePreset.IOS_LARGE_CLOCK.heightDp,
        refreshIntervalMinutes = 1,
      ),
      html = IOS_CLOCK_HTML,
      css = IOS_CLOCK_CSS,
      js = IOS_CLOCK_JS,
    ),
    WidgetTemplate(
      title = "HTML Glass HUD",
      kind = "HTML",
      visualSpec = VisualWidgetSpec(
        htmlTransparent = true,
        sizePresetName = WidgetSizePreset.MEDIUM.name,
        targetWidthDp = WidgetSizePreset.MEDIUM.widthDp,
        targetHeightDp = WidgetSizePreset.MEDIUM.heightDp,
        refreshIntervalMinutes = 5,
      ),
      html = DEFAULT_WIDGET_HTML,
      css = DEFAULT_WIDGET_CSS,
      js = DEFAULT_WIDGET_JS,
    ),
    WidgetTemplate(
      title = "Animated Neon Clock",
      kind = "HTML",
      visualSpec = VisualWidgetSpec(
        htmlTransparent = true,
        enableAnimation = true,
        animationFrameMs = 160,
        sizePresetName = WidgetSizePreset.MEDIUM.name,
        targetWidthDp = WidgetSizePreset.MEDIUM.widthDp,
        targetHeightDp = WidgetSizePreset.MEDIUM.heightDp,
        refreshIntervalMinutes = 1,
      ),
      html = ANIM_PULSE_HTML,
      css = ANIM_PULSE_CSS,
      js = ANIM_PULSE_JS,
    ),
    WidgetTemplate(
      title = "Animated Wave HUD",
      kind = "HTML",
      visualSpec = VisualWidgetSpec(
        htmlTransparent = true,
        enableAnimation = true,
        animationFrameMs = 140,
        sizePresetName = WidgetSizePreset.MEDIUM.name,
        targetWidthDp = WidgetSizePreset.MEDIUM.widthDp,
        targetHeightDp = WidgetSizePreset.MEDIUM.heightDp,
        refreshIntervalMinutes = 1,
      ),
      html = ANIM_WAVE_HTML,
      css = ANIM_WAVE_CSS,
      js = ANIM_WAVE_JS,
    ),
    WidgetTemplate(
      title = "HTML Media Player HUD",
      kind = "HTML",
      visualSpec = VisualWidgetSpec(
        htmlTransparent = true,
        sizePresetName = WidgetSizePreset.MEDIUM.name,
        targetWidthDp = WidgetSizePreset.MEDIUM.widthDp,
        targetHeightDp = WidgetSizePreset.MEDIUM.heightDp,
        refreshIntervalMinutes = 1,
      ),
      html = MEDIA_HUD_HTML,
      css = MEDIA_HUD_CSS,
      js = MEDIA_HUD_JS,
    ),
    WidgetTemplate(
      title = "HTML System + Network HUD",
      kind = "HTML",
      visualSpec = VisualWidgetSpec(
        htmlTransparent = true,
        sizePresetName = WidgetSizePreset.MEDIUM.name,
        targetWidthDp = WidgetSizePreset.MEDIUM.widthDp,
        targetHeightDp = WidgetSizePreset.MEDIUM.heightDp,
        refreshIntervalMinutes = 1,
      ),
      html = SYS_NET_HTML,
      css = SYS_NET_CSS,
      js = SYS_NET_JS,
    ),
  )

  val uiState: StateFlow<WidgetStudioUiState> = combine(
    dao.observeAll(),
    instanceDao.observeAll(),
    _targetAppWidgetId,
    _editingId,
    _projectName,
    _editorMode,
    _visualSpec,
    _selectedElementId,
    _htmlCode,
    _cssCode,
    _jsCode,
    _liveValues,
    _importedAssets,
    _statusMessage,
  ) { arr ->
    @Suppress("UNCHECKED_CAST")
    WidgetStudioUiState(
      projects = arr[0] as List<WidgetProjectEntity>,
      placedInstances = arr[1] as List<WidgetInstanceEntity>,
      targetAppWidgetId = arr[2] as Int?,
      editingId = arr[3] as Long,
      projectName = arr[4] as String,
      editorMode = arr[5] as String,
      visualSpec = arr[6] as VisualWidgetSpec,
      selectedElementId = arr[7] as String?,
      htmlCode = arr[8] as String,
      cssCode = arr[9] as String,
      jsCode = arr[10] as String,
      liveValues = arr[11] as Map<VisualElementKind, String>,
      importedAssetNames = arr[12] as List<String>,
      statusMessage = arr[13] as String?,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = WidgetStudioUiState(),
  )

  init {
    refreshLiveValues()
    refreshAssetsList()
  }

  fun refreshLiveValues() {
    viewModelScope.launch {
      _liveValues.value = runCatching {
        WidgetBitmapRenderer.buildLiveValuesMap(getApplication())
      }.getOrDefault(emptyMap())
    }
  }

  fun refreshAssetsList() {
    viewModelScope.launch(Dispatchers.IO) {
      val dir = File(getApplication<Application>().filesDir, NexusWidgetJsBridge.ASSETS_DIR)
      val list = dir.listFiles()?.sortedByDescending { it.lastModified() }?.map { it.name } ?: emptyList()
      _importedAssets.value = list
    }
  }

  fun setTargetAppWidgetId(appWidgetId: Int?) {
    _targetAppWidgetId.value = appWidgetId
  }

  fun openWidgetTarget(widgetId: Int?, projectId: Long?) {
    viewModelScope.launch {
      if (widgetId != null && widgetId > 0) {
        _targetAppWidgetId.value = widgetId
        val bound = instanceDao.getByWidgetId(widgetId)
        val targetProjId = projectId?.takeIf { it > 0L } ?: bound?.projectId
        if (targetProjId != null) {
          dao.getById(targetProjId)?.let { loadProject(it) }
        }
      } else if (projectId != null && projectId > 0L) {
        dao.getById(projectId)?.let { loadProject(it) }
      }
    }
  }

  fun setProjectName(name: String) {
    _projectName.value = name
  }

  fun setEditorMode(mode: String) {
    _editorMode.value = mode
  }

  fun setHtmlCode(code: String) { _htmlCode.value = code }
  fun setCssCode(code: String) { _cssCode.value = code }
  fun setJsCode(code: String) { _jsCode.value = code }

  fun selectElement(id: String?) {
    _selectedElementId.value = id
  }

  fun selectWidgetSizePreset(preset: WidgetSizePreset) {
    _visualSpec.value = _visualSpec.value.copy(
      sizePresetName = preset.name,
      targetWidthDp = preset.widthDp,
      targetHeightDp = preset.heightDp,
    )
  }

  fun setCustomDimensions(widthDp: Int, heightDp: Int) {
    _visualSpec.value = _visualSpec.value.copy(
      sizePresetName = WidgetSizePreset.CUSTOM.name,
      targetWidthDp = widthDp.coerceIn(72, 900),
      targetHeightDp = heightDp.coerceIn(56, 900),
    )
  }

  fun setContainerPadding(paddingDp: Int) {
    _visualSpec.value = _visualSpec.value.copy(containerPaddingDp = paddingDp.coerceIn(0, 36))
  }

  fun setRefreshIntervalMinutes(minutes: Int) {
    _visualSpec.value = _visualSpec.value.copy(refreshIntervalMinutes = minutes.coerceIn(0, 60))
  }

  fun setAnimationEnabled(enabled: Boolean) {
    _visualSpec.value = _visualSpec.value.copy(enableAnimation = enabled)
  }

  fun setAnimationFrameMs(ms: Int) {
    _visualSpec.value = _visualSpec.value.copy(animationFrameMs = ms.coerceIn(80, 1000))
  }

  fun addElement(kind: VisualElementKind) {
    val cur = _visualSpec.value
    val maxOrder = (cur.elements.maxOfOrNull { it.layerOrder } ?: -1) + 1
    val newEl = VisualWidgetElement(
      id = "el_${UUID.randomUUID().toString().take(6)}",
      kind = kind,
      label = if (kind == VisualElementKind.TEXT) "Nexus Label" else "",
      xFraction = 0.08f,
      yFraction = (0.12f + (cur.elements.size % 4) * 0.18f).coerceAtMost(0.75f),
      widthFraction = if (kind == VisualElementKind.IMAGE) 0.40f else 0.72f,
      heightFraction = if (kind == VisualElementKind.IMAGE) 0.40f else 0.18f,
      fontSizeSp = if (kind == VisualElementKind.CLOCK) 26f else 14f,
      layerOrder = maxOrder,
    )
    _visualSpec.value = cur.copy(elements = cur.elements + newEl)
    _selectedElementId.value = newEl.id
  }

  fun moveSelectedElement(deltaXFraction: Float, deltaYFraction: Float) {
    val selId = _selectedElementId.value ?: return
    val cur = _visualSpec.value
    _visualSpec.value = cur.copy(
      elements = cur.elements.map { el ->
        if (el.id == selId) {
          el.copy(
            xFraction = (el.xFraction + deltaXFraction).coerceIn(0f, 0.90f),
            yFraction = (el.yFraction + deltaYFraction).coerceIn(0f, 0.90f),
          )
        } else el
      }
    )
  }

  fun updateElement(updated: VisualWidgetElement) {
    _visualSpec.value = _visualSpec.value.copy(
      elements = _visualSpec.value.elements.map { if (it.id == updated.id) updated else it }
    )
  }

  fun reorderSelectedLayer(moveForward: Boolean) {
    val selId = _selectedElementId.value ?: return
    val sorted = _visualSpec.value.elements.sortedBy { it.layerOrder }.toMutableList()
    val idx = sorted.indexOfFirst { it.id == selId }
    if (idx < 0) return
    val targetIdx = if (moveForward) (idx + 1).coerceAtMost(sorted.lastIndex) else (idx - 1).coerceAtLeast(0)
    if (idx == targetIdx) return
    val item = sorted.removeAt(idx)
    sorted.add(targetIdx, item)
    val reindexed = sorted.mapIndexed { i, el -> el.copy(layerOrder = i) }
    _visualSpec.value = _visualSpec.value.copy(elements = reindexed)
  }

  fun removeSelectedElement() {
    val selId = _selectedElementId.value ?: return
    _visualSpec.value = _visualSpec.value.copy(
      elements = _visualSpec.value.elements.filterNot { it.id == selId }
    )
    _selectedElementId.value = _visualSpec.value.elements.firstOrNull()?.id
  }

  fun updateVisualStyle(transform: (VisualWidgetSpec) -> VisualWidgetSpec) {
    _visualSpec.value = transform(_visualSpec.value)
  }

  fun setHtmlTransparent(transparent: Boolean) {
    _visualSpec.value = _visualSpec.value.copy(htmlTransparent = transparent)
  }

  fun importVisualImageForSelection(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      val app = getApplication<Application>()
      runCatching {
        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val rawName = resolveDisplayName(app, uri).lowercase()
      val mime = app.contentResolver.getType(uri).orEmpty().lowercase()
      val ext = when {
        mime.contains("gif") || rawName.endsWith(".gif") -> "gif"
        mime.contains("webp") || rawName.endsWith(".webp") -> "webp"
        mime.contains("jpeg") || mime.contains("jpg") || rawName.endsWith(".jpg") || rawName.endsWith(".jpeg") -> "jpg"
        else -> "png"
      }
      val copied = copyUriIntoDir(app, uri, "widget_images", "img_${System.currentTimeMillis()}.$ext")
      if (copied == null) {
        _statusMessage.value = "Failed to import selected image."
        return@launch
      }
      val isGif = ext == "gif" || com.example.widget.AnimatedWidgetEngine.isAnimatedGifFile(copied.absolutePath)
      val cur = if (isGif) _visualSpec.value.copy(enableAnimation = true) else _visualSpec.value
      val sel = cur.elements.firstOrNull { it.id == _selectedElementId.value }
      if (sel != null && sel.kind == VisualElementKind.IMAGE) {
        _visualSpec.value = cur.copy(
          elements = cur.elements.map { if (it.id == sel.id) sel.copy(imageUriOrPath = copied.absolutePath) else it }
        )
      } else {
        val maxOrder = (cur.elements.maxOfOrNull { it.layerOrder } ?: -1) + 1
        val newEl = VisualWidgetElement(
          id = "img_${UUID.randomUUID().toString().take(6)}",
          kind = VisualElementKind.IMAGE,
          label = if (isGif) "Animated GIF" else "Photo",
          xFraction = 0.06f,
          yFraction = 0.12f,
          widthFraction = 0.36f,
          heightFraction = 0.44f,
          cornerRadiusDp = 14,
          layerOrder = maxOrder,
          imageUriOrPath = copied.absolutePath,
        )
        _visualSpec.value = cur.copy(elements = cur.elements + newEl)
        _selectedElementId.value = newEl.id
      }
      _statusMessage.value = if (isGif) {
        "Imported animated GIF into widget canvas."
      } else {
        "Imported local image into widget canvas."
      }
    }
  }

  fun importHtmlAssetFromUri(uri: Uri, insertTagIntoHtml: Boolean = true) {
    viewModelScope.launch(Dispatchers.IO) {
      val app = getApplication<Application>()
      runCatching {
        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val rawName = resolveDisplayName(app, uri)
      val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
      val copied = copyUriIntoDir(app, uri, NexusWidgetJsBridge.ASSETS_DIR, safeName)
      if (copied == null) {
        _statusMessage.value = "Failed to import asset."
        return@launch
      }
      refreshAssetsList()
      val lowerName = safeName.lowercase()
      val isFont = lowerName.endsWith(".ttf") || lowerName.endsWith(".otf") || lowerName.endsWith(".woff2") || lowerName.endsWith(".woff")
      if (insertTagIntoHtml && !isFont) {
        val mime = app.contentResolver.getType(uri).orEmpty().lowercase()
        val tag = when {
          mime.startsWith("audio/") || safeName.endsWith(".mp3", true) || safeName.endsWith(".wav", true) ->
            "\n<audio controls preload=\"metadata\" style=\"width:100%;margin-top:8px;\" src=\"${copied.name}\"></audio>"
          else ->
            "\n<img src=\"${copied.name}\" style=\"max-width:100%;height:auto;border-radius:12px;margin-top:6px;object-fit:cover;\" />"
        }
        _htmlCode.value = _htmlCode.value + tag
      }
      _statusMessage.value = if (isFont) {
        "Imported font '${copied.name}' (CSS font-family: '${copied.nameWithoutExtension}')."
      } else {
        "Imported '${copied.name}' into widget local assets."
      }
    }
  }

  fun deleteImportedAsset(fileName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val file = File(File(getApplication<Application>().filesDir, NexusWidgetJsBridge.ASSETS_DIR), fileName)
      if (file.exists()) file.delete()
      refreshAssetsList()
      _statusMessage.value = "Removed asset '$fileName'."
    }
  }

  fun loadProject(project: WidgetProjectEntity) {
    _editingId.value = project.id
    _projectName.value = project.name
    _editorMode.value = project.kind
    _visualSpec.value = WidgetClockSerializer.deserializeVisualWidget(project.visualJson)
    _selectedElementId.value = _visualSpec.value.elements.firstOrNull()?.id
    if (project.htmlCode.isNotBlank()) _htmlCode.value = project.htmlCode
    if (project.cssCode.isNotBlank()) _cssCode.value = project.cssCode
    if (project.jsCode.isNotBlank()) _jsCode.value = project.jsCode
    _statusMessage.value = "Loaded '${project.name}'."
  }

  fun applyTemplate(tpl: WidgetTemplate) {
    _editingId.value = 0L
    _projectName.value = tpl.title
    _editorMode.value = tpl.kind
    _visualSpec.value = tpl.visualSpec
    _selectedElementId.value = tpl.visualSpec.elements.firstOrNull()?.id
    if (tpl.kind == "HTML") {
      _htmlCode.value = tpl.html
      _cssCode.value = tpl.css
      _jsCode.value = tpl.js
    }
    _statusMessage.value = "Applied template '${tpl.title}'."
  }

  fun saveProject(activateOnHomeScreen: Boolean, capturedWebBitmap: Bitmap? = null) {
    viewModelScope.launch {
      val app = getApplication<Application>()
      val cleanName = _projectName.value.trim().ifBlank { "Nexus Widget" }
      val mode = _editorMode.value
      val visJson = WidgetClockSerializer.serializeVisualWidget(_visualSpec.value)
      var bmpPath: String? = null
      val density = app.resources.displayMetrics.density

      if (activateOnHomeScreen || mode == "HTML") {
        val dir = File(app.filesDir, "widget_renders").apply { if (!exists()) mkdirs() }
        val fileTag = if (_editingId.value > 0L) "proj_${_editingId.value}" else "active_custom_widget"
        val outFile = File(dir, "${fileTag}.png")
        val bmpToSave = if (mode == "VISUAL") {
          val live = WidgetBitmapRenderer.buildLiveValuesMap(app)
          WidgetBitmapRenderer.renderVisualWidgetBitmapDp(
            spec = _visualSpec.value,
            liveValues = live,
            widthDp = _visualSpec.value.targetWidthDp,
            heightDp = _visualSpec.value.targetHeightDp,
            density = density,
          )
        } else {
          capturedWebBitmap?.let {
            NexusBitmapUtils.ensureBinderSafeBitmap(it, maxByteBudget = 1_200_000, maxWidth = 1080, maxHeight = 1080)
          }
        }
        if (bmpToSave != null) {
          withContext(Dispatchers.IO) {
            FileOutputStream(outFile).use { fos ->
              bmpToSave.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
          }
          if (mode == "VISUAL") bmpToSave.recycle()
          bmpPath = outFile.absolutePath
        }
      }

      val existingRecord = if (_editingId.value > 0L) dao.getById(_editingId.value) else null
      val entity = WidgetProjectEntity(
        id = _editingId.value,
        name = cleanName,
        kind = mode,
        visualJson = visJson,
        htmlCode = _htmlCode.value,
        cssCode = _cssCode.value,
        jsCode = _jsCode.value,
        isActiveHomeWidget = activateOnHomeScreen || (existingRecord?.isActiveHomeWidget == true),
        renderedBitmapPath = bmpPath ?: existingRecord?.renderedBitmapPath,
        updatedAtMs = System.currentTimeMillis(),
      )
      val savedId = if (_editingId.value > 0L) {
        dao.update(entity)
        _editingId.value
      } else {
        dao.insert(entity).also { _editingId.value = it }
      }

      val targetWidgetId = _targetAppWidgetId.value
      if (activateOnHomeScreen) {
        if (targetWidgetId != null && targetWidgetId > 0) {
          instanceDao.upsert(
            WidgetInstanceEntity(
              appWidgetId = targetWidgetId,
              projectId = savedId,
              widthDp = _visualSpec.value.targetWidthDp,
              heightDp = _visualSpec.value.targetHeightDp,
              cachedBitmapPath = bmpPath,
              updatedAtMs = System.currentTimeMillis(),
            ),
          )
          val mgr = runCatching { AppWidgetManager.getInstance(app) }.getOrNull()
          if (mgr != null) {
            NexusCustomWidgetProvider.updateWidgets(app, mgr, intArrayOf(targetWidgetId))
          }
          _statusMessage.value = "Updated Home Slot #$targetWidgetId with '$cleanName'."
        } else {
          dao.clearActiveFlag()
          dao.markAsActive(savedId, bmpPath)
          NexusWidgetUpdater.refreshAll(app)
          _statusMessage.value = "Saved & activated '$cleanName' as default home widget."
        }
      } else {
        // Refresh any home instances already pointing to this edited project
        val bound = instanceDao.getByProjectId(savedId)
        if (bound.isNotEmpty()) {
          val mgr = runCatching { AppWidgetManager.getInstance(app) }.getOrNull()
          if (mgr != null) {
            NexusCustomWidgetProvider.updateWidgets(app, mgr, bound.map { it.appWidgetId }.toIntArray())
          }
        }
        _statusMessage.value = "Saved widget project '$cleanName'."
      }
    }
  }

  fun duplicateProject(project: WidgetProjectEntity) {
    viewModelScope.launch {
      dao.insert(
        project.copy(
          id = 0L,
          name = "${project.name} Copy",
          isActiveHomeWidget = false,
          updatedAtMs = System.currentTimeMillis(),
        )
      )
      _statusMessage.value = "Duplicated '${project.name}'."
    }
  }

  fun deleteProject(id: Long) {
    viewModelScope.launch {
      instanceDao.deleteByProjectId(id)
      dao.deleteById(id)
      if (_editingId.value == id) _editingId.value = 0L
      _statusMessage.value = "Deleted widget project."
    }
  }

  fun exportHtmlToUri(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      val spec = _visualSpec.value
      val assetsDir = File(getApplication<Application>().filesDir, NexusWidgetJsBridge.ASSETS_DIR)
      val doc = NexusWidgetJsBridge.composeHtmlDocument(
        rawHtml = _htmlCode.value,
        rawCss = _cssCode.value,
        rawJs = _jsCode.value,
        widthDp = spec.targetWidthDp,
        heightDp = spec.targetHeightDp,
        transparentBg = spec.htmlTransparent,
        assetsDir = assetsDir,
        isAutoSize = spec.sizePresetName.equals(WidgetSizePreset.AUTO.name, ignoreCase = true),
      )
      runCatching {
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
          out.write(doc.toByteArray())
        } ?: error("Cannot write destination")
      }.onSuccess {
        _statusMessage.value = "Exported HTML widget bundle."
      }.onFailure {
        _statusMessage.value = "Export failed: ${it.message}"
      }
    }
  }

  fun importHtmlFromUri(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        val raw = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
          it.bufferedReader().readText()
        } ?: error("Cannot read selected file")
        val styleMatches = Regex("<style[^>]*>([\\s\\S]*?)</style>", RegexOption.IGNORE_CASE).findAll(raw)
          .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
          .filter { it.isNotEmpty() }
          .toList()
        val scriptMatches = Regex("<script(?![^>]*\\bsrc=)[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE).findAll(raw)
          .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
          .filter { it.isNotEmpty() }
          .toList()
        val body = Regex("<body[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.getOrNull(1)
          ?.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
          ?.trim()
        Triple(
          body ?: raw,
          if (styleMatches.isNotEmpty()) styleMatches.joinToString("\n\n") else "",
          if (scriptMatches.isNotEmpty()) scriptMatches.joinToString("\n\n") else "",
        )
      }.onSuccess { (body, css, js) ->
        _editorMode.value = "HTML"
        _htmlCode.value = body
        _cssCode.value = css
        _jsCode.value = js
        _statusMessage.value = "Imported HTML/CSS/JS widget."
      }.onFailure {
        _statusMessage.value = "Import failed: ${it.message}"
      }
    }
  }

  fun clearMessage() {
    _statusMessage.value = null
  }

  private fun resolveDisplayName(app: Application, uri: Uri): String {
    runCatching {
      app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
          val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) return c.getString(idx)
        }
      }
    }
    return "asset_${System.currentTimeMillis()}"
  }

  private fun copyUriIntoDir(app: Application, uri: Uri, dirName: String, fileName: String): File? {
    return runCatching {
      val dir = File(app.filesDir, dirName).apply { if (!exists()) mkdirs() }
      val dest = File(dir, fileName)
      app.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
      } ?: return null
      dest
    }.getOrNull()
  }
}
