plugins {
    id("waymark.android.feature")
}

android {
    namespace = "com.waymark.feature.shelf"
}

dependencies {
    implementation(projects.feature.share)
    implementation(libs.androidx.activity.compose)
}
