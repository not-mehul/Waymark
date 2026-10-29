import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// A test module that drives the app from outside: macrobenchmarks, and the
// generator of the baseline profile.
plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    compileSdk = WaymarkSdk.COMPILE
    defaultConfig {
        // Profiles are collected without root from Android 9 on.
        minSdk = 28
        targetSdk = WaymarkSdk.TARGET
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}
