plugins {
    id("waymark.android.compose")
}

android {
    namespace = "com.waymark.core.notifications"
}

// Whether the app may notify, and the composition local that asks once.
dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
