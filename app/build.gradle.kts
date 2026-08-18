plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.mikepenz.aboutlibraries.plugin")
}

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

android {
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    namespace = "com.nihaltp.sbskip"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nihaltp.sbskip"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "1.9.1"

        val changelogsDir = rootProject.file("fastlane/metadata/android/en-US/changelogs")
        var allChangelogs = ""
        if (changelogsDir.exists() && changelogsDir.isDirectory) {
            val changelogFiles = changelogsDir.listFiles()
                ?.filter { it.extension == "txt" && it.nameWithoutExtension.toIntOrNull() != null }
                ?.sortedByDescending { it.nameWithoutExtension.toInt() }
            if (changelogFiles != null && changelogFiles.isNotEmpty()) {
                val combinedText = changelogFiles.joinToString("\n\n") { it.readText().trim() }
                val processedText = combinedText
                    .replace(Regex("(?i)<ul>\\s*"), "")
                    .replace(Regex("(?i)\\s*</ul>"), "")
                    .replace(Regex("(?i)\\s*<li>\\s*"), "- ")
                    .replace(Regex("(?i)\\s*</li>\\s*"), "\n")
                allChangelogs = processedText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n")
            }
        }
        buildConfigField("String", "CHANGELOG", "\"$allChangelogs\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

configurations.all {
    resolutionStrategy.force(
        "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21",
        "org.jetbrains.kotlin:kotlin-reflect:2.0.21"
    )
}
ktlint {
    // ktlint CLI tool version (separate from the Gradle plugin version)
    version.set("1.2.1")
    android.set(true)
    filter {
        // Avoid formatting Gradle Kotlin scripts and generated build files
        exclude("**/*.gradle.kts")
        exclude("**/build/**")
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.7")
    implementation("androidx.hilt:hilt-work:1.2.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    kapt("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-16kb:6.1.7")
    implementation("com.mikepenz:aboutlibraries-compose-m3:11.2.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")

    // Instrumented UI tests & Screengrab
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("tools.fastlane:screengrab:2.1.1")
}

kapt {
    correctErrorTypes = true
}

// https://stackoverflow.com/questions/70917287/how-to-assign-different-versioncode-for-multiple-architecture-apks-built-with-fl/70942242#70942242
// Map each ABI to a unique integer suffix
val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4
)

android.applicationVariants.all {
    val variant = this
    variant.outputs.all {
        val output = this as? com.android.build.gradle.api.ApkVariantOutput
        val abi = output?.getFilter(com.android.build.VariantOutput.FilterType.ABI)
        if (abi != null) {
            val baseAbiVersionCode = abiCodes[abi] ?: 0
            output.versionCodeOverride = variant.versionCode * 1000 + baseAbiVersionCode
        } else {
            output?.versionCodeOverride = variant.versionCode * 1000
        }
    }
}
