package com.upivoicealert

import com.upivoicealert.filter.NotificationFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterTest {

    private val filter = NotificationFilter(
        keywords = setOf("cashback", "offer", "reward", "scratch card", "reminder", "request money", "bill due"),
        financialSignals = setOf(
            "received", "credited", "credit", "deposited", "transaction", "upi",
            "inr", "₹", "rs", "amount", "payment", "bank", "account", "balance"
        ),
        blockedPackages = setOf("com.whatsapp", "com.instagram.android", "com.google.android.youtube")
    )

    @Test
    fun `passes received payment text from supported app`() {
        assertTrue(filter.isPaymentCandidate("com.phonepe.app", "You received ₹500 from Rahul"))
    }

    @Test
    fun `passes financial text from unknown package - source agnostic`() {
        // Kotak 811-style banking notification: no whitelist, financial signal present
        assertTrue(filter.isPaymentCandidate("com.kotak811mobilebankingapp", "₹500 credited to your account"))
    }

    @Test
    fun `passes financial text using rs keyword`() {
        assertTrue(filter.isPaymentCandidate("com.example.bank", "Rs 500 deposited successfully"))
    }

    @Test
    fun `passes financial text using rs with period`() {
        assertTrue(filter.isPaymentCandidate("com.example.bank", "Rs. 500 transferred"))
    }

    @Test
    fun `passes financial text using trailing rs`() {
        assertTrue(filter.isPaymentCandidate("com.example.bank", "500 rs transferred"))
    }

    @Test
    fun `rs keyword does not false-positive inside ordinary words`() {
        assertFalse(filter.isPaymentCandidate("com.example.news", "years of transfers updates"))
    }

    @Test
    fun `rejects notification from obvious non-financial app even with financial text`() {
        assertFalse(filter.isPaymentCandidate("com.whatsapp", "You received ₹500 from Rahul"))
    }

    @Test
    fun `drops promotional notification`() {
        assertFalse(filter.isPaymentCandidate("com.phonepe.app", "Get 20% cashback on your next bill"))
    }

    @Test
    fun `drops money request reminder`() {
        assertFalse(filter.isPaymentCandidate("net.one97.paytm", "Reminder: collect payment of ₹400"))
    }

    @Test
    fun `rejects non-financial text from unknown package - no financial signal`() {
        assertFalse(filter.isPaymentCandidate("com.example.news", "Breaking news: weather update today"))
    }
}
