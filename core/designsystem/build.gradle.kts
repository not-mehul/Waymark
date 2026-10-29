plugins {
    id("waymark.android.compose")
}

android {
    namespace = "com.waymark.core.designsystem"
}

// Tokens, type, shapes, the component kit and the charts. Knows the domain's
// types and nothing of where they are stored.
dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.core.ktx)
}
