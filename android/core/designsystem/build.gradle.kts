plugins {
    id("thinkofme.android.library")
    id("thinkofme.android.compose")
}

android {
    namespace = "com.thinkofme.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
