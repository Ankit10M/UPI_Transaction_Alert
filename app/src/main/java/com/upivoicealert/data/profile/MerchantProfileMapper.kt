package com.upivoicealert.data.profile

import com.upivoicealert.domain.profile.MerchantProfile

/**
 * Mapper between DTO and domain model. Keeps API models separated from business models.
 */
fun MerchantProfileDto.toDomain(): MerchantProfile = MerchantProfile(
    merchantId = merchantId,
    ownerName = ownerName,
    shopName = shopName,
    phoneNumber = phoneNumber,
    createdAt = createdAt
)

fun MerchantProfile.toDto(): MerchantProfileDto = MerchantProfileDto(
    merchantId = merchantId,
    ownerName = ownerName,
    shopName = shopName,
    phoneNumber = phoneNumber,
    createdAt = createdAt
)
