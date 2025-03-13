pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Sefirah"
include(":app")

include(":core:common")
include(":core:network")
include(":core:database")
include(":core:presentation")

include(":domain")
include(":data")
include(":data")

include(":feature:clipboard")
include(":feature:projection")
include(":feature:notification")
include(":android-smsmms")
