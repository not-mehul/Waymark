plugins {
    id("waymark.android.feature")
}

android {
    namespace = "com.waymark.feature.share"
}

// Everything that leaves or enters the app as a file, a code or text.
dependencies {
    implementation(libs.zxing.core)
}
