package com.upivoicealert

import com.upivoicealert.data.profile.MerchantProfileDto
import com.upivoicealert.data.profile.toDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantProfileMapperTest {
    @Test
    fun `DTO maps to domain preserving all fields`() {
        val dto = MerchantProfileDto(
            merchantId = "SP-123456",
            ownerName = "Rahul",
            shopName = "Rahul Store",
            phoneNumber = "+919876543210",
            createdAt = "2026-01-01T00:00:00.000Z"
        )
        val domain = dto.toDomain()
        assertEquals("SP-123456", domain.merchantId)
        assertEquals("Rahul", domain.ownerName)
        assertEquals("Rahul Store", domain.shopName)
        assertEquals("+919876543210", domain.phoneNumber)
        assertEquals("2026-01-01T00:00:00.000Z", domain.createdAt)
    }

    @Test
    fun `DTO with null ownerName shopName maps correctly`() {
        val dto = MerchantProfileDto(
            merchantId = "SP-000001",
            ownerName = null,
            shopName = null,
            phoneNumber = "+911234567890",
            createdAt = "2026-02-01T10:00:00.000Z"
        )
        val domain = dto.toDomain()
        assertEquals(null, domain.ownerName)
        assertEquals(null, domain.shopName)
    }
}
