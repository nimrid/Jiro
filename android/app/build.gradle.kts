plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jiro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jiro.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PROGRAM_ID", "\"AU4qNgE5qEvuULeKTxJgotM2V8eNygZj14YWDSMU5dqc\"")
        buildConfigField("String", "USDC_MINT", "\"4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU\"")
        buildConfigField("String", "DEVNET_RPC_URL", "\"https://api.devnet.solana.com\"")
        // Default KEEPER_URL is the live Render deployment
        buildConfigField("String", "KEEPER_URL", "\"https://jiro-4el4.onrender.com\"")
        // Certificate pinning is disabled by default (overridden per build type below)
        buildConfigField("Boolean", "ENABLE_CERT_PINNING", "false")
    }

    buildTypes {
        debug {
            // Debug keeps verbose logging on; cert pinning disabled
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "KEEPER_URL", "\"https://jiro-4el4.onrender.com\"")
            // Enable TLS certificate pinning in release builds
            buildConfigField("Boolean", "ENABLE_CERT_PINNING", "true")
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.solana.mwa)
    implementation(libs.bouncy.castle)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bouncy.castle)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
