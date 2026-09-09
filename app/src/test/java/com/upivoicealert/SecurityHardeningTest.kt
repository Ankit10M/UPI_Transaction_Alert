package com.upivoicealert

import com.upivoicealert.config.EnvironmentConfig
import com.upivoicealert.config.AppEnvironment
import com.upivoicealert.config.EnvironmentValidator
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Phase 8.5 security regression — verifies fixes for demonstrated vulnerabilities.
 * Covers T11 logging, T12 config leakage, T10 local data, T17 payload limits, JWT config, etc.
 */
class SecurityHardeningTest {

    @Test fun `UserRepositoryImpl does not log phone or PII in release`() {
        val src = File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/data/repository/UserRepositoryImpl.kt").readText()
        assertFalse("Must not log phoneNumber in release", src.contains("phone=\$phoneNumber"))
        assertFalse("Must not log name in release with phone", src.contains("name=\$name") && src.contains("phone="))
        assertTrue("Must gate PROFILE_SAVED with DEBUG or AppLogger", src.contains("BuildConfig.DEBUG") || src.contains("AppLogger.d"))
    }

    @Test fun `network security config forbids cleartext`() {
        val xml = File("D:/UPI_Notification_Alert/app/src/main/res/xml/network_security_config.xml").readText()
        assertTrue(xml.contains("cleartextTrafficPermitted=\"false\""))
        val manifest = File("D:/UPI_Notification_Alert/app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("networkSecurityConfig"))
        assertTrue(manifest.contains("allowBackup=\"false\""))
        assertTrue(manifest.contains("dataExtractionRules"))
    }

    @Test fun `EncryptedSharedPreferences used for tokens not Room`() {
        val sessionStore = File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/data/auth/AuthSessionStore.kt").readText()
        assertTrue(sessionStore.contains("EncryptedSharedPreferences"))
        assertFalse("Tokens must not be stored in Room Entity", sessionStore.contains("@Entity"))
        val transactionEntity = File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/data/database/TransactionEntity.kt").readText()
        assertFalse(transactionEntity.contains("accessToken"))
        assertFalse(transactionEntity.contains("refreshToken"))
    }

    @Test fun `release base URL validation rejects localhost and non-https`() {
        listOf("http://10.0.2.2:3000/api/", "http://localhost:3000/api/", "http://192.168.1.10:3000/api/").forEach { url ->
            val cfg = EnvironmentConfig(baseUrl = url, environment = AppEnvironment.RELEASE)
            assertNotNull("RELEASE must reject $url", EnvironmentValidator.validate(cfg))
        }
        assertNull(EnvironmentValidator.validate(EnvironmentConfig("https://api.shoutpay.in/api/", AppEnvironment.RELEASE)))
        assertNull(EnvironmentValidator.validate(EnvironmentConfig("http://192.168.1.10:3000/api/", AppEnvironment.DEBUG)))
    }

    @Test fun `NotificationListenerService exported with permission`() {
        val manifest = File("D:/UPI_Notification_Alert/app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("UpiNotificationListenerService"))
        assertTrue(manifest.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"))
        assertTrue(manifest.contains("android:exported=\"true\""))
        // No other service exported without permission
        val exportedCount = Regex("android:exported=\"true\"").findAll(manifest).count()
        assertTrue("Only MainActivity + Listener should be exported", exportedCount <= 2)
    }

    @Test fun `backend json body limit hardened`() {
        val appJs = File("D:/UPI_Notification_Alert/shoutpay-backend/src/app.js").readText()
        assertTrue(appJs.contains("256kb"))
        assertFalse("Must not allow 10mb bodies", appJs.contains("'10mb'") || appJs.contains("\"10mb\""))
    }

    @Test fun `backend 404 does not leak path in production`() {
        val appJs = File("D:/UPI_Notification_Alert/shoutpay-backend/src/app.js").readText()
        assertTrue(appJs.contains("NOT_FOUND"))
        assertTrue(appJs.contains("NODE_ENV === 'production'"))
    }

    @Test fun `JWT verification pinned to HS256`() {
        val jwtJs = File("D:/UPI_Notification_Alert/shoutpay-backend/src/auth/jwt.js").readText()
        assertTrue(jwtJs.contains("HS256"))
        assertTrue(jwtJs.contains("algorithms"))
        assertTrue(jwtJs.contains("issuer"))
        assertTrue(jwtJs.contains("audience"))
    }

    @Test fun `backend validation rejects mass assignment fields`() {
        val syncValidation = File("D:/UPI_Notification_Alert/shoutpay-backend/src/modules/transactions/validation.js").readText()
        assertTrue(syncValidation.contains("merchantId: Joi.forbidden()"))
        assertTrue(syncValidation.contains("firebaseUid: Joi.forbidden()"))
        assertTrue(syncValidation.contains("status: Joi.forbidden()"))
        assertTrue(syncValidation.contains("unknown(false)"))
        val merchantValidation = File("D:/UPI_Notification_Alert/shoutpay-backend/src/modules/merchant/validation.js").readText()
        assertTrue(merchantValidation.contains("merchantId: Joi.forbidden()"))
        assertTrue(merchantValidation.contains("phoneNumber: Joi.forbidden()"))
        val deviceValidation = File("D:/UPI_Notification_Alert/shoutpay-backend/src/modules/devices/validation.js").readText()
        assertTrue(deviceValidation.contains("unknown(false)"))
    }

    @Test fun `transaction validation limits enforced`() {
        val v = File("D:/UPI_Notification_Alert/shoutpay-backend/src/modules/transactions/validation.js").readText()
        assertTrue(v.contains("max(100)"))
        assertTrue(v.contains("positive()"))
        assertTrue(v.contains("max(200)"))
        assertTrue(v.contains("isoDate()"))
    }

    @Test fun `query pagination bounded`() {
        val q = File("D:/UPI_Notification_Alert/shoutpay-backend/src/modules/transactions/query.validation.js").readText()
        assertTrue(q.contains("max(100)"))
        assertTrue(q.contains("min(1)"))
    }

    @Test fun `secrets not committed gitignore`() {
        val gi = File("D:/UPI_Notification_Alert/.gitignore").readText()
        assertTrue(gi.contains(".env"))
        assertTrue(gi.contains("local.properties"))
        assertFalse("local.properties must be ignored", gi.isEmpty())
        val localProps = File("D:/UPI_Notification_Alert/local.properties").readText()
        assertTrue(localProps.contains("SHOUTPAY_DEBUG_BASE_URL"))
        assertFalse(localProps.contains("SHOUTPAY_RELEASE_BASE_URL") && localProps.contains("api.shoutpay.in"))
    }

    @Test fun `google-services json is public identifier not secret`() {
        val gs = File("D:/UPI_Notification_Alert/app/google-services.json").readText()
        assertTrue(gs.contains("project_id"))
        // No private_key in google-services.json
        assertFalse(gs.contains("private_key"))
    }

    @Test fun `diagnostics remain sanitized after phase 8_5`() {
        val repo = File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/data/sync/TransactionSyncRepository.kt").readText()
        assertTrue(repo.contains("ERROR_MSG_VALIDATION"))
        assertFalse("Must not persist raw body", repo.contains("e.response") && repo.contains("lastErrorMessage = e.response"))
    }

    @Test fun `offline payment not blocked by network`() {
        // Structural: TransactionRepositoryImpl / pipeline must not reference NetworkMonitor or Auth
        val txnRepo = File("D:/UPI_Notification_Alert/app/src/main/java/com/upivoicealert/data/repository/TransactionRepositoryImpl.kt").readText()
        assertFalse("Offline pipeline must not depend on NetworkMonitor", txnRepo.contains("NetworkMonitor"))
        assertFalse(txnRepo.contains("AuthRepository"))
    }
}
