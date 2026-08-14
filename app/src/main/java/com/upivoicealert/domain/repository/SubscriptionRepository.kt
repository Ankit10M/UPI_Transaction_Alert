package com.upivoicealert.domain.repository

import com.upivoicealert.domain.model.SubscriptionInfo
import com.upivoicealert.domain.model.SubscriptionPlan
import com.upivoicealert.domain.model.SubscriptionStatus
import com.upivoicealert.domain.model.UpgradeResult
import kotlinx.coroutines.flow.Flow

/**
 * Subscription state (Feature 3 — pricing/subscription page).
 *
 * MVP implementation is DataStore-local. The interface is shaped so a real
 * Razorpay-backed implementation can replace it later without touching the UI.
 */
interface SubscriptionRepository {

    /** Reactive current plan + lifecycle status. */
    fun observeSubscription(): Flow<SubscriptionInfo>

    /** Current plan (defaults to the Free Trial until upgraded). */
    suspend fun getCurrentPlan(): SubscriptionPlan

    /** Current lifecycle status (FREE_TRIAL / ACTIVE / EXPIRED). */
    suspend fun checkSubscriptionStatus(): SubscriptionStatus

    /**
     * Request an upgrade to [planId]. MVP: no payment gateway is integrated, so
     * this always returns [UpgradeResult.GatewayNotIntegrated] (placeholder).
     */
    suspend fun upgradePlan(planId: String): UpgradeResult
}
