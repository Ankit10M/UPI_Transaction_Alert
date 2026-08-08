package com.upivoicealert

import com.upivoicealert.parser.ReferenceIdExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReferenceIdExtractorTest {

    @Test
    fun `extracts upi ref`() {
        assertEquals("111111", ReferenceIdExtractor.extract("Rahul paid you ₹10.00 UPI Ref 111111"))
        assertEquals("111111", ReferenceIdExtractor.extract("Rahul paid you ₹10.00 UPI Ref: 111111"))
    }

    @Test
    fun `extracts ref colon and ref no`() {
        assertEquals("271910140834", ReferenceIdExtractor.extract("₹10.00 received from PRIYA Ref: 271910140834"))
        assertEquals("271910140834", ReferenceIdExtractor.extract("₹10.00 received from PRIYA Ref No 271910140834"))
        assertEquals("271910140834", ReferenceIdExtractor.extract("₹10.00 received from PRIYA Ref No. 271910140834"))
    }

    @Test
    fun `extracts ref id and reference keyword`() {
        assertEquals("123456789012", ReferenceIdExtractor.extract("Ref ID 123456789012"))
        assertEquals("123456789012", ReferenceIdExtractor.extract("Reference 123456789012"))
    }

    @Test
    fun `extracts utr and transaction id`() {
        assertEquals("987654321098", ReferenceIdExtractor.extract("UTR 987654321098"))
        assertEquals("202406121234", ReferenceIdExtractor.extract("Transaction ID: 202406121234"))
        assertEquals("202406121234", ReferenceIdExtractor.extract("Txn ID 202406121234"))
        assertEquals("202406121234", ReferenceIdExtractor.extract("Txn 202406121234"))
    }

    @Test
    fun `different references are extracted distinctly`() {
        assertEquals("111111", ReferenceIdExtractor.extract("₹10 from Rahul UPI Ref 111111"))
        assertEquals("222222", ReferenceIdExtractor.extract("₹10 from Rahul UPI Ref 222222"))
    }

    @Test
    fun `returns null when no reference keyword present`() {
        assertNull(ReferenceIdExtractor.extract("PRIYA BRIJESH MISHRA paid you ₹10.00"))
        assertNull(ReferenceIdExtractor.extract("₹10.00 received from PRIYA BRIJESH MISHRA Amount credited to XX3434"))
    }

    @Test
    fun `does not false-positive on refund or short tokens`() {
        assertNull(ReferenceIdExtractor.extract("Refund of ₹10 processed"))
        assertNull(ReferenceIdExtractor.extract("Rahul paid you ₹10.00 UPI Ref 12345"))
        assertNull(ReferenceIdExtractor.extract("Refer your friends and earn ₹50"))
    }
}
