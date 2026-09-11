plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("javazoom:jlayer:1.0.1")
    // Bounded AAC/Opus/Vorbis streaming decoder; bundled Windows runtime, no PATH dependency.
    implementation("org.bytedeco:ffmpeg:7.1.1-1.5.12")
    runtimeOnly("org.bytedeco:ffmpeg:7.1.1-1.5.12:windows-x86_64")
    runtimeOnly("org.bytedeco:javacpp:1.5.12:windows-x86_64")
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:3.0.3")
}

compose.desktop {
    application {
        mainClass = "dev.auralis.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "Lyrune"
            packageVersion = "1.0.0"
            description = "Lyrune Music Player"
            vendor = "Auralis Studio"
            modules("java.xml", "java.instrument", "jdk.unsupported", "java.desktop", "java.logging", "java.management")
            windows {
                menuGroup = "Lyrune"
                upgradeUuid = "6f8c70e9-b5d1-4c12-8e0a-3a1b4c9e8d7f"
                perUserInstall = true
                dirChooser = true
                installationPath = "Programs/Lyrune"
            }
        }
    }
}

// Use the same user-facing vocabulary as Android; no copied translation catalog.
tasks.processResources {
    from("../app/src/main/res/values/strings.xml") { into("i18n"); rename { "android_en.xml" } }
    from("../app/src/main/res/values-ar/strings.xml") { into("i18n"); rename { "android_ar.xml" } }
}
