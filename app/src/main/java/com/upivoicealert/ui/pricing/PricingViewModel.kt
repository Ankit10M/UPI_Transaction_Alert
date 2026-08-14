package com.upivoicealert.ui.pricing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.upivoicealert.domain.model.SubscriptionInfo
import com.upivoicealert.domain.model.SubscriptionPlan
import com.upivoicealert.domain.model.SubscriptionPlans
import com.upivoicealert.domain.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the pricing/subscription screen (Feature 3). The current
 * plan comes from [SubscriptionRepository] (DataStore); upgrades are
 * placeholders until a payment gateway (Razorpay) is integrated.
 */
@HiltViewModel
class PricingViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    /** Reactive current plan + status. */
    val subscription: StateFlow<SubscriptionInfo> = subscriptionRepository.observeSubscription()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialSubscription())

    /** All offered plans, in display order (Stitch pricing design). */
    val plans: List<SubscriptionPlan> = SubscriptionPlans.ALL

    /** One-shot event: upgrade request surfaced to the placeholder dialog. */
    private val _upgradeRequest = MutableStateFlow<String?>(null)
    val upgradeRequest: StateFlow<String?> = _upgradeRequest.asStateFlow()

    fun onUpgradeClick(planId: String) {
        _upgradeRequest.value = planId
    }

    fun dismissUpgradeDialog() {
        _upgradeRequest.value = null
    }

    /** Placeholder: persists nothing, logs the request, confirms the flow exists. */
    fun confirmUpgradePlaceholder(planId: String) = viewModelScope.launch {
        subscriptionRepository.upgradePlan(planId)
        _upgradeRequest.value = null
    }

    private fun initialSubscription(): SubscriptionInfo = SubscriptionInfo(
        plan = SubscriptionPlans.FREE_TRIAL,
        status = com.upivoicealert.domain.model.SubscriptionStatus.FREE_TRIAL
    )
}
