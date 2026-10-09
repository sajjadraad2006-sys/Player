import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("com.chaquo.python")
}

android {
    namespace = "dev.auralis.studio"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.auralis.studio"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // Optional provider credentials from local.properties. Never commit local.properties.
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }
        buildConfigField("String", "JAMENDO_CLIENT_ID", "\"${properties.getProperty("JAMENDO_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "AUDIUS_API_KEY", "\"${properties.getProperty("AUDIUS_API_KEY") ?: ""}\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${properties.getProperty("YOUTUBE_API_KEY") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE.md",
            "/META-INF/LICENSE-notice.md",
            "/META-INF/INDEX.LIST",
            "/META-INF/DEPENDENCIES",
        )
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName = if (buildType.name == "debug") "Lyrune-debug.apk" else "Lyrune.apk"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Optional, user-downloaded UVR MDX-Net Voc_FT inference. Model weights are not bundled.
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("com.github.wendykierp:JTransforms:3.1")

    // HTTP client for LRCLIB lyrics and Jamendo (when configured)
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.ktor:ktor-client-mock:3.0.3")
    testImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

chaquopy {
    defaultConfig {
        version = "3.14"
        val customPython = (project.findProperty("PYTHON_PATH") as? String)
            ?: System.getenv("PYTHON_PATH")
            ?: if (file("/home/sajjad/.local/bin/python3.14").exists()) "/home/sajjad/.local/bin/python3.14" else null
            ?: if (System.getProperty("os.name").lowercase().contains("windows")) "python" else "python3"
        if (!customPython.isNullOrBlank()) {
            buildPython(customPython)
        }
        pip {
            install("yt-dlp==2026.8.19")
            install("yt-dlp-ejs==0.8.0")
        }
    }
}
