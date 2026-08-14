package com.upivoicealert.domain.model

/**
 * Outcome of [com.upivoicealert.domain.repository.SubscriptionRepository.upgradePlan].
 *
 * The MVP has NO payment gateway integrated (Feature 3 — prepare architecture
 * for Razorpay later), so the only possible result today is
 * [GatewayNotIntegrated]; the UI shows a placeholder subscription flow.
 */
sealed class UpgradeResult {
    /** Razorpay (or any gateway) is not integrated yet — placeholder only. */
    data object GatewayNotIntegrated : UpgradeResult()
}
