package com.upivoicealert

import com.upivoicealert.filter.NotificationFilter
import com.upivoicealert.utils.PackageNames
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterTest {

    private val filter = NotificationFilter(
        keywords = setOf("cashback", "offer", "reward", "scratch card", "reminder", "request money", "bill due")
    )

    @Test
    fun `passes known payment text from supported app`() {
        assertTrue(filter.isPaymentCandidate(PackageNames.PHONEPE, "You received ₹500 from Rahul"))
    }

    @Test
    fun `rejects unknown package`() {
        assertFalse(filter.isPaymentCandidate("com.whatsapp", "You received ₹500"))
    }

    @Test
    fun `drops promotional notification`() {
        assertFalse(filter.isPaymentCandidate(PackageNames.PHONEPE, "Get 20% cashback on your next bill"))
    }

    @Test
    fun `drops money request reminder`() {
        assertFalse(filter.isPaymentCandidate(PackageNames.PAYTM, "Reminder: collect payment of ₹400"))
    }
}