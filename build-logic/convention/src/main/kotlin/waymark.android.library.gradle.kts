import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// An Android library. AGP compiles its Kotlin; this sets the SDK levels, the
// bytecode level and the unit-test setup every library shares.
plugins {
    id("com.android.library")
}

android {
    compileSdk = WaymarkSdk.COMPILE
    defaultConfig {
        minSdk = WaymarkSdk.MIN
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        // Robolectric reads the merged manifest and resources.
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "testImplementation"(libs.findLibrary("junit").get())
    "testImplementation"(libs.findLibrary("robolectric").get())
    "testImplementation"(libs.findLibrary("androidx-test-core").get())
    "testImplementation"(libs.findLibrary("androidx-test-junit").get())
    "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
}
