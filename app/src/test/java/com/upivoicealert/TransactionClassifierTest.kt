package com.upivoicealert

import com.upivoicealert.domain.model.TransactionStatus
import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.filter.TransactionClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionClassifierTest {

    private val classifier = TransactionClassifier()

    @Test
    fun `classifies received success`() {
        val result = classifier.classify("You received ₹500 from Rahul Kumar")
        assertEquals(TransactionType.RECEIVED, result.type)
        assertEquals(TransactionStatus.SUCCESS, result.status)
    }

    @Test
    fun `classifies credited as received`() {
        val result = classifier.classify("₹120 credited to your account")
        assertEquals(TransactionType.RECEIVED, result.type)
    }

    @Test
    fun `classifies sent transaction`() {
        val result = classifier.classify("You sent ₹300 to Priya")
        assertEquals(TransactionType.SENT, result.type)
    }

    @Test
    fun `classifies failed transaction`() {
        val result = classifier.classify("Your payment of ₹500 failed")
        assertEquals(TransactionStatus.FAILED, result.status)
    }

    @Test
    fun `classifies pending transaction`() {
        val result = classifier.classify("Payment processing, ₹200")
        assertEquals(TransactionStatus.PENDING, result.status)
    }

    @Test
    fun `defaults ambiguous text to sent - never announced as received`() {
        val result = classifier.classify("UPI update for your account")
        assertEquals(TransactionType.SENT, result.type)
    }

    @Test
    fun `paid you classifies as received`() {
        val result = classifier.classify("PRIYA paid you ₹10")
        assertEquals(TransactionType.RECEIVED, result.type)
    }

    @Test
    fun `paid you full name classifies as received`() {
        val result = classifier.classify("PRIYA BRIJESH MISHRA paid you ₹10.00")
        assertEquals(TransactionType.RECEIVED, result.type)
    }

    @Test
    fun `amount credited classifies as received`() {
        val result = classifier.classify("Amount credited to XX3434")
        assertEquals(TransactionType.RECEIVED, result.type)
    }

    @Test
    fun `got classifies as received`() {
        val result = classifier.classify("You got ₹200 from Rahul")
        assertEquals(TransactionType.RECEIVED, result.type)
    }

    @Test
    fun `got does not false-positive inside ordinary words`() {
        val result = classifier.classify("Your forgot password update")
        assertEquals(TransactionType.SENT, result.type)
    }

    @Test
    fun `debited classifies as sent`() {
        val result = classifier.classify("₹500 debited from your account")
        assertEquals(TransactionType.SENT, result.type)
    }

    @Test
    fun `paid to classifies as sent`() {
        val result = classifier.classify("You paid to PRIYA ₹300")
        assertEquals(TransactionType.SENT, result.type)
    }

    @Test
    fun `transferred classifies as sent`() {
        val result = classifier.classify("₹700 transferred from your account")
        assertEquals(TransactionType.SENT, result.type)
    }

    @Test
    fun `received takes priority over sent keywords`() {
        val result = classifier.classify("₹10 received after transfer")
        assertEquals(TransactionType.RECEIVED, result.type)
    }
}