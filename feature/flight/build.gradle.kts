plugins {
    id("waymark.android.feature")
}

android {
    namespace = "com.waymark.feature.flight"
}

dependencies {
    implementation(projects.feature.share)
    implementation(projects.core.notifications)
    implementation(libs.androidx.activity.compose)
}
