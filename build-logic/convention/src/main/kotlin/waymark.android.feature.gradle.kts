// A feature: a set of screens, their view models, and nothing the app shell
// owns. Features depend on the core modules and on each other only where one
// screen genuinely hosts another's sheet.
plugins {
    id("waymark.android.compose")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    "implementation"(project(":core:designsystem"))
    "implementation"(project(":core:data"))
    "implementation"(lib("androidx-core-ktx"))
    "implementation"(lib("androidx-lifecycle-viewmodel-compose"))
    "implementation"(lib("androidx-lifecycle-runtime-compose"))
}
