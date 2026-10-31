// A plain Kotlin module that defines a kotlinx.serialization format.
plugins {
    id("waymark.jvm.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "api"(libs.findLibrary("kotlinx-serialization-json").get())
}
