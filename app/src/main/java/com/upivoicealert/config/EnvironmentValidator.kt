package com.upivoicealert.config

/**
 * Phase 8.1 — Environment validation mechanism.
 *
 * Validates that a release build never ships with an obviously invalid
 * production endpoint (localhost, emulator loopback, LAN IP, missing, malformed).
 *
 * Debug builds are NOT validated for localhost/LAN — developers legitimately
 * use those endpoints during development.
 */
object EnvironmentValidator {

    /**
     * Returns null if [config] is valid, otherwise a human-readable error.
     * Callers should throw IllegalStateException(error) to fail the build/init.
     */
    fun validate(config: EnvironmentConfig): String? {
        val url = config.baseUrl.trim()

        if (url.isEmpty()) {
            return "API base URL is missing or empty for environment ${config.environment}"
        }

        // Must be a well-formed http(s) URL ending with '/'
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "API base URL must start with http:// or https:// — got '$url'"
        }
        if (!url.endsWith("/")) {
            return "API base URL must end with '/' — got '$url'"
        }

        // Malformed URL check via java.net.URL parse
        try {
            java.net.URL(url)
        } catch (e: Exception) {
            return "API base URL is malformed: '$url' (${e.message})"
        }

        if (config.isRelease) {
            val lower = url.lowercase()

            // Release must NEVER point at localhost / emulator / obvious LAN dev endpoints
            if (lower.contains("10.0.2.2")) {
                return "Invalid release base URL: contains emulator loopback 10.0.2.2 — release must use production endpoint. Got '$url'"
            }
            if (lower.contains("localhost") || lower.contains("127.0.0.1")) {
                return "Invalid release base URL: contains localhost/127.0.0.1 — release must use production endpoint. Got '$url'"
            }
            // Obvious LAN IPs - do not over-match public IPs, just private dev ranges commonly mis-shipped
            if (Regex("""https?://192\.168\.\d+\.\d+""").containsMatchIn(lower)) {
                return "Invalid release base URL: contains LAN IP 192.168.x.x — release must use production endpoint. Got '$url'"
            }
            if (Regex("""https?://10\.\d+\.\d+\.\d+""").containsMatchIn(lower)) {
                return "Invalid release base URL: contains private IP 10.x.x.x — release must use production endpoint. Got '$url'"
            }

            // Must be HTTPS in release (production requirement)
            if (!lower.startsWith("https://")) {
                return "Invalid release base URL: release must use https:// — got '$url'"
            }
        }

        return null // valid
    }

    /** Validate and throw on failure — suitable for app startup / DI provider. */
    fun requireValid(config: EnvironmentConfig) {
        val error = validate(config)
        if (error != null) throw IllegalStateException(error)
    }
}
