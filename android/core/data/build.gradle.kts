plugins {
    id("thinkofme.android.library")
    id("thinkofme.hilt")
}

android {
    namespace = "com.thinkofme.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.config)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)
}
