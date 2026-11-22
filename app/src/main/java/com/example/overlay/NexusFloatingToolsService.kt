package com.example.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.NexusActionSpec
import com.example.core.NexusActionType
import com.example.data.local.FloatingToolsConfig
import com.example.data.local.QuickNoteEntity
import com.example.data.local.SharedShortcutEntity
import com.example.di.NexusContainer
import com.example.ui.navigation.NexusRoutes
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native Floating Tools Overlay Service using WindowManager TYPE_APPLICATION_OVERLAY.
 * Supports draggable bubble, smooth animated edge snapping, custom size & opacity,
 * Quick Notes, Calculator, Timer/Stopwatch, Freeform Multi-Window App Launcher,
 * Floating Mini-Browser WebView, Shared Shortcuts, and NEXUS modules.
 */
class NexusFloatingToolsService : Service() {

  private data class FloatingLaunchApp(
    val label: String,
    val packageName: String,
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var windowManager: WindowManager

  private var bubbleView: FrameLayout? = null
  private var bubbleParams: WindowManager.LayoutParams? = null
  private var panelView: LinearLayout? = null
  private var panelParams: WindowManager.LayoutParams? = null
  private var floatingWebView: WebView? = null

  private var currentConfig = FloatingToolsConfig()
  private var cachedShortcuts: List<SharedShortcutEntity> = emptyList()
  private var cachedNotes: List<QuickNoteEntity> = emptyList()
  private var cachedLaunchableApps: List<FloatingLaunchApp> = emptyList()
  private var appSearchQuery = ""
  private var activeTab = "SHORTCUTS"
  private var editingNoteId: Long = 0L
  private var calcExpr = ""
  private var calcResultText = "0"
  private var timerTickerJob: Job? = null
  private var isExpanded = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    if (!Settings.canDrawOverlays(this)) {
      stopSelf()
      return
    }
    running.set(true)
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    startForegroundOverlayNotification()

    val container = NexusContainer.get(applicationContext)
    scope.launch {
      withContext(Dispatchers.IO) {
        container.sharedShortcutRepository.ensureSeededDefaults()
        loadLaunchableAppsCache()
      }
      launch {
        container.advancedPreferences.floatingToolsConfig.collectLatest { cfg ->
          currentConfig = cfg
          applyBubbleAppearance(cfg)
          if (isExpanded) {
            panelView?.alpha = cfg.opacity
          }
        }
      }
      launch {
        container.sharedShortcutRepository.shortcuts.collectLatest { list ->
          cachedShortcuts = list.filter { OverlaySurfaceFilter.matchesFloatingTools(it.targetSurface) }
          if (isExpanded && activeTab == "SHORTCUTS") renderPanelContent()
        }
      }
      launch {
        container.sharedShortcutRepository.notes.collectLatest { notes ->
          cachedNotes = notes
          if (isExpanded && activeTab == "NOTES") renderPanelContent()
        }
      }
    }
  }

  private fun loadLaunchableAppsCache() {
    val pm = packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
      @Suppress("DEPRECATION")
      pm.queryIntentActivities(launcherIntent, 0)
    }
    cachedLaunchableApps = resolved
      .mapNotNull { info ->
        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
        if (pkg == packageName) return@mapNotNull null
        val lbl = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
        FloatingLaunchApp(label = lbl, packageName = pkg)
      }
      .distinctBy { it.packageName }
      .sortedBy { it.label.lowercase() }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (!Settings.canDrawOverlays(this)) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (bubbleView == null) {
      attachBubble()
    }
    return START_STICKY
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun attachBubble() {
    val dp = resources.displayMetrics.density
    val sizePx = (currentConfig.bubbleSizeDp * dp).toInt()
    val containerView = FrameLayout(this).apply {
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#E616181F"))
        setStroke((2f * dp).toInt(), Color.parseColor("#E53935"))
      }
      elevation = 10f * dp
      alpha = currentConfig.opacity
    }

    val label = TextView(this).apply {
      text = "NX"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      gravity = Gravity.CENTER
      typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    containerView.addView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

    val params = WindowManager.LayoutParams(
      sizePx,
      sizePx,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = currentConfig.bubbleX
      y = currentConfig.bubbleY
    }

    var downRawX = 0f
    var downRawY = 0f
    var startX = 0
    var startY = 0
    var dragged = false

    containerView.setOnTouchListener { _, event ->
      val p = bubbleParams ?: return@setOnTouchListener false
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downRawX = event.rawX
          downRawY = event.rawY
          startX = p.x
          startY = p.y
          dragged = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = (event.rawX - downRawX).toInt()
          val dy = (event.rawY - downRawY).toInt()
          if (abs(dx) > 8 || abs(dy) > 8) dragged = true
          val maxH = (resources.displayMetrics.heightPixels - p.height - 40).coerceAtLeast(100)
          p.x = startX + dx
          p.y = (startY + dy).coerceIn(60, maxH)
          runCatching { windowManager.updateViewLayout(containerView, p) }
          true
        }
        MotionEvent.ACTION_UP -> {
          if (!dragged) {
            toggleExpandedPanel()
          } else {
            val targetX = if (currentConfig.edgeSnapEnabled) {
              val screenW = resources.displayMetrics.widthPixels
              val mid = p.x + p.width / 2
              if (mid < screenW / 2) (10 * dp).toInt() else (screenW - p.width - (10 * dp).toInt())
            } else {
              p.x
            }
            animateBubbleSnap(containerView, p, targetX)
          }
          true
        }
        else -> false
      }
    }

    bubbleView = containerView
    bubbleParams = params
    runCatching { windowManager.addView(containerView, params) }
  }

  private fun animateBubbleSnap(view: View, params: WindowManager.LayoutParams, targetX: Int) {
    val startX = params.x
    val finalY = params.y
    ValueAnimator.ofInt(startX, targetX).apply {
      duration = 200L
      interpolator = DecelerateInterpolator()
      addUpdateListener { anim ->
        params.x = anim.animatedValue as Int
        runCatching { windowManager.updateViewLayout(view, params) }
      }
      start()
    }
    scope.launch(Dispatchers.IO) {
      NexusContainer.get(applicationContext).advancedPreferences.updateFloatingToolsConfig {
        it.copy(bubbleX = targetX, bubbleY = finalY)
      }
    }
  }

  private fun applyBubbleAppearance(cfg: FloatingToolsConfig) {
    val v = bubbleView ?: return
    val p = bubbleParams ?: return
    val sizePx = (cfg.bubbleSizeDp * resources.displayMetrics.density).toInt()
    p.width = sizePx
    p.height = sizePx
    v.alpha = cfg.opacity
    runCatching { windowManager.updateViewLayout(v, p) }
  }

  private fun toggleExpandedPanel() {
    if (isExpanded) collapsePanel() else expandPanel()
  }

  private fun setPanelFocusable(focusable: Boolean) {
    val view = panelView ?: return
    val p = panelParams ?: return
    val nextFlags = if (focusable) {
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    } else {
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    }
    if (p.flags != nextFlags) {
      p.flags = nextFlags
      runCatching { windowManager.updateViewLayout(view, p) }
    }
  }

  private fun expandPanel() {
    if (isExpanded) return
    isExpanded = true
    val dp = resources.displayMetrics.density
    val widthPx = (resources.displayMetrics.widthPixels * 0.88f).toInt().coerceIn((280 * dp).toInt(), (400 * dp).toInt())
    val heightPx = (resources.displayMetrics.heightPixels * 0.62f).toInt().coerceIn((340 * dp).toInt(), (540 * dp).toInt())

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#F2111319"))
        cornerRadius = 18f * dp
        setStroke((1.5f * dp).toInt(), Color.parseColor("#33FFFFFF"))
      }
      setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
      alpha = currentConfig.opacity
    }

    activeTab = pickDefaultAvailableTab()
    val needsInputFocus = activeTab in setOf("NOTES", "APPS", "BROWSER")
    val initialFlags = if (needsInputFocus) {
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    } else {
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    }

    val params = WindowManager.LayoutParams(
      widthPx,
      heightPx,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      initialFlags,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.CENTER
      softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    panelView = root
    panelParams = params
    renderPanelContent()
    runCatching { windowManager.addView(root, params) }
  }

  private fun collapsePanel() {
    isExpanded = false
    timerTickerJob?.cancel()
    timerTickerJob = null
    floatingWebView?.let { wv ->
      runCatching {
        wv.stopLoading()
        wv.destroy()
      }
    }
    floatingWebView = null
    panelView?.let { runCatching { windowManager.removeView(it) } }
    panelView = null
    panelParams = null
  }

  private fun pickDefaultAvailableTab(): String {
    val sel = currentConfig.selectedTools
    return when {
      "SHORTCUTS" in sel -> "SHORTCUTS"
      "APPS" in sel -> "APPS"
      "BROWSER" in sel -> "BROWSER"
      "NOTES" in sel -> "NOTES"
      "CALCULATOR" in sel -> "CALCULATOR"
      "TIMER" in sel -> "TIMER"
      "MODULES" in sel -> "MODULES"
      else -> "SHORTCUTS"
    }
  }

  private fun renderPanelContent() {
    val root = panelView ?: return
    val dp = resources.displayMetrics.density
    root.removeAllViews()
    timerTickerJob?.cancel()

    val needsInput = activeTab in setOf("NOTES", "APPS", "BROWSER")
    setPanelFocusable(needsInput)

    // Header bar
    val header = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    val title = TextView(this).apply {
      text = "NEXUS Floating Tools"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      typeface = android.graphics.Typeface.DEFAULT_BOLD
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = createPillButton("Collapse", "#26E53935") { collapsePanel() }
    header.addView(title)
    header.addView(closeBtn)
    root.addView(header)

    // Tool selector bar honoring user's selected tools
    val tabsScroll = android.widget.HorizontalScrollView(this).apply {
      isHorizontalScrollBarEnabled = false
      setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
    }
    val tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val allTabs = listOf(
      "SHORTCUTS" to "Shortcuts",
      "APPS" to "Apps",
      "BROWSER" to "Browser",
      "NOTES" to "Notes",
      "CALCULATOR" to "Calc",
      "TIMER" to "Timer",
      "MODULES" to "NEXUS",
    ).filter { it.first in currentConfig.selectedTools || currentConfig.selectedTools.isEmpty() }

    allTabs.forEach { (id, label) ->
      val bg = if (activeTab == id) "#E53935" else "#1FFFFFFF"
      val btn = createPillButton(label, bg) {
        if (activeTab == "BROWSER" && id != "BROWSER") {
          floatingWebView?.onPause()
        }
        activeTab = id
        renderPanelContent()
      }
      tabsRow.addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        marginEnd = (6 * dp).toInt()
      })
    }
    tabsScroll.addView(tabsRow)
    root.addView(tabsScroll)

    if (activeTab == "BROWSER") {
      val browserHost = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
      }
      root.addView(browserHost)
      renderBrowserTab(browserHost)
      return
    }

    val bodyContainer = ScrollView(this).apply {
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    }
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    bodyContainer.addView(body)
    root.addView(bodyContainer)

    when (activeTab) {
      "APPS" -> renderAppsTab(body)
      "NOTES" -> renderQuickNotesTab(body)
      "CALCULATOR" -> renderCalculatorTab(body)
      "TIMER" -> renderTimerTab(body)
      "MODULES" -> renderNexusModulesTab(body)
      else -> renderShortcutsTab(body)
    }
  }

  private fun renderAppsTab(body: LinearLayout) {
    val dp = resources.displayMetrics.density
    body.addView(infoText("Tap any app to open it in Android Freeform multi-window bounds (falls back gracefully if OEM restricts freeform).").apply {
      setPadding(0, 0, 0, (6 * dp).toInt())
    })

    val searchInput = EditText(this).apply {
      hint = "Search installed apps..."
      setText(appSearchQuery)
      setSelection(text.length)
      setHintTextColor(Color.parseColor("#88FFFFFF"))
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setSingleLine(true)
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#18FFFFFF"))
        cornerRadius = 10f * dp
      }
      setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
    }
    body.addView(searchInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
      bottomMargin = (8 * dp).toInt()
    })

    val listHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    body.addView(listHost)

    fun populateList(query: String) {
      listHost.removeAllViews()
      if (cachedLaunchableApps.isEmpty()) {
        loadLaunchableAppsCache()
      }
      val q = query.trim().lowercase()
      val filtered = if (q.isBlank()) {
        cachedLaunchableApps.take(40)
      } else {
        cachedLaunchableApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }.take(40)
      }
      if (filtered.isEmpty()) {
        listHost.addView(infoText("No matching launchable apps found."))
        return
      }
      filtered.forEach { app ->
        val row = createListRow(app.label, "${app.packageName} • Freeform Window") {
          launchInFreeformWindow(app.packageName, app.label)
        }
        listHost.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
          bottomMargin = (6 * dp).toInt()
        })
      }
    }

    searchInput.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        appSearchQuery = s?.toString().orEmpty()
        populateList(appSearchQuery)
      }
      override fun afterTextChanged(s: Editable?) = Unit
    })

    populateList(appSearchQuery)
  }

  private fun launchInFreeformWindow(targetPkg: String, label: String) {
    val intent = packageManager.getLaunchIntentForPackage(targetPkg)
    if (intent == null) {
      Toast.makeText(this, "No launcher activity found for $label", Toast.LENGTH_SHORT).show()
      return
    }
    intent.addFlags(
      Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    )
    val metrics = resources.displayMetrics
    val w = metrics.widthPixels
    val h = metrics.heightPixels
    val left = (w * 0.10f).toInt()
    val top = (h * 0.14f).toInt()
    val right = (w * 0.90f).toInt()
    val bottom = (h * 0.72f).toInt()
    val freeformBounds = Rect(left, top, right, bottom)

    val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      ActivityOptions.makeBasic().apply {
        setLaunchBounds(freeformBounds)
        // WindowConfiguration.WINDOWING_MODE_FREEFORM = 5 (safe optional reflection for OEMs with Freeform enabled)
        runCatching {
          val m = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
          m.invoke(this, 5)
        }
      }
    } else null

    val launchedFreeform = runCatching {
      if (options != null) {
        startActivity(intent, options.toBundle())
      } else {
        startActivity(intent)
      }
      true
    }.getOrDefault(false)

    if (!launchedFreeform) {
      val fallbackOk = runCatching {
        val stdIntent = packageManager.getLaunchIntentForPackage(targetPkg)?.apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (stdIntent != null) startActivity(stdIntent)
        true
      }.getOrDefault(false)
      Toast.makeText(
        this,
        if (fallbackOk) "Opened $label (Freeform restricted by OEM)" else "Could not open $label",
        Toast.LENGTH_SHORT,
      ).show()
    } else {
      Toast.makeText(this, "Launching $label in Freeform bounds", Toast.LENGTH_SHORT).show()
    }
    collapsePanel()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun renderBrowserTab(host: LinearLayout) {
    val dp = resources.displayMetrics.density
    val toolbar = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }

    val urlInput = EditText(this).apply {
      hint = "URL or search query"
      setText(currentConfig.lastBrowserUrl)
      setHintTextColor(Color.parseColor("#88FFFFFF"))
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
      setSingleLine(true)
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#1FFFFFFF"))
        cornerRadius = 8f * dp
      }
      setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginStart = (4 * dp).toInt()
        marginEnd = (4 * dp).toInt()
      }
    }

    val wv = floatingWebView ?: WebView(applicationContext).apply {
      setBackgroundColor(Color.WHITE)
      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        useWideViewPort = true
        loadWithOverviewMode = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
      }
      webChromeClient = WebChromeClient()
      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
          val target = request?.url?.toString().orEmpty()
          val lower = target.lowercase()
          return !(lower.startsWith("https://") || lower.startsWith("http://"))
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          if (!url.isNullOrBlank()) {
            urlInput.setText(url)
            scope.launch(Dispatchers.IO) {
              NexusContainer.get(applicationContext).advancedPreferences.updateFloatingToolsConfig {
                it.copy(lastBrowserUrl = url)
              }
            }
          }
        }
      }
      loadUrl(normalizeBrowserUrl(currentConfig.lastBrowserUrl))
    }.also { floatingWebView = it }

    // Detach from prior parent before re-attaching
    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
    wv.onResume()

    val backBtn = createPillButton("‹", "#26FFFFFF") { if (wv.canGoBack()) wv.goBack() }
    val fwdBtn = createPillButton("›", "#26FFFFFF") { if (wv.canGoForward()) wv.goForward() }.apply {
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        marginStart = (4 * dp).toInt()
      }
    }
    val goBtn = createPillButton("Go", "#E53935") {
      val resolved = normalizeBrowserUrl(urlInput.text.toString())
      urlInput.setText(resolved)
      wv.loadUrl(resolved)
    }

    toolbar.addView(backBtn)
    toolbar.addView(fwdBtn)
    toolbar.addView(urlInput)
    toolbar.addView(goBtn)

    host.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
      bottomMargin = (6 * dp).toInt()
    })
    host.addView(wv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
  }

  private fun normalizeBrowserUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "https://duckduckgo.com"
    val lower = trimmed.lowercase()
    return when {
      lower.startsWith("https://") || lower.startsWith("http://") -> trimmed
      !trimmed.contains(" ") && trimmed.contains(".") -> "https://$trimmed"
      else -> "https://duckduckgo.com/?q=${android.net.Uri.encode(trimmed)}"
    }
  }

  private fun renderShortcutsTab(body: LinearLayout) {
    val dp = resources.displayMetrics.density
    if (cachedShortcuts.isEmpty()) {
      body.addView(infoText("No shared shortcuts yet. Add apps or combos inside Edge Panel / Floating Tools settings."))
      return
    }
    val repo = NexusContainer.get(applicationContext).sharedShortcutRepository
    cachedShortcuts.forEach { sc ->
      val row = createListRow(
        title = (if (sc.isFavorite) "★ " else "") + sc.label,
        subtitle = sc.category,
      ) {
        scope.launch(Dispatchers.IO) {
          repo.executeShortcut(sc, "FLOATING_TOOLS")
        }
        collapsePanel()
      }
      body.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = (6 * dp).toInt()
      })
    }
  }

  private fun renderQuickNotesTab(body: LinearLayout) {
    val dp = resources.displayMetrics.density
    val noteInput = EditText(this).apply {
      hint = "Write a quick note..."
      setHintTextColor(Color.parseColor("#88FFFFFF"))
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#18FFFFFF"))
        cornerRadius = 10f * dp
      }
      setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
      minLines = 2
    }
    body.addView(noteInput)

    val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val saveBtn = createPillButton(if (editingNoteId > 0L) "Update Note" else "Save Quick Note", "#E53935") {
      val txt = noteInput.text.toString().trim()
      if (txt.isNotBlank()) {
        val dao = NexusContainer.get(applicationContext).database.quickNoteDao()
        val idToSave = editingNoteId
        scope.launch(Dispatchers.IO) {
          dao.upsert(
            QuickNoteEntity(
              id = idToSave,
              title = txt.lineSequence().firstOrNull()?.take(36) ?: "Note",
              content = txt,
              updatedAtMs = System.currentTimeMillis(),
            )
          )
        }
        editingNoteId = 0L
        noteInput.setText("")
      }
    }
    btnRow.addView(saveBtn)
    if (editingNoteId > 0L) {
      btnRow.addView(
        createPillButton("New", "#26FFFFFF") {
          editingNoteId = 0L
          noteInput.setText("")
          renderPanelContent()
        }.apply {
          layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          ).apply { marginStart = (6 * dp).toInt() }
        }
      )
    }
    body.addView(btnRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
      topMargin = (8 * dp).toInt()
      bottomMargin = (10 * dp).toInt()
    })

    cachedNotes.forEach { n ->
      val rowContainer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }
      val card = createListRow(
        title = n.title,
        subtitle = n.content,
      ) {
        editingNoteId = n.id
        noteInput.setText(n.content)
      }.apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }
      val delBtn = createPillButton("Del", "#33E53935") {
        val dao = NexusContainer.get(applicationContext).database.quickNoteDao()
        if (editingNoteId == n.id) editingNoteId = 0L
        scope.launch(Dispatchers.IO) { dao.deleteById(n.id) }
      }.apply {
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = (6 * dp).toInt() }
      }
      rowContainer.addView(card)
      rowContainer.addView(delBtn)
      body.addView(rowContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = (6 * dp).toInt()
      })
    }
  }

  private fun renderCalculatorTab(body: LinearLayout) {
    val dp = resources.displayMetrics.density
    val display = TextView(this).apply {
      text = if (calcExpr.isBlank()) calcResultText else "$calcExpr\n= $calcResultText"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
      gravity = Gravity.END
      setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#18FFFFFF"))
        cornerRadius = 10f * dp
      }
    }
    body.addView(display, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
      bottomMargin = (8 * dp).toInt()
    })

    val rows = listOf(
      listOf("C", "(", ")", "÷"),
      listOf("7", "8", "9", "×"),
      listOf("4", "5", "6", "-"),
      listOf("1", "2", "3", "+"),
      listOf("0", ".", "⌫", "="),
    )

    fun refreshCalc() {
      calcResultText = if (calcExpr.isBlank()) {
        "0"
      } else {
        CalculatorEvaluator.evaluate(calcExpr)
          .map { CalculatorEvaluator.formatResult(it) }
          .getOrDefault("...")
      }
      display.text = if (calcExpr.isBlank()) calcResultText else "$calcExpr\n= $calcResultText"
    }

    rows.forEach { r ->
      val rowView = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
      r.forEach { key ->
        val isEq = key == "="
        val btn = Button(this).apply {
          text = key
          setTextColor(Color.WHITE)
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
          background = GradientDrawable().apply {
            setColor(Color.parseColor(if (isEq) "#E53935" else "#20FFFFFF"))
            cornerRadius = 8f * dp
          }
          setOnClickListener {
            when (key) {
              "C" -> {
                calcExpr = ""
                calcResultText = "0"
              }
              "⌫" -> calcExpr = calcExpr.dropLast(1)
              "=" -> {
                val evaluated = CalculatorEvaluator.evaluate(calcExpr)
                calcResultText = evaluated.map { CalculatorEvaluator.formatResult(it) }.getOrDefault("Error")
                if (evaluated.isSuccess) calcExpr = calcResultText
              }
              else -> calcExpr += key
            }
            if (key != "=") refreshCalc() else display.text = calcResultText
          }
        }
        rowView.addView(btn, LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1f).apply {
          setMargins((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
        })
      }
      body.addView(rowView)
    }
  }

  private fun renderTimerTab(body: LinearLayout) {
    val dp = resources.displayMetrics.density
    val swLabel = TextView(this).apply {
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val cdLabel = TextView(this).apply {
      setTextColor(Color.parseColor("#FF8A80"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    fun updateLabels() {
      val now = System.currentTimeMillis()
      val sw = OverlayTimerCoordinator.currentStopwatchMs(now)
      val cd = OverlayTimerCoordinator.currentCountdownMs(now)
      swLabel.text = "Stopwatch: ${OverlayTimerCoordinator.formatClockMs(sw)}"
      cdLabel.text = "Timer: ${OverlayTimerCoordinator.formatClockMs(cd)}"
    }
    updateLabels()

    body.addView(swLabel)
    val swRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    swRow.addView(createPillButton("Start/Pause", "#E53935") {
      OverlayTimerCoordinator.toggleStopwatch()
      updateLabels()
    })
    swRow.addView(createPillButton("Reset", "#26FFFFFF") {
      OverlayTimerCoordinator.resetStopwatch()
      updateLabels()
    }.apply {
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        marginStart = (8 * dp).toInt()
      }
    })
    body.addView(swRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
      topMargin = (6 * dp).toInt()
      bottomMargin = (14 * dp).toInt()
    })

    body.addView(cdLabel)
    val cdRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    cdRow.addView(createPillButton("Start/Pause", "#E53935") {
      OverlayTimerCoordinator.toggleCountdown()
      updateLabels()
    })
    cdRow.addView(createPillButton("3m", "#26FFFFFF") {
      OverlayTimerCoordinator.setCountdownPreset(180_000L)
      updateLabels()
    }.apply {
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        marginStart = (6 * dp).toInt()
      }
    })
    cdRow.addView(createPillButton("5m", "#26FFFFFF") {
      OverlayTimerCoordinator.setCountdownPreset(300_000L)
      updateLabels()
    }.apply {
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        marginStart = (6 * dp).toInt()
      }
    })
    body.addView(cdRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
      topMargin = (6 * dp).toInt()
    })

    timerTickerJob?.cancel()
    timerTickerJob = scope.launch {
      while (isActive && isExpanded && activeTab == "TIMER") {
        updateLabels()
        delay(100L)
      }
    }
  }

  private fun renderNexusModulesTab(body: LinearLayout) {
    val dp = resources.displayMetrics.density
    val modules = listOf(
      NexusRoutes.BATTERY to "Battery & Charging",
      NexusRoutes.APP_LOCK to "App Lock",
      NexusRoutes.SECURE_VAULT to "Secure Vault",
      NexusRoutes.GESTURE_STUDIO to "Gesture Studio",
      NexusRoutes.EDGE_PANEL to "Edge Panel",
      NexusRoutes.WIDGET_STUDIO to "Widget Studio",
      NexusRoutes.APP_MANAGER to "App Manager",
    )
    val dispatcher = NexusContainer.get(applicationContext).actionDispatcher
    modules.forEach { (route, label) ->
      val row = createListRow(label, "Open module in NEXUS") {
        scope.launch(Dispatchers.IO) {
          dispatcher.dispatchSingle(NexusActionSpec(NexusActionType.OPEN_NEXUS_MODULE, label, route))
        }
        collapsePanel()
      }
      body.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = (6 * dp).toInt()
      })
    }
  }

  private fun createPillButton(textValue: String, hexBg: String, onClick: () -> Unit): TextView {
    val dp = resources.displayMetrics.density
    return TextView(this).apply {
      text = textValue
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      typeface = android.graphics.Typeface.DEFAULT_BOLD
      background = GradientDrawable().apply {
        setColor(Color.parseColor(hexBg))
        cornerRadius = 999f
      }
      setPadding((12 * dp).toInt(), (7 * dp).toInt(), (12 * dp).toInt(), (7 * dp).toInt())
      setOnClickListener { onClick() }
    }
  }

  private fun createListRow(title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
    val dp = resources.displayMetrics.density
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#18FFFFFF"))
        cornerRadius = 10f * dp
      }
      setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
      addView(TextView(context).apply {
        text = title
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
      })
      addView(TextView(context).apply {
        text = subtitle
        setTextColor(Color.parseColor("#B0B6C2"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
      })
      setOnClickListener { onClick() }
    }
  }

  private fun infoText(msg: String): TextView {
    return TextView(this).apply {
      text = msg
      setTextColor(Color.parseColor("#B0B6C2"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
    }
  }

  private fun startForegroundOverlayNotification() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val chId = "nexus_floating_tools"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      nm.createNotificationChannel(
        NotificationChannel(chId, "NEXUS Floating Tools", NotificationManager.IMPORTANCE_MIN)
      )
    }
    val openIntent = PendingIntent.getActivity(
      this, 7201,
      Intent(this, MainActivity::class.java).putExtra("route", NexusRoutes.FLOATING_TOOLS),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val stopIntent = PendingIntent.getService(
      this, 7202,
      Intent(this, NexusFloatingToolsService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notif: Notification = NotificationCompat.Builder(this, chId)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("NEXUS Floating Tools")
      .setContentText("Bubble active • Apps, Browser, Notes, Calc & Timer")
      .setContentIntent(openIntent)
      .addAction(0, "Stop", stopIntent)
      .setOngoing(true)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(7201, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(7201, notif)
    }
  }

  override fun onDestroy() {
    running.set(false)
    collapsePanel()
    bubbleView?.let { runCatching { windowManager.removeView(it) } }
    bubbleView = null
    scope.cancel()
    super.onDestroy()
  }

  companion object {
    private const val ACTION_STOP = "com.example.action.STOP_FLOATING_TOOLS"
    private val running = AtomicBoolean(false)

    fun isRunning(): Boolean = running.get()

    fun start(context: Context) {
      val intent = Intent(context.applicationContext, NexusFloatingToolsService::class.java)
      ContextCompat.startForegroundService(context.applicationContext, intent)
    }

    fun stop(context: Context) {
      context.applicationContext.stopService(
        Intent(context.applicationContext, NexusFloatingToolsService::class.java)
      )
    }
  }
}
