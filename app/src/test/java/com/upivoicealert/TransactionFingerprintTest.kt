package com.upivoicealert

import com.upivoicealert.data.repository.TransactionFingerprint
import com.upivoicealert.domain.model.ParseStatus
import com.upivoicealert.domain.model.Transaction
import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionFingerprintTest {

    @Test
    fun `fingerprint combines amount sender and type`() {
        val fp = TransactionFingerprint.compute(
            txn(amount = 10.0, sender = "RAHUL KUMAR", type = TransactionType.RECEIVED)
        )
        assertEquals("10|RAHUL KUMAR|RECEIVED", fp)
    }

    @Test
    fun `same payment normalized identically across sources`() {
        // GPay phrasing vs bank "received from" — different text, same underlying
        // payment, so the fingerprint must be identical.
        val gpay = TransactionFingerprint.compute(
            txn(amount = 10.0, sender = "PRIYA BRIJESH MISHRA", type = TransactionType.RECEIVED)
        )
        val bank = TransactionFingerprint.compute(
            txn(amount = 10.0, sender = "PRIYA BRIJESH MISHRA", type = TransactionType.RECEIVED)
        )
        assertEquals(gpay, bank)
    }

    @Test
    fun `sender normalization collapses case and separators`() {
        assertEquals(
            TransactionFingerprint.compute(txn(amount = 10.0, sender = "priya-mishra")),
            TransactionFingerprint.compute(txn(amount = 10.0, sender = "PRIYA MISHRA"))
        )
        assertEquals(
            TransactionFingerprint.compute(txn(amount = 10.0, sender = "Priya   Mishra")),
            TransactionFingerprint.compute(txn(amount = 10.0, sender = "PRIYA MISHRA"))
        )
    }

    @Test
    fun `different amounts produce different fingerprints`() {
        val ten = TransactionFingerprint.compute(txn(amount = 10.0, sender = "RAHUL"))
        val tenFifty = TransactionFingerprint.compute(txn(amount = 10.5, sender = "RAHUL"))
        val eleven = TransactionFingerprint.compute(txn(amount = 11.0, sender = "RAHUL"))
        assertEquals("10|RAHUL|RECEIVED", ten)
        assertEquals("10.5|RAHUL|RECEIVED", tenFifty)
        assertEquals("11|RAHUL|RECEIVED", eleven)
    }

    @Test
    fun `different senders produce different fingerprints`() {
        val rahul = TransactionFingerprint.compute(txn(amount = 10.0, sender = "RAHUL"))
        val priya = TransactionFingerprint.compute(txn(amount = 10.0, sender = "PRIYA"))
        assertEquals("10|RAHUL|RECEIVED", rahul)
        assertEquals("10|PRIYA|RECEIVED", priya)
    }

    @Test
    fun `blank sender yields null fingerprint`() {
        assertNull(TransactionFingerprint.compute(txn(amount = 10.0, sender = "  ")))
        assertNull(TransactionFingerprint.compute(txn(amount = 10.0, sender = "")))
    }

    @Test
    fun `non positive amount yields null fingerprint`() {
        assertNull(TransactionFingerprint.compute(txn(amount = 0.0, sender = "RAHUL")))
        assertNull(TransactionFingerprint.compute(txn(amount = -5.0, sender = "RAHUL")))
    }

    private fun txn(
        amount: Double,
        sender: String,
        type: TransactionType = TransactionType.RECEIVED
    ) = Transaction(
        id = "t-$amount-$sender",
        amount = amount,
        sender = sender,
        upiApp = "TestApp",
        transactionType = type,
        status = TransactionStatus.SUCCESS,
        transactionId = null,
        rawNotification = "test",
        parserVersion = "TestParserV1",
        parseStatus = ParseStatus.PARSED,
        createdAt = 1_700_000_000_000L
    )
}