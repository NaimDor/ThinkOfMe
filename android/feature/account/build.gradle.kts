plugins {
    id("thinkofme.android.feature")
}

android {
    namespace = "com.thinkofme.feature.account"
}

dependencies {
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
}
