package com.upivoicealert

import com.upivoicealert.filter.NotificationTextCleaner
import com.upivoicealert.parser.gpay.GPayParserV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextCleanerTest {

    private val cleaner = NotificationTextCleaner(
        promoKeywords = setOf(
            "cashback", "offer", "rewards", "reward", "scratch card", "reminder",
            "request money", "bill due", "win a", "lucky draw", "collect"
        )
    )

    @Test
    fun `cleans gpay sample to payment core`() {
        assertEquals(
            "ANKIT paid you ₹10.00",
            cleaner.clean("ANKIT paid you ₹10.00 Tap to view. Tap to view.")
        )
    }

    @Test
    fun `cleans real gpay notification with full sender name`() {
        assertEquals(
            "ANKIT KUMAR BRIJESH SHANKA MISHRA paid you ₹10.00",
            cleaner.clean("ANKIT KUMAR BRIJESH SHANKA MISHRA paid you ₹10.00 Tap to view. Tap to view.")
        )
    }

    @Test
    fun `removes http and www urls`() {
        assertEquals(
            "Received ₹500 from Rahul",
            cleaner.clean("Received ₹500 from Rahul https://gpay.app.goo.gl/xyz123")
        )
        assertEquals(
            "Received ₹500 from Rahul",
            cleaner.clean("Received ₹500 from Rahul www.gpay.co/abc")
        )
    }

    @Test
    fun `removes bare domain link but preserves merchant domain`() {
        // Bare merchant domain without a path is preserved, not treated as a URL.
        assertEquals(
            "Payment of ₹500 at paytm.com",
            cleaner.clean("Payment of ₹500 at paytm.com")
        )
        // A promotional link with a path is removed, and the promo fragment is
        // then dropped entirely as promotional text.
        assertEquals(
            "",
            cleaner.clean("Check offers at paytm.com/offers")
        )
    }

    @Test
    fun `collapses unnecessary whitespace`() {
        assertEquals(
            "Received ₹500 from Rahul",
            cleaner.clean("Received   ₹500  from\nRahul")
        )
    }

    @Test
    fun `drops consecutive duplicate sentences`() {
        assertEquals(
            "₹500 received",
            cleaner.clean("₹500 received. ₹500 received.")
        )
    }

    @Test
    fun `collapses repeated whole text without sentence delimiters`() {
        // text == bigText duplication joined without punctuation
        assertEquals(
            "₹500 received",
            cleaner.clean("₹500 received ₹500 received")
        )
    }

    @Test
    fun `removes trailing promotional sentence`() {
        assertEquals(
            "You received ₹500 from Rahul",
            cleaner.clean("You received ₹500 from Rahul. Get 20% cashback on your next bill.")
        )
    }

    @Test
    fun `keeps payment sentence that merely mentions an offer`() {
        // Conservative: a transaction signal protects the segment; the filter is
        // the backstop that drops it if it is still promotional.
        assertEquals(
            "You received ₹500 and a 10% reward",
            cleaner.clean("You received ₹500 and a 10% reward")
        )
    }

    @Test
    fun `removes standalone promotional notification entirely`() {
        assertEquals(
            "",
            cleaner.clean("Get 20% cashback on your next bill")
        )
    }

    @Test
    fun `preserves rs amount format`() {
        assertEquals(
            "Rahul paid you Rs. 500",
            cleaner.clean("Rahul paid you Rs. 500 Tap to view.")
        )
    }

    @Test
    fun `preserves bank sms content`() {
        assertEquals(
            "Your A/c no. 006153 is credited with INR 10.00 on 09-08-2026 towards UPI/622122395006/ANKIT KUMA/KK. Current Bal is INR 18,130.18 CR. - Saraswat Co-op Bank Ltd",
            cleaner.clean(
                "Your A/c no. 006153 is credited with INR 10.00 on 09-08-2026 towards UPI/622122395006/ANKIT KUMA/KK. Current Bal is INR 18,130.18 CR. - Saraswat Co-op Bank Ltd."
            )
        )
    }

    @Test
    fun `blank input returns blank`() {
        assertEquals("", cleaner.clean(""))
        assertEquals("", cleaner.clean("   "))
    }

    @Test
    fun `cleaning is idempotent`() {
        val samples = listOf(
            "ANKIT paid you ₹10.00 Tap to view. Tap to view.",
            "₹500 received ₹500 received",
            "You received ₹500 from Rahul. Get 20% cashback on your next bill.",
            "Received   ₹500  from\nRahul https://gpay.app.goo.gl/x"
        )
        for (sample in samples) {
            val once = cleaner.clean(sample)
            assertEquals("not idempotent for: $sample", once, cleaner.clean(once))
        }
    }

    @Test
    fun `cleaned gpay text still parses with GPayParserV1`() {
        val cleaned = cleaner.clean("ANKIT KUMAR BRIJESH SHANKA MISHRA paid you ₹10.00 Tap to view. Tap to view.")
        val parser = GPayParserV1()
        assertTrue(parser.canParse(cleaned))
        val parsed = parser.parse(cleaned, 1_700_000_000_000L)
        assertEquals(10.0, parsed.amount, 0.001)
        assertEquals("ANKIT KUMAR BRIJESH SHANKA MISHRA", parsed.sender)
    }
}
