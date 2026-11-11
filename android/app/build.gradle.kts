import java.util.Properties

plugins {
    id("thinkofme.android.application")
    id("thinkofme.android.compose")
    id("thinkofme.hilt")
    alias(libs.plugins.kotlin.serialization)
}

// Firebase is optional for local builds: without google-services.json the app
// runs in demo mode with on-device sample data.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Release signing comes from environment / keystore.properties (never committed).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String? = System.getenv(key) ?: keystoreProps.getProperty(key)

// Google's public test IDs. Real IDs are passed as Gradle properties for release builds.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testNativeUnit = "ca-app-pub-3940256099942544/2247696110"
val testBannerUnit = "ca-app-pub-3940256099942544/9214589741"

android {
    namespace = "com.thinkofme.app"

    defaultConfig {
        applicationId = "com.thinkofme.app"
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebaseConfig.toString())
    }

    signingConfigs {
        create("release") {
            secret("THINKOFME_KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = secret("THINKOFME_KEYSTORE_PASSWORD")
            keyAlias = secret("THINKOFME_KEY_ALIAS")
            keyPassword = secret("THINKOFME_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("String", "AD_UNIT_NATIVE", "\"$testNativeUnit\"")
            buildConfigField("String", "AD_UNIT_BANNER", "\"$testBannerUnit\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (secret("THINKOFME_KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["admobAppId"] = (findProperty("admobAppId") as String?) ?: testAdmobAppId
            buildConfigField("String", "AD_UNIT_NATIVE", "\"${(findProperty("admobNativeUnit") as String?) ?: testNativeUnit}\"")
            buildConfigField("String", "AD_UNIT_BANNER", "\"${(findProperty("admobBannerUnit") as String?) ?: testBannerUnit}\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:wishes"))
    implementation(project(":feature:circles"))
    implementation(project(":feature:occasions"))
    implementation(project(":feature:account"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
}
