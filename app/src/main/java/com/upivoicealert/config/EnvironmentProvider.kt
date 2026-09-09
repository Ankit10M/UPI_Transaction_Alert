package com.upivoicealert.config

import com.upivoicealert.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized environment provider (Phase 8.1).
 *
 * Single source of truth for environment decisions:
 *   BuildConfig → EnvironmentProvider → Network Module → Retrofit
 *
 * Repositories and business logic depend on this provider (or just the API
 * interfaces), never on BuildConfig directly.
 */
interface EnvironmentProvider {
    val config: EnvironmentConfig
    val baseUrl: String get() = config.baseUrl
    val environment: AppEnvironment get() = config.environment
    val isDebug: Boolean get() = config.isDebug
    val isHttpBodyLoggingEnabled: Boolean get() = config.isHttpBodyLoggingEnabled
}

@Singleton
class BuildConfigEnvironmentProvider @Inject constructor() : EnvironmentProvider {

    override val config: EnvironmentConfig by lazy {
        val cfg = EnvironmentConfig(
            baseUrl = BuildConfig.BASE_URL,
            environment = if (BuildConfig.DEBUG) AppEnvironment.DEBUG else AppEnvironment.RELEASE
        )
        // Fail fast on obviously invalid release config — visible during development/build verification.
        // Debug localhost/LAN is allowed; release localhost/LAN will throw.
        EnvironmentValidator.requireValid(cfg)
        cfg
    }
}
