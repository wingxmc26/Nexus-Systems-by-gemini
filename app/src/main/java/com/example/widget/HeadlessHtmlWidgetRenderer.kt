package com.example.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Renders HTML/CSS/JS widgets headlessly offscreen at true pixel dimensions so dynamic clocks,
 * metrics, and responsive layouts update cleanly on home screen without needing the app open.
 */
object HeadlessHtmlWidgetRenderer {

  @SuppressLint("SetJavaScriptEnabled")
  suspend fun renderHtmlToBitmap(
    context: Context,
    rawHtml: String,
    widthDp: Int,
    heightDp: Int,
    density: Float,
    transparentBg: Boolean,
  ): Bitmap? {
    val renderPx = WidgetSizingEngine.computeRenderPixels(widthDp, heightDp, density)
    return withTimeoutOrNull(1400L) {
      withContext(Dispatchers.Main) {
        runCatching {
          val pageReady = CompletableDeferred<Unit>()
          val webView = WebView(context.applicationContext).apply {
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
            val bridge = NexusWidgetJsBridge(context.applicationContext, widthDp, heightDp, density)
            addJavascriptInterface(bridge, "NexusWidget")
            addJavascriptInterface(bridge, "NexusBridge")
            webViewClient = object : WebViewClient() {
              override fun onPageFinished(view: WebView?, url: String?) {
                if (!pageReady.isCompleted) pageReady.complete(Unit)
              }
            }
          }

          val wSpec = View.MeasureSpec.makeMeasureSpec(renderPx.widthPx, View.MeasureSpec.EXACTLY)
          val hSpec = View.MeasureSpec.makeMeasureSpec(renderPx.heightPx, View.MeasureSpec.EXACTLY)
          webView.measure(wSpec, hSpec)
          webView.layout(0, 0, renderPx.widthPx, renderPx.heightPx)

          val assetsDir = File(context.filesDir, NexusWidgetJsBridge.ASSETS_DIR).apply {
            if (!exists()) mkdirs()
          }
          val wrapped = NexusWidgetJsBridge.wrapHtmlDocument(rawHtml, widthDp, heightDp, transparentBg)
          webView.loadDataWithBaseURL(
            "file://${assetsDir.absolutePath}/",
            wrapped,
            "text/html",
            "UTF-8",
            null,
          )

          withTimeoutOrNull(900L) { pageReady.await() }
          delay(140L)

          val bitmap = Bitmap.createBitmap(renderPx.widthPx, renderPx.heightPx, Bitmap.Config.ARGB_8888)
          val canvas = Canvas(bitmap)
          webView.draw(canvas)
          webView.destroy()
          bitmap
        }.getOrNull()
      }
    }
  }
}
