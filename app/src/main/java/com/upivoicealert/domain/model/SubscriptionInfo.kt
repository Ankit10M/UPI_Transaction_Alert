package com.upivoicealert.domain.model

/** Current subscription: which plan the merchant is on and its lifecycle state. */
data class SubscriptionInfo(
    val plan: SubscriptionPlan,
    val status: SubscriptionStatus
)
