pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google's public mirror of Maven Central: avoids Central's rate limits on large artifacts.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "ThinkOfMe"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:data")
include(":core:ui")
include(":feature:home")
include(":feature:wishes")
include(":feature:circles")
include(":feature:occasions")
include(":feature:account")
