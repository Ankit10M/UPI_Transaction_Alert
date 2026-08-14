package com.upivoicealert.domain.model

/**
 * Static catalog of plans offered on the Pricing page (Feature 3).
 *
 * Local-only in the MVP: the current plan defaults to [FREE_TRIAL] and
 * upgrades are placeholders until Razorpay is integrated.
 */
object SubscriptionPlans {

    val FREE_TRIAL = SubscriptionPlan(
        id = "free_trial",
        name = "Free Trial",
        price = 0.0,
        duration = "7 days",
        features = listOf(
            "Unlimited payment alerts",
            "English, Hindi & Marathi voice",
            "Payment verification",
            "Business summary"
        )
    )

    val PRO = SubscriptionPlan(
        id = "pro",
        name = "Pro",
        price = 199.0,
        duration = "30 days",
        features = listOf(
            "Everything in Free Trial",
            "Extended transaction history",
            "Priority support",
            "No ads, ever"
        )
    )

    val BUSINESS = SubscriptionPlan(
        id = "business",
        name = "Business",
        price = 499.0,
        duration = "30 days",
        features = listOf(
            "Everything in Pro",
            "Multi-device sync (coming soon)",
            "Daily sales reports",
            "Dedicated merchant support"
        )
    )

    val ALL: List<SubscriptionPlan> = listOf(FREE_TRIAL, PRO, BUSINESS)

    fun byId(id: String): SubscriptionPlan = ALL.firstOrNull { it.id == id } ?: FREE_TRIAL
}
