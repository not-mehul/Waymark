// An Android library drawn with Compose: the compiler plugin, the BOM, and
// what Compose tests under Robolectric need.
plugins {
    id("waymark.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures { compose = true }
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    val bom = platform(lib("androidx-compose-bom"))
    "implementation"(bom)
    "implementation"(lib("androidx-compose-ui"))
    "implementation"(lib("androidx-compose-ui-graphics"))
    "implementation"(lib("androidx-compose-foundation"))
    "implementation"(lib("androidx-compose-material3"))
    "debugImplementation"(lib("androidx-compose-ui-tooling"))
    "testImplementation"(bom)
    "testImplementation"(lib("androidx-compose-ui-test-junit4"))
    "debugImplementation"(lib("androidx-compose-ui-test-manifest"))
}
