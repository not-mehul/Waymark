// The app, driven from outside on a device: it writes the baseline profile
// the release build ships, and measures how fast the app starts with and
// without it. Runs on a Gradle-managed emulator, so it needs no phone:
//
//   ./gradlew :app:generateBaselineProfile
//   ./gradlew :baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest
plugins {
    id("waymark.android.benchmark")
}

android {
    namespace = "com.waymark.baselineprofile"
    targetProjectPath = ":app"

    defaultConfig {
        // An emulator's timings are not a phone's; they still say whether
        // the profile helped.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    testOptions.managedDevices.localDevices {
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
