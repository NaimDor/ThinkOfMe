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
