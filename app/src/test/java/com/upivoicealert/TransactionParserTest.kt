package com.upivoicealert

import com.upivoicealert.domain.model.TransactionType
import com.upivoicealert.parser.ParserVersionResolver
import com.upivoicealert.parser.generic.GenericReceivedParserV1
import com.upivoicealert.parser.gpay.GPayParserV1
import com.upivoicealert.parser.kotak.KotakParserV1
import com.upivoicealert.utils.PackageNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionParserTest {

    private val generic = GenericReceivedParserV1()
    private val kotak = KotakParserV1()
    private val gpay = GPayParserV1()

    @Test
    fun `generic parser extracts amount sender and type`() {
        val parsed = generic.parse(
            "₹10.00 received from PRIYA BRIJESH MISHRA Amount credited to XX3434",
            1_700_000_000_000L
        )
        assertEquals(10.0, parsed.amount, 0.001)
        assertEquals("PRIYA BRIJESH MISHRA", parsed.sender)
        assertEquals(TransactionType.RECEIVED, parsed.transactionType)
    }

    @Test
    fun `generic parser can parse received from text`() {
        assertTrue(generic.canParse("₹10.00 received from PRIYA BRIJESH MISHRA Amount credited to XX3434"))
        assertFalse(generic.canParse("PRIYA BRIJESH MISHRA paid you ₹10.00"))
    }

    @Test
    fun `kotak parser extracts amount sender and app`() {
        val parsed = kotak.parse(
            "₹10.00 received from PRIYA BRIJESH MISHRA Amount credited to XX3434. Check out details.",
            1_700_000_000_000L
        )
        assertEquals(10.0, parsed.amount, 0.001)
        assertEquals("PRIYA BRIJESH MISHRA", parsed.sender)
        assertEquals(PackageNames.labelFor(PackageNames.KOTAK), parsed.upiApp)
        assertEquals(TransactionType.RECEIVED, parsed.transactionType)
    }

    @Test
    fun `gpay parser extracts sender and amount`() {
        val parsed = gpay.parse("PRIYA BRIJESH MISHRA paid you ₹10.00", 1_700_000_000_000L)
        assertEquals("PRIYA BRIJESH MISHRA", parsed.sender)
        assertEquals(10.0, parsed.amount, 0.001)
        assertEquals(PackageNames.labelFor(PackageNames.GPAY), parsed.upiApp)
        assertEquals(TransactionType.RECEIVED, parsed.transactionType)
    }

    @Test
    fun `gpay parser can parse paid you text`() {
        assertTrue(gpay.canParse("PRIYA BRIJESH MISHRA paid you ₹10.00"))
        assertFalse(gpay.canParse("₹10.00 received from PRIYA BRIJESH MISHRA"))
    }

    @Test
    fun `gpay parser handles rs amount format`() {
        val parsed = gpay.parse("Rahul paid you Rs. 500", 1_700_000_000_000L)
        assertEquals(500.0, parsed.amount, 0.001)
        assertEquals("Rahul", parsed.sender)
    }

    @Test
    fun `resolver falls back to generic parser for unknown package`() {
        val resolver = ParserVersionResolver(listOf(gpay, kotak, generic))
        val parser = resolver.resolve(
            "com.example.somebank",
            "₹10.00 received from PRIYA BRIJESH MISHRA Amount credited to XX3434"
        )
        assertNotNull(parser)
        assertTrue(parser is GenericReceivedParserV1)
    }

    @Test
    fun `resolver picks kotak parser for kotak package`() {
        val resolver = ParserVersionResolver(listOf(gpay, kotak, generic))
        val parser = resolver.resolve(
            PackageNames.KOTAK,
            "₹10.00 received from PRIYA BRIJESH MISHRA Amount credited to XX3434"
        )
        assertNotNull(parser)
        assertTrue(parser is KotakParserV1)
    }

    @Test
    fun `resolver returns null when no parser matches`() {
        val resolver = ParserVersionResolver(listOf(gpay, kotak, generic))
        assertNull(resolver.resolve("com.example.app", "Your weekly digest is ready"))
    }
}
