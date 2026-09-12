# Phase 9.2 — Release minification is currently DISABLED (isMinifyEnabled = false, isShrinkResources = false).
# See docs/PHASE_9_2_RELEASE_ARTIFACT_HARDENING.md for decision, validation required, and risk analysis.
# This file contains the MINIMAL keep set validated by static inspection; enabling R8 requires
# runtime build verification (Phase 9.3: ./gradlew assembleRelease + install + smoke test).
#
# IMPORTANT: A smaller or more secure APK is never worth breaking payment capture.
# Do not enable minify until Room/Hilt/Firebase/WorkManager/Compose keep rules are build-verified.

# --- Hilt — generated components via annotation processing (ksp) ---
# Why: Hilt generates Dagger components reflectively; R8 would strip them as unused.
# Evidence: @HiltAndroidApp, @AndroidEntryPoint, @Inject usage across app; build uses hilt-compiler ksp.
# Minimal: keep hilt generated + entry points, not whole dagger.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends androidx.hilt.work.HiltWorker
-keep class * extends dagger.hilt.android.AndroidEntryPoint

# --- Room — entities, DAOs, generated_Impl ---
# Why: Room generates AppDatabase_Impl and DAO_Impl via ksp; entities use reflection for column mapping.
# Evidence: AppDatabase @Database(version=9, entities=[TransactionEntity, UnparsedNotificationEntity, SyncQueueEntity, SyncDiagnosticEventEntity]), DAO @Query methods.
# Minimal: keep database package + Entity annotation + RoomDatabase subclass.
-keep class com.upivoicealert.data.database.** { *; }
-keep class com.upivoicealert.data.sync.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class androidx.room.** { *; }

# --- Retrofit / Gson / OkHttp — DTOs and reflective converters ---
# Why: Retrofit creates implementations via dynamic proxy; Gson uses reflection for field names (TransactionSyncRequestDto etc.).
# Evidence: libs.versions.toml retrofit 2.11.0 + converter-gson, OkHttp 4.12.0, TransactionSyncApi etc. @Body/@POST.
# Minimal: keep network DTOs + Gson reflective access, not whole okhttp.
-keep class com.upivoicealert.network.** { *; }
-keep class com.upivoicealert.data.sync.TransactionSync* { *; }
-keep class com.upivoicealert.data.sync.SyncQueueEntity { *; }
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod

# --- Firebase Auth / GMS — reflective initialization ---
# Why: Firebase Auth initializes via reflection and keeps native bindings; GMS `google-services.json` project upi-voice-alert-e7257.
# Evidence: firebase-auth via BOM 32.8.1, google-services plugin, FirebaseAuth.getInstance() in AuthModule/AuthRepository.
# Minimal: keep firebase + gms, not entire play-services.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# --- WorkManager — Workers instantiated by name via HiltWorkerFactory ---
# Why: WorkManager instantiates workers reflectively by class name (HiltWorkerFactory); R8 would rename them.
# Evidence: 4 workers (TransactionSyncWorker, TransactionReconciliationWorker, StaleUploadRecoveryWorker, SyncIntegrityAuditWorker) + 2 work workers, declared in AndroidManifest + scheduler unique KEEP.
# Minimal: keep Worker subclasses + HiltWorkerFactory.
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class androidx.hilt.work.HiltWorkerFactory { *; }

# --- Android Components — manifest-declared entry points ---
# Why: NotificationListenerService and Application are referenced only from AndroidManifest, not code.
# Evidence: AndroidManifest declares .UpiVoiceAlertApplication, .MainActivity, .service.UpiNotificationListenerService.
# Minimal: keep application + service + activity.
-keep class com.upivoicealert.UpiVoiceAlertApplication { *; }
-keep class com.upivoicealert.MainActivity { *; }
-keep class com.upivoicealert.service.UpiNotificationListenerService { *; }

# --- Compose / Navigation — keep is handled by compose compiler + Kotlin metadata; no custom keep needed ---
# Evidence: kotlin plugin compose enabled, androidx.compose.bom, NavHost `composable()` routes are inline lambdas, not reflective strings.
# Decision: no additional rule; verified via static grep — no Class.forName for compose destinations.

# --- Logging — strip verbose logs only AFTER R8 verified ---
# Phase 8.1 policy: when minification is enabled, strip v/d/i to ensure no debug info leaks (AppLogger already gates at runtime).
# Do NOT uncomment until ./gradlew assembleRelease + lintVital PASS + keep-rules smoke:
# -assumenosideeffects class android.util.Log {
#     public static *** v(...);
#     public static *** d(...);
#     public static *** i(...);
# }
