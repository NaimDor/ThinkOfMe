plugins {
    id("thinkofme.android.library")
    id("thinkofme.android.compose")
}

android {
    namespace = "com.thinkofme.core.ui"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    api(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.play.services.ads)
}
