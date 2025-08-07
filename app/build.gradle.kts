plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.parcelize")
}

android {
    namespace = "com.example.evid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.evid"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Set to true if using ProGuard
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    // Enable ABI splits for FFmpegKit to reduce APK size
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    // Optional: Use App Bundles for dynamic delivery
    bundle {
        abi {
            enableSplit = true
        }
    }
}

dependencies {
    // Exclude com.arthenica:ffmpeg-kit-https to avoid conflicts
    configurations.all {
        exclude(group = "com.arthenica", module = "ffmpeg-kit-https")
    }

    // Local FFmpegKit artifact
    implementation("com.local.ffmpeg-kit:full_binary:6.0-2")

    // Smart exception dependencies (local files)
    implementation(files("./repo/smart-exception-java-0.2.1.jar"))
    implementation(files("./repo/smart-exception-common-0.2.1.jar"))

    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("org.json:json:20230227")
    // Alternative: Use Maven Central for smart exceptions
    // implementation("com.arthenica:smart-exception-common:0.2.1")
    // implementation("com.arthenica:smart-exception-java:0.2.1")


    implementation("androidx.compose.material:material-icons-extended:1.6.1")
    // Compose and Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.media3.common.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}