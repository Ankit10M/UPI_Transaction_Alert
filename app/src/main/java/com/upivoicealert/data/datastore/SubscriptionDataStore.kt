package com.upivoicealert.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.upivoicealert.domain.model.SubscriptionPlans
import com.upivoicealert.domain.model.SubscriptionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.subscriptionDataStore: androidx.datastore.core.DataStore<Preferences> by
    preferencesDataStore(name = "subscription")

/**
 * Local subscription persistence (Feature 3). MVP stores only the chosen plan
 * id + lifecycle status; the plan catalog itself is static
 * ([SubscriptionPlans]). Defaults to the Free Trial so a fresh install shows
 * the trial plan with no setup.
 */
@Singleton
class SubscriptionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val PLAN_ID = stringPreferencesKey("subscription_plan_id")
        val STATUS = stringPreferencesKey("subscription_status")
    }

    val planId: Flow<String> = context.subscriptionDataStore.data
        .map { it[Keys.PLAN_ID] ?: SubscriptionPlans.FREE_TRIAL.id }

    val status: Flow<SubscriptionStatus> = context.subscriptionDataStore.data
        .map {
            runCatching { SubscriptionStatus.valueOf(it[Keys.STATUS] ?: "") }
                .getOrDefault(SubscriptionStatus.FREE_TRIAL)
        }

    suspend fun setPlanId(planId: String) {
        context.subscriptionDataStore.edit { it[Keys.PLAN_ID] = planId }
    }

    suspend fun setStatus(status: SubscriptionStatus) {
        context.subscriptionDataStore.edit { it[Keys.STATUS] = status.name }
    }
}
