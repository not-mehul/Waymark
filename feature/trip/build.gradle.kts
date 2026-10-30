plugins {
    id("waymark.android.feature")
    id("waymark.screenshots")
}

android {
    namespace = "com.waymark.feature.trip"
}

// The trip and everything opened from it: timeline, ideas, party, costs,
// booking, map, numbers and insights.
dependencies {
    implementation(projects.feature.share)
    implementation(projects.feature.people)
    implementation(projects.core.notifications)
    implementation(projects.core.online)
    implementation(libs.androidx.compose.material3.adaptive)
}
