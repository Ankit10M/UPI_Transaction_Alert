# Phase 8.1 — Release minification is currently disabled (isMinifyEnabled = false).
# Keep rules are documented here for future enablement. Do not enable aggressive
# shrinking without testing; these rules are the minimal safe set.

# Hilt — generated components use reflection
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends androidx.hilt.work.HiltWorker

# Room — entities and DAOs
-keep class com.upivoicealert.data.database.** { *; }
-keep class com.upivoicealert.data.sync.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Retrofit / Gson — model DTOs
-keep class com.upivoicealert.network.** { *; }
-keep class com.upivoicealert.data.** { *; }
-keep class com.upivoicealert.data.sync.** { *; }
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod

# Firebase Auth
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

# Phase 8.1 logging policy — when minification is enabled, strip verbose logs
# to ensure no debug information leaks to release (AppLogger already gates at runtime).
# Uncomment after verifying minify does not break Hilt/Room:
# -assumenosideeffects class android.util.Log {
#     public static *** v(...);
#     public static *** d(...);
#     public static *** i(...);
# }
