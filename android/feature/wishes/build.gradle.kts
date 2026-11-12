plugins {
    id("thinkofme.android.feature")
}

android {
    namespace = "com.thinkofme.feature.wishes"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
