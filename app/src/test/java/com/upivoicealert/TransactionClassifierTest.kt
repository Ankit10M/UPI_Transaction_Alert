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
}