package com.example.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.widget.engine.DiagnosticSeverity
import com.example.widget.engine.WidgetDiagnosticCenter
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Renders HTML/CSS/JS widgets headlessly offscreen at true pixel dimensions so dynamic clocks,
 * SVG charts, CSS keyframes, custom fonts, and live metrics render crisply onto home-screen RemoteViews.
 */
object HeadlessHtmlWidgetRenderer {

  private suspend fun awaitVisualSettled(webView: WebView, settleMs: Long = 240L) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val visualReady = CompletableDeferred<Unit>()
      runCatching {
        webView.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
          override fun onComplete(requestId: Long) {
            if (!visualReady.isCompleted) visualReady.complete(Unit)
          }
        })
      }.onFailure {
        if (!visualReady.isCompleted) visualReady.complete(Unit)
      }
      withTimeoutOrNull(700L) { visualReady.await() }
    }
    delay(settleMs)
  }

  @SuppressLint("SetJavaScriptEnabled")
  suspend fun renderHtmlToBitmap(
    context: Context,
    rawHtml: String,
    widthDp: Int,
    heightDp: Int,
    density: Float,
    transparentBg: Boolean,
    widgetId: Int = -1,
  ): Bitmap? {
    val renderPx = WidgetSizingEngine.computeRenderPixels(widthDp, heightDp, density)
    return withTimeoutOrNull(2600L) {
      withContext(Dispatchers.Main) {
        runCatching {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { WebView.enableSlowWholeDocumentDraw() }
          }
          val pageReady = CompletableDeferred<Unit>()
          val assetsDir = File(context.filesDir, NexusWidgetJsBridge.ASSETS_DIR).apply {
            if (!exists()) mkdirs()
          }
          val webView = WebView(context.applicationContext).apply {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setBackgroundColor(if (transparentBg) Color.TRANSPARENT else Color.BLACK)
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              allowFileAccess = true
              allowContentAccess = true
              useWideViewPort = true
              loadWithOverviewMode = true
              mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            val bridge = NexusWidgetJsBridge(
              context = context.applicationContext,
              widthDp = widthDp,
              heightDp = heightDp,
              density = density,
              widgetId = widgetId,
            )
            addJavascriptInterface(bridge, "NexusWidget")
            addJavascriptInterface(bridge, "NexusBridge")
            webChromeClient = object : WebChromeClient() {
              override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null && consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                  WidgetDiagnosticCenter.record(
                    widgetKey = "custom_studio",
                    category = "HTML_JS",
                    severity = DiagnosticSeverity.ERROR,
                    message = "HTML console error (line ${consoleMessage.lineNumber()}): ${consoleMessage.message()}",
                    widgetId = widgetId,
                  )
                }
                return super.onConsoleMessage(consoleMessage)
              }
            }
            webViewClient = object : WebViewClient() {
              override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                return !NexusWidgetJsBridge.isSafeWebViewUrl(url)
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                if (!pageReady.isCompleted) pageReady.complete(Unit)
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
              ) {
                WidgetDiagnosticCenter.record(
                  widgetKey = "custom_studio",
                  category = "HTML_RENDER",
                  severity = DiagnosticSeverity.WARN,
                  message = "WebView resource error: ${error?.description}",
                  widgetId = widgetId,
                )
              }

              override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                WidgetDiagnosticCenter.record(
                  widgetKey = "custom_studio",
                  category = "HTML_RENDER",
                  severity = DiagnosticSeverity.ERROR,
                  message = "WebView render process exited (crashed=${detail?.didCrash() == true})",
                  widgetId = widgetId,
                )
                return true
              }
            }
          }

          val wSpec = View.MeasureSpec.makeMeasureSpec(renderPx.widthPx, View.MeasureSpec.EXACTLY)
          val hSpec = View.MeasureSpec.makeMeasureSpec(renderPx.heightPx, View.MeasureSpec.EXACTLY)
          webView.measure(wSpec, hSpec)
          webView.layout(0, 0, renderPx.widthPx, renderPx.heightPx)

          val wrapped = NexusWidgetJsBridge.wrapHtmlDocument(rawHtml, widthDp, heightDp, transparentBg, assetsDir)
          webView.loadDataWithBaseURL(
            "file://${assetsDir.absolutePath}/",
            wrapped,
            "text/html",
            "UTF-8",
            null,
          )

          withTimeoutOrNull(1400L) { pageReady.await() }
          awaitVisualSettled(webView, settleMs = 260L)

          val bitmap = Bitmap.createBitmap(renderPx.widthPx, renderPx.heightPx, Bitmap.Config.ARGB_8888)
          val canvas = Canvas(bitmap)
          webView.draw(canvas)
          webView.destroy()
          bitmap
        }.onFailure { err ->
          WidgetDiagnosticCenter.record(
            widgetKey = "custom_studio",
            category = "HTML_RENDER",
            severity = DiagnosticSeverity.ERROR,
            message = "Headless HTML render failed: ${err.message}",
            widgetId = widgetId,
          )
        }.getOrNull()
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  suspend fun renderHtmlAnimationFrames(
    context: Context,
    rawHtml: String,
    widthDp: Int,
    heightDp: Int,
    density: Float,
    transparentBg: Boolean,
    frameCount: Int = 8,
    intervalMs: Int = 180,
    widgetId: Int = -1,
  ): List<Bitmap> {
    val count = frameCount.coerceIn(2, 12)
    val stepMs = intervalMs.coerceIn(80, 500).toLong()
    val cappedDensity = density.coerceAtMost(2.0f)
    val renderPx = WidgetSizingEngine.computeRenderPixels(widthDp, heightDp, cappedDensity)
    return withTimeoutOrNull(5200L) {
      withContext(Dispatchers.Main) {
        val frames = mutableListOf<Bitmap>()
        runCatching {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { WebView.enableSlowWholeDocumentDraw() }
          }
          val pageReady = CompletableDeferred<Unit>()
          val assetsDir = File(context.filesDir, NexusWidgetJsBridge.ASSETS_DIR).apply {
            if (!exists()) mkdirs()
          }
          val webView = WebView(context.applicationContext).apply {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setBackgroundColor(if (transparentBg) Color.TRANSPARENT else Color.BLACK)
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              allowFileAccess = true
              allowContentAccess = true
              useWideViewPort = true
              loadWithOverviewMode = true
              mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            val bridge = NexusWidgetJsBridge(
              context = context.applicationContext,
              widthDp = widthDp,
              heightDp = heightDp,
              density = cappedDensity,
              widgetId = widgetId,
            )
            addJavascriptInterface(bridge, "NexusWidget")
            addJavascriptInterface(bridge, "NexusBridge")
            webViewClient = object : WebViewClient() {
              override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                return !NexusWidgetJsBridge.isSafeWebViewUrl(url)
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                if (!pageReady.isCompleted) pageReady.complete(Unit)
              }
            }
          }

          val wSpec = View.MeasureSpec.makeMeasureSpec(renderPx.widthPx, View.MeasureSpec.EXACTLY)
          val hSpec = View.MeasureSpec.makeMeasureSpec(renderPx.heightPx, View.MeasureSpec.EXACTLY)
          webView.measure(wSpec, hSpec)
          webView.layout(0, 0, renderPx.widthPx, renderPx.heightPx)

          val wrapped = NexusWidgetJsBridge.wrapHtmlDocument(rawHtml, widthDp, heightDp, transparentBg, assetsDir)
          webView.loadDataWithBaseURL(
            "file://${assetsDir.absolutePath}/",
            wrapped,
            "text/html",
            "UTF-8",
            null,
          )

          withTimeoutOrNull(1400L) { pageReady.await() }
          awaitVisualSettled(webView, settleMs = 200L)

          for (i in 0 until count) {
            val bmp = Bitmap.createBitmap(renderPx.widthPx, renderPx.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            webView.draw(canvas)
            frames.add(bmp)
            if (i < count - 1) delay(stepMs)
          }
          webView.destroy()
        }
        frames
      }
    } ?: emptyList()
  }
}
