import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.upivoicealert"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.upivoicealert"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            buildConfigField(
                "String",
                "BASE_URL",
                "\"${localProperties.getProperty("SHOUTPAY_DEBUG_BASE_URL") ?: "https://api.shoutpay.in/api/"}\""
            )
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Production URL is externally configurable via local.properties
            // SHOUTPAY_RELEASE_BASE_URL or env. Do not hardcode dev IPs here.
            // Fallback is https://api.shoutpay.in/api/ — must be verified as the
            // real production domain before Play Store publishing. Validation at
            // runtime will reject localhost/10.0.2.2/LAN IPs if misconfigured.
            val releaseBaseUrl = localProperties.getProperty("SHOUTPAY_RELEASE_BASE_URL")
                ?: System.getenv("SHOUTPAY_RELEASE_BASE_URL")
                ?: "https://api.shoutpay.in/api/"
            // Build-time safety: fail release build if release URL is obviously invalid
            if (releaseBaseUrl.contains("10.0.2.2") || releaseBaseUrl.contains("localhost") || releaseBaseUrl.contains("127.0.0.1") || Regex("""192\.168\.\d+\.\d+""").containsMatchIn(releaseBaseUrl)) {
                throw GradleException("Invalid SHOUTPAY_RELEASE_BASE_URL for release build: '$releaseBaseUrl' — release must use production HTTPS endpoint, not localhost/LAN/emulator IP.")
            }
            if (!releaseBaseUrl.startsWith("https://")) {
                throw GradleException("Invalid SHOUTPAY_RELEASE_BASE_URL for release build: '$releaseBaseUrl' — release must use https://.")
            }
            if (!releaseBaseUrl.endsWith("/")) {
                throw GradleException("Invalid SHOUTPAY_RELEASE_BASE_URL for release build: '$releaseBaseUrl' — must end with '/'.")
            }
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
            // Only use keystore signing if keystore.properties is present; otherwise
            // assembled release APK/AAB will be unsigned (CI must supply keystore).
            if (keystoreProperties.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // NotificationFilter logs via android.util.Log; stub it in JVM tests.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.security.crypto)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
