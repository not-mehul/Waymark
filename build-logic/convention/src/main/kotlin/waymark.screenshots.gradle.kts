// Screenshot tests: Compose drawn by Robolectric's native graphics and
// compared, pixel for pixel, with the reference images committed beside the
// tests. `recordRoborazziDebug` writes them, `verifyRoborazziDebug` checks
// them; an ordinary unit-test run only draws.
plugins {
    id("io.github.takahirom.roborazzi")
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    "testImplementation"(lib("roborazzi"))
    "testImplementation"(lib("roborazzi-compose"))
}
