package com.example.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.NexusActionSpec
import com.example.core.NexusActionType
import com.example.data.local.EdgePanelConfig
import com.example.data.local.EdgeSide
import com.example.data.local.SharedShortcutEntity
import com.example.di.NexusContainer
import com.example.gesture.GestureClassifier
import com.example.gesture.GestureType
import com.example.ui.navigation.NexusRoutes
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native Edge Panel WindowManager overlay service.
 * Provides a configurable Left/Right edge handle with swipe-to-open and swipe-to-close gestures,
 * category tabs (All, Favorites, NEXUS, Apps, Combos), custom size/opacity/sensitivity,
 * shared shortcuts & multi-action execution, plus optional system-wide vertical edge gesture forwarding.
 */
class NexusEdgePanelService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var windowManager: WindowManager

  private var handleView: FrameLayout? = null
  private var handleParams: WindowManager.LayoutParams? = null

  private var panelContainer: LinearLayout? = null
  private var panelParams: WindowManager.LayoutParams? = null
  private var scrimView: View? = null

  private var config = EdgePanelConfig()
  private var shortcuts: List<SharedShortcutEntity> = emptyList()
  private var selectedCategory = "All"
  private var panelOpen = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    if (!Settings.canDrawOverlays(this)) {
      stopSelf()
      return
    }
    running.set(true)
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    startForegroundNotification()

    val container = NexusContainer.get(applicationContext)
    scope.launch {
      withContext(Dispatchers.IO) {
        container.sharedShortcutRepository.ensureSeededDefaults()
      }
      launch {
        container.advancedPreferences.edgePanelConfig.collectLatest { cfg ->
          config = cfg
          updateHandleLayout(cfg)
          if (panelOpen) renderEdgePanel()
        }
      }
      launch {
        container.sharedShortcutRepository.shortcuts.collectLatest { list ->
          shortcuts = list.filter { OverlaySurfaceFilter.matchesEdgePanel(it.targetSurface) }
          if (panelOpen) renderEdgePanel()
        }
      }
    }
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
    if (handleView == null) {
      attachEdgeHandle()
    }
    return START_STICKY
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun attachEdgeHandle() {
    val dp = resources.displayMetrics.density
    val root = FrameLayout(this)
    val pill = View(this).apply {
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#CCF44336"))
        cornerRadius = 999f
      }
    }
    root.addView(
      pill,
      FrameLayout.LayoutParams((5 * dp).toInt(), FrameLayout.LayoutParams.MATCH_PARENT).apply {
        gravity = Gravity.CENTER
      },
    )

    val params = WindowManager.LayoutParams(
      (config.handleWidthDp * dp).toInt(),
      (config.handleHeightDp * dp).toInt(),
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT,
    )
    applyGravityAndOffsets(params, config)

    var downX = 0f
    var downY = 0f
    var multiTouch = false

    root.setOnTouchListener { _, ev ->
      when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downX = ev.rawX
          downY = ev.rawY
          multiTouch = false
          true
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
          multiTouch = true
          true
        }
        MotionEvent.ACTION_UP -> {
          val dx = ev.rawX - downX
          val dy = ev.rawY - downY
          val swipeThresh = (32f * dp / config.sensitivity).coerceIn(14f, 140f)
          val pullInward = if (config.side == EdgeSide.RIGHT) dx <= -swipeThresh else dx >= swipeThresh
          val isTap = abs(dx) < 12f * dp && abs(dy) < 12f * dp

          if (pullInward || isTap) {
            openEdgePanel()
          } else if (config.systemGesturesAttached) {
            val detected = GestureClassifier.classifySwipe(
              dx = dx,
              dy = dy,
              pointerCount = if (multiTouch) 2 else 1,
              sensitivity = config.sensitivity,
              density = dp,
            )
            if (detected != null) {
              scope.launch(Dispatchers.IO) {
                NexusContainer.get(applicationContext).gestureRepository.dispatchDetectedGesture(
                  context = applicationContext,
                  detected = detected,
                  startX = if (config.side == EdgeSide.LEFT) 4f else resources.displayMetrics.widthPixels - 4f,
                  startY = downY,
                  widthPx = resources.displayMetrics.widthPixels.toFloat(),
                  heightPx = resources.displayMetrics.heightPixels.toFloat(),
                )
              }
            }
          }
          true
        }
        else -> false
      }
    }

    handleView = root
    handleParams = params
    runCatching { windowManager.addView(root, params) }
  }

  private fun applyGravityAndOffsets(params: WindowManager.LayoutParams, cfg: EdgePanelConfig) {
    val dp = resources.displayMetrics.density
    val screenH = resources.displayMetrics.heightPixels
    params.width = (cfg.handleWidthDp * dp).toInt()
    params.height = (cfg.handleHeightDp * dp).toInt()
    params.gravity = Gravity.TOP or if (cfg.side == EdgeSide.LEFT) Gravity.START else Gravity.END
    params.x = 0
    params.y = (screenH * cfg.verticalOffsetPct).toInt().coerceIn(80, (screenH - params.height - 80).coerceAtLeast(100))
  }

  private fun updateHandleLayout(cfg: EdgePanelConfig) {
    val hv = handleView ?: return
    val hp = handleParams ?: return
    applyGravityAndOffsets(hp, cfg)
    hv.alpha = cfg.opacity
    runCatching { windowManager.updateViewLayout(hv, hp) }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun openEdgePanel() {
    if (panelOpen) return
    panelOpen = true
    val dp = resources.displayMetrics.density

    val scrim = View(this).apply {
      setBackgroundColor(Color.parseColor("#4D000000"))
      setOnClickListener { closeEdgePanel() }
    }
    val scrimParams = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT,
    )
    scrimView = scrim
    runCatching { windowManager.addView(scrim, scrimParams) }

    val panel = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#F412151C"))
        cornerRadius = 20f * dp
        setStroke((1.2f * dp).toInt(), Color.parseColor("#33FFFFFF"))
      }
      setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
      alpha = config.opacity
    }

    var startX = 0f
    panel.setOnTouchListener { _, ev ->
      when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          startX = ev.rawX
          false
        }
        MotionEvent.ACTION_UP -> {
          val dx = ev.rawX - startX
          val closeSwipe = if (config.side == EdgeSide.RIGHT) dx >= 42f * dp else dx <= -42f * dp
          if (closeSwipe) {
            closeEdgePanel()
            true
          } else {
            false
          }
        }
        else -> false
      }
    }

    val panelParams = WindowManager.LayoutParams(
      (config.panelWidthDp * dp).toInt(),
      (resources.displayMetrics.heightPixels * 0.68f).toInt(),
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.CENTER_VERTICAL or if (config.side == EdgeSide.LEFT) Gravity.START else Gravity.END
      x = (8 * dp).toInt()
    }

    this.panelContainer = panel
    this.panelParams = panelParams
    renderEdgePanel()
    runCatching { windowManager.addView(panel, panelParams) }
  }

  private fun closeEdgePanel() {
    panelOpen = false
    panelContainer?.let { runCatching { windowManager.removeView(it) } }
    scrimView?.let { runCatching { windowManager.removeView(it) } }
    panelContainer = null
    scrimView = null
  }

  private fun renderEdgePanel() {
    val panel = panelContainer ?: return
    val dp = resources.displayMetrics.density
    panel.removeAllViews()

    val header = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    header.addView(TextView(this).apply {
      text = "Edge Panel"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      typeface = android.graphics.Typeface.DEFAULT_BOLD
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })
    val closeBtn = TextView(this).apply {
      text = "Close"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setPadding((10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt())
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#33E53935"))
        cornerRadius = 999f
      }
      setOnClickListener { closeEdgePanel() }
    }
    header.addView(closeBtn)
    panel.addView(header)

    // Category filter bar
    val catScroll = android.widget.HorizontalScrollView(this).apply {
      isHorizontalScrollBarEnabled = false
      setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
    }
    val catRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("All", "Favorites", "NEXUS", "Apps", "Quick Actions", "Combos").forEach { cat ->
      val active = selectedCategory == cat
      val chip = TextView(this).apply {
        text = cat
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setPadding((10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt())
        background = GradientDrawable().apply {
          setColor(Color.parseColor(if (active) "#E53935" else "#20FFFFFF"))
          cornerRadius = 999f
        }
        setOnClickListener {
          selectedCategory = cat
          renderEdgePanel()
        }
      }
      catRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        marginEnd = (6 * dp).toInt()
      })
    }
    catScroll.addView(catRow)
    panel.addView(catScroll)

    val filtered = when (selectedCategory) {
      "All" -> shortcuts
      "Favorites" -> shortcuts.filter { it.isFavorite }
      else -> shortcuts.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    val scroll = ScrollView(this).apply {
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    }
    val listBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    scroll.addView(listBody)
    panel.addView(scroll)

    val repo = NexusContainer.get(applicationContext).sharedShortcutRepository
    if (filtered.isEmpty()) {
      listBody.addView(TextView(this).apply {
        text = "No shortcuts in \"$selectedCategory\". Edit shortcuts in Edge Panel Studio."
        setTextColor(Color.parseColor("#B0B6C2"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
      })
      return
    }

    filtered.forEach { sc ->
      val specs = NexusActionSpec.parseList(sc.actionsJson)
      val appPkg = specs.firstOrNull { it.type == NexusActionType.LAUNCH_APP }?.param1
      val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
          setColor(Color.parseColor("#1CFFFFFF"))
          cornerRadius = 12f * dp
        }
        setPadding((10 * dp).toInt(), (9 * dp).toInt(), (10 * dp).toInt(), (9 * dp).toInt())
        setOnClickListener {
          scope.launch(Dispatchers.IO) {
            repo.executeShortcut(sc, "EDGE_PANEL")
          }
          closeEdgePanel()
        }
      }

      if (!appPkg.isNullOrBlank()) {
        val iconDrawable = runCatching { packageManager.getApplicationIcon(appPkg) }.getOrNull()
        if (iconDrawable != null) {
          row.addView(ImageView(this).apply {
            setImageDrawable(iconDrawable)
            layoutParams = LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).apply {
              marginEnd = (10 * dp).toInt()
            }
          })
        }
      }

      val texts = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }
      texts.addView(TextView(this).apply {
        text = (if (sc.isFavorite) "★ " else "") + sc.label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
      })
      val stepSuffix = if (specs.size > 1) " • ${specs.size} actions" else ""
      texts.addView(TextView(this).apply {
        text = "${sc.category}$stepSuffix"
        setTextColor(Color.parseColor("#A8B0BC"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        maxLines = 1
      })
      row.addView(texts)

      listBody.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = (7 * dp).toInt()
      })
    }
  }

  private fun startForegroundNotification() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val chId = "nexus_edge_panel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      nm.createNotificationChannel(
        NotificationChannel(chId, "NEXUS Edge Panel", NotificationManager.IMPORTANCE_MIN)
      )
    }
    val openIntent = PendingIntent.getActivity(
      this, 7251,
      Intent(this, MainActivity::class.java).putExtra("route", NexusRoutes.EDGE_PANEL),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val stopIntent = PendingIntent.getService(
      this, 7252,
      Intent(this, NexusEdgePanelService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notif: Notification = NotificationCompat.Builder(this, chId)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("NEXUS Edge Panel")
      .setContentText("Edge handle active (${config.side.name.lowercase()} side)")
      .setContentIntent(openIntent)
      .addAction(0, "Stop", stopIntent)
      .setOngoing(true)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(7251, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(7251, notif)
    }
  }

  override fun onDestroy() {
    running.set(false)
    closeEdgePanel()
    handleView?.let { runCatching { windowManager.removeView(it) } }
    handleView = null
    scope.cancel()
    super.onDestroy()
  }

  companion object {
    private const val ACTION_STOP = "com.example.action.STOP_EDGE_PANEL"
    private val running = AtomicBoolean(false)

    fun isRunning(): Boolean = running.get()

    fun start(context: Context) {
      val intent = Intent(context.applicationContext, NexusEdgePanelService::class.java)
      ContextCompat.startForegroundService(context.applicationContext, intent)
    }

    fun stop(context: Context) {
      context.applicationContext.stopService(
        Intent(context.applicationContext, NexusEdgePanelService::class.java)
      )
    }
  }
}
