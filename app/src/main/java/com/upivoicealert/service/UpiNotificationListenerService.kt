package com.upivoicealert.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.upivoicealert.domain.usecases.ProcessTransactionUseCase
import com.upivoicealert.logging.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * UPI Notification Listener — Phase 8.3 hardened.
 *
 * Lifecycle: Android-managed NotificationListenerService. The system binds this
 * service when `enabled_notification_listeners` contains our component; it
 * automatically re-binds after process death and after reboot (no BOOT_COMPLETED
 * receiver required). Do NOT add a foreground service, WakeLock or AlarmManager
 * merely to "keep it alive" — NLS already provides the correct platform lifecycle
 * for notification delivery. See Phase 8.3 investigation report.
 *
 * Idempotency: `onListenerConnected` may fire multiple times (re-bind, settings
 * toggle, process recreation). This implementation is stateless aside from the
 * coroutine scope — no duplicate pipelines are created.
 *
 * Scope: `serviceScope` (SupervisorJob + IO) is cancelled in `onDestroy` so no
 * coroutine leaks after service destruction and no long-running job keeps the
 * process alive.
 *
 * Processing: `onNotificationPosted` extracts minimal data (package, rawText,
 * postTime, key) and launches `ProcessTransactionUseCase.processNotification`
 * off the callback thread. No network, blocking DB, or TTS is performed
 * synchronously in the callback.
 */
@AndroidEntryPoint
class UpiNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var processTransactionUseCase: ProcessTransactionUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "onCreate: service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AppLogger.d(TAG, "onListenerConnected: Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AppLogger.w(TAG, "onListenerDisconnected: Notification listener disconnected (possible OEM battery kill)")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        try {
            val packageName = sbn.packageName ?: ""
            val rawText = extractRawText(sbn) ?: ""
            // Operational debug: package + length only; full notification content
            // is NOT logged even in debug to avoid leaking UPI/VPA/phone/amount
            // in logcat (Sec 34). Use the unparsed-notification queue for diagnostics.
            if (AppLogger.isVerboseEnabled) {
                AppLogger.d(TAG, "onNotificationPosted: package=$packageName key=${sbn.key} rawLen=${rawText.length}")
            }
            if (rawText.isBlank()) return

            val postTime = sbn.postTime
            serviceScope.launch {
                processTransactionUseCase.processNotification(packageName, rawText, postTime, sbn.key)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error handling notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        AppLogger.d(TAG, "Notification removed: ${sbn.packageName}")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun extractRawText(sbn: StatusBarNotification): String? {
        val extras = sbn.notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ")
        return listOfNotNull(title, text, bigText, textLines).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "SHOUTPAY_NOTIFICATION_DEBUG"
    }
}