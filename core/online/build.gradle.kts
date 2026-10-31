plugins {
    id("waymark.android.library")
}

android {
    namespace = "com.waymark.core.online"
}

// The app's only network code: the weather and the day's exchange rates, from
// two free services that need no account and no key. Nothing else in the app
// opens a connection, and this does only when the traveler has said it may.
dependencies {
    api(projects.core.data)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
