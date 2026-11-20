package com.example.notifications

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.local.NotificationHistoryEntity
import com.example.di.NexusContainer
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NexusNotificationListenerService : NotificationListenerService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  companion object {
    @Volatile
    private var serviceRef: WeakReference<NexusNotificationListenerService>? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun dismissActiveNotification(key: String): Boolean {
      val svc = serviceRef?.get() ?: return false
      return runCatching {
        svc.cancelNotification(key)
        true
      }.getOrDefault(false)
    }
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    serviceRef = WeakReference(this)
    _isConnected.value = true
  }

  override fun onListenerDisconnected() {
    _isConnected.value = false
    serviceRef = null
    super.onListenerDisconnected()
  }

  override fun onBind(intent: Intent?): IBinder? {
    serviceRef = WeakReference(this)
    return super.onBind(intent)
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    if (sbn.packageName == applicationContext.packageName) return

    val extras = sbn.notification?.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
      ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
    val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

    if (title.isBlank() && text.isBlank()) return

    val pm = applicationContext.packageManager
    val appName = runCatching {
      val info = pm.getApplicationInfo(sbn.packageName, 0)
      pm.getApplicationLabel(info).toString()
    }.getOrDefault(sbn.packageName)

    val isOngoing = sbn.isOngoing || (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
    val category = sbn.notification.category ?: ""
    val channelId = sbn.notification.channelId ?: ""

    serviceScope.launch {
      runCatching {
        val dao = NexusContainer.get(applicationContext).database.notificationHistoryDao()
        dao.insert(
          NotificationHistoryEntity(
            notificationKey = sbn.key ?: "${sbn.packageName}_${sbn.id}",
            packageName = sbn.packageName,
            appName = appName,
            title = title.ifBlank { appName },
            text = text,
            subText = subText,
            channelId = channelId,
            category = category,
            postedAtMs = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
            isOngoing = isOngoing,
            isClearable = sbn.isClearable,
            isImportant = category == Notification.CATEGORY_CALL ||
              category == Notification.CATEGORY_MESSAGE ||
              category == Notification.CATEGORY_ALARM,
          )
        )
      }
    }
  }

  override fun onDestroy() {
    _isConnected.value = false
    serviceRef = null
    serviceScope.cancel()
    super.onDestroy()
  }
}
