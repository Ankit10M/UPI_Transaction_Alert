package com.upivoicealert.domain.model

/**
 * Service run state (Feature 6 — service control improvement).
 *
 * The big START/STOP control on the Home screen toggles between these two
 * states. The NotificationListenerService itself stays bound to the system in
 * both states — only transaction processing (and TTS) is gated.
 */
enum class ServiceStatus {
    SERVICE_RUNNING,
    SERVICE_STOPPED
}
