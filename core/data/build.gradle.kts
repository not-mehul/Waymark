plugins {
    id("waymark.android.room")
}

android {
    namespace = "com.waymark.core.data"
}

dependencies {
    api(projects.core.share)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
