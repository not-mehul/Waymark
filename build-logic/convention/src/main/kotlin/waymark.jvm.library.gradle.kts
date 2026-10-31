import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Plain Kotlin: the domain, the catalogues and the sharing formats, which know
// nothing of Android and test on the JVM in a fraction of a second.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "testImplementation"(libs.findLibrary("junit").get())
}

// One name for "the unit tests" in every module, JVM or Android, so CI asks
// for testDebugUnitTest once and gets all of them.
tasks.register("testDebugUnitTest") { dependsOn("test") }
