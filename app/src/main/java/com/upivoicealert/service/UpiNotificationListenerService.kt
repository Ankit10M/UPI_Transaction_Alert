package com.upivoicealert.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.upivoicealert.domain.usecases.ProcessTransactionUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * TEMPORARY DEBUGGING BUILD:
 * - Logs everything under TAG "UPI_DEBUG".
 * - Captures ALL notifications (package filtering disabled on purpose).
 * - Does NOT parse, classify or store anything itself.
 */
@AndroidEntryPoint
class UpiNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var processTransactionUseCase: ProcessTransactionUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "onListenerConnected: Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "onListenerDisconnected: Notification listener disconnected (possible OEM battery kill)")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        try {
            val packageName = sbn.packageName ?: ""
            val key = sbn.key
            Log.i(TAG, "onNotificationPosted: package=$packageName key=$key")

            val extras = sbn.notification?.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val textLines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString(" | ")

            Log.i(TAG, "onNotificationPosted: title=$title")
            Log.i(TAG, "onNotificationPosted: text=$text")
            Log.i(TAG, "onNotificationPosted: bigText=$bigText")
            Log.i(TAG, "onNotificationPosted: textLines=$textLines")

            val rawText = extractRawText(sbn) ?: ""
            Log.i(TAG, "Captured package: $packageName")
            Log.i(TAG, "Captured text: $rawText")
            if (rawText.isBlank()) return

            val postTime = sbn.postTime
            serviceScope.launch {
                processTransactionUseCase.processNotification(packageName, rawText, postTime, sbn.key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
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
        const val TAG = "UPI_DEBUG"
    }
}