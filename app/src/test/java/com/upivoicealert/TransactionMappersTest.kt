package com.upivoicealert

import com.upivoicealert.data.database.TransactionEntity
import com.upivoicealert.data.model.toDomain
import com.upivoicealert.data.model.toEntity
import com.upivoicealert.domain.model.NotificationSource
import com.upivoicealert.domain.model.ParseStatus
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.utils.PackageNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionMappersTest {

    private val baseEntity = TransactionEntity(
        id = "txn-1",
        amount = 10.0,
        sender = "ANKIT KUMAR BRIJESH SHANKA MISHRA",
        upiApp = "Google Pay",
        transactionType = TransactionType.RECEIVED.name,
        status = TransactionStatus.SUCCESS.name,
        transactionId = null,
        rawNotification = "ANKIT KUMAR BRIJESH SHANKA MISHRA paid you ₹10.00",
        parserVersion = "GPayParserV1",
        parseStatus = ParseStatus.PARSED.name,
        createdAt = 1_700_000_000_000L,
        sourceType = NotificationSource.UPI_APP.name,
        packageName = PackageNames.GPAY,
        notificationKey = "0|com.google.android.apps.nbu.paisa.user|0|12345",
        originalNotificationText = "ANKIT KUMAR BRIJESH SHANKA MISHRA paid you ₹10.00 Tap to view. Tap to view.",
        cleanedNotificationText = "ANKIT KUMAR BRIJESH SHANKA MISHRA paid you ₹10.00",
        voiceAnnounced = true
    )

    @Test
    fun `entity to domain maps multi source fields`() {
        val domain = baseEntity.toDomain()
        assertEquals(NotificationSource.UPI_APP, domain.sourceType)
        assertEquals(PackageNames.GPAY, domain.packageName)
        assertEquals("0|com.google.android.apps.nbu.paisa.user|0|12345", domain.notificationKey)
        assertEquals(
            "ANKIT KUMAR BRIJESH SHANKA MISHRA paid you ₹10.00 Tap to view. Tap to view.",
            domain.originalNotificationText
        )
        assertEquals("ANKIT KUMAR BRIJESH SHANKA MISHRA paid you ₹10.00", domain.cleanedNotificationText)
        // Legacy field keeps its semantic: cleaned text.
        assertEquals(domain.cleanedNotificationText, domain.rawNotification)
        assertEquals(true, domain.voiceAnnounced)
    }

    @Test
    fun `domain to entity round trips all fields`() {
        val domain = baseEntity.toDomain()
        assertEquals(baseEntity, domain.toEntity())
    }

    @Test
    fun `legacy defaults survive round trip`() {
        val legacy = Transaction(
            id = "txn-old",
            amount = 500.0,
            sender = "PRIYA",
            upiApp = "PhonePe",
            transactionType = TransactionType.RECEIVED,
            status = TransactionStatus.SUCCESS,
            transactionId = null,
            rawNotification = "PRIYA paid you ₹500",
            parserVersion = "GenericReceivedParserV1",
            parseStatus = ParseStatus.PARSED,
            createdAt = 42L
        )
        val back = legacy.toEntity().toDomain()
        assertEquals(NotificationSource.UNKNOWN, back.sourceType)
        assertEquals("", back.packageName)
        assertNull(back.notificationKey)
        assertEquals("", back.originalNotificationText)
        assertEquals("", back.cleanedNotificationText)
        assertEquals(legacy.rawNotification, back.rawNotification)
        assertEquals(false, back.voiceAnnounced)
        // Legacy rows carry no fingerprint (schema v4 default).
        assertNull(back.dedupFingerprint)
    }

    @Test
    fun `dedup fingerprint survives domain entity round trip`() {
        val domain = baseEntity.toDomain().copy(dedupFingerprint = "10|PRIYA BRIJESH MISHRA|RECEIVED")
        val entity = domain.toEntity()
        assertEquals("10|PRIYA BRIJESH MISHRA|RECEIVED", entity.dedupFingerprint)
        assertEquals("10|PRIYA BRIJESH MISHRA|RECEIVED", entity.toDomain().dedupFingerprint)
    }
}
