package com.upivoicealert.data.repository

import android.util.Log
import com.upivoicealert.data.datastore.SubscriptionDataStore
import com.upivoicealert.domain.model.SubscriptionInfo
import com.upivoicealert.domain.model.SubscriptionPlan
import com.upivoicealert.domain.model.SubscriptionPlans
import com.upivoicealert.domain.model.SubscriptionStatus
import com.upivoicealert.domain.model.UpgradeResult
import com.upivoicealert.domain.repository.SubscriptionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed subscription state (Feature 3). Local only — the interface
 * is the seam where a Razorpay-backed implementation will plug in later.
 */
@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val dataStore: SubscriptionDataStore
) : SubscriptionRepository {

    override fun observeSubscription(): Flow<SubscriptionInfo> =
        combine(dataStore.planId, dataStore.status) { planId, status ->
            SubscriptionInfo(plan = SubscriptionPlans.byId(planId), status = status)
        }

    override suspend fun getCurrentPlan(): SubscriptionPlan =
        SubscriptionPlans.byId(dataStore.planId.first())

    override suspend fun checkSubscriptionStatus(): SubscriptionStatus =
        dataStore.status.first()

    override suspend fun upgradePlan(planId: String): UpgradeResult {
        Log.i(
            TAG,
            "UPGRADE_REQUEST planId=$planId — no payment gateway integrated (Razorpay planned), returning placeholder"
        )
        return UpgradeResult.GatewayNotIntegrated
    }

    private companion object {
        const val TAG = "SHOUTPAY_SUBSCRIPTION_DEBUG"
    }
}
