// A module that owns a Room database: KSP for the generated code, and Room's
// plugin for the committed schema of every version.
plugins {
    id("waymark.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

room {
    schemaDirectory("$projectDir/schemas")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    "api"(lib("androidx-room-runtime"))
    "implementation"(lib("androidx-room-ktx"))
    "ksp"(lib("androidx-room-compiler"))
}
