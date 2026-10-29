import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// The app shell: SDK levels and bytecode as the libraries have them, Compose,
// serialization for the navigation keys, and the baseline-profile plugin
// that compiles the profile :baselineprofile records into release builds.
plugins {
    id("com.android.application")
    id("androidx.baselineprofile")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    compileSdk = WaymarkSdk.COMPILE
    defaultConfig {
        minSdk = WaymarkSdk.MIN
        targetSdk = WaymarkSdk.TARGET
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}
