package com.upivoicealert.domain.model

/**
 * A purchasable subscription plan (Feature 3 — pricing/subscription page).
 *
 * The MVP shows these locally and never charges money; a future Razorpay
 * integration will drive [SubscriptionRepository.upgradePlan].
 */
data class SubscriptionPlan(
    val id: String,
    val name: String,
    /** Price in INR. 0.0 for the free trial. */
    val price: Double,
    /** Billing duration label, e.g. "7 days" / "30 days" / "1 year". */
    val duration: String,
    val features: List<String>
)
