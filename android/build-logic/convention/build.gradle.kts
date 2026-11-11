plugins {
    `kotlin-dsl`
}

group = "com.thinkofme.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "thinkofme.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "thinkofme.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "thinkofme.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "thinkofme.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("androidFeature") {
            id = "thinkofme.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}
