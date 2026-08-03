package com.upivoicealert.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.upivoicealert.domain.usecases.ProcessTransactionUseCase
import com.upivoicealert.utils.PackageNames
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Captures system notifications, filters to known UPI packages only, and routes
 * candidates into the filter pipeline. Does NOT parse, classify or store anything
 * itself (CLAUDE.md Module 1).
 */
@AndroidEntryPoint
class UpiNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var processTransactionUseCase: ProcessTransactionUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected (possible OEM battery kill)")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        try {
            val packageName = sbn.packageName ?: return
            if (packageName !in PackageNames.ALL) return

            val rawText = extractRawText(sbn) ?: return
            val postTime = sbn.postTime
            serviceScope.launch {
                processTransactionUseCase.processNotification(packageName, rawText, postTime)
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
        return listOfNotNull(title, text, bigText).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "UpiListenerService"
    }
}